package com.niklauncher.app.game

import android.util.Log
import com.niklauncher.app.data.AppContainer
import com.niklauncher.app.runtime.GlfwBridge
import com.niklauncher.app.runtime.JvmBridge
import com.niklauncher.core.control.ControlButton
import com.niklauncher.core.control.ControlInputTranslator
import com.niklauncher.core.control.ControlJoystick
import com.niklauncher.core.control.ControlLayout
import com.niklauncher.core.control.ControlPresets
import com.niklauncher.core.install.InstallPlan
import com.niklauncher.core.install.InstallPlanner
import com.niklauncher.core.instance.Instance
import com.niklauncher.core.io.SessionLogs
import com.niklauncher.core.launch.DisplaySize
import com.niklauncher.core.launch.LaunchAccount
import com.niklauncher.core.launch.LaunchPlanner
import com.niklauncher.core.launch.PlannedLaunch
import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.InstalledRuntime
import com.niklauncher.core.runtime.RuntimePack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** What the session is doing, for the screen to show. */
sealed interface GameState {
    data object Preparing : GameState
    data class Ready(val summary: String) : GameState
    data object Running : GameState
    /**
     * [logTail] is shown on screen rather than only written to a file: internal
     * storage is not browsable without a PC, so a path the player cannot open
     * is not a diagnosis they can send back. [log] is the whole thing, offered
     * for sharing, because the tail is often not where the cause is.
     */
    data class Failed(
        val reason: String,
        val logTail: String? = null,
        val log: File? = null,
    ) : GameState
}

/**
 * One session, from "the player pressed play" to "the VM is running".
 *
 * Split from the Activity so the order of operations is readable in one place.
 * That order is the whole point: everything the game needs has to be in place
 * before the VM starts, because Minecraft creates its window during startup and
 * neither the EGL binding nor the Surface can be changed afterwards.
 */
class GameSession(
    private val container: AppContainer,
    private val instanceId: String,
) {

    private val _state = MutableStateFlow<GameState>(GameState.Preparing)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _controls = MutableStateFlow(ControlPresets.all().first())
    val controls: StateFlow<ControlLayout> = _controls.asStateFlow()

    private val _inMenu = MutableStateFlow(false)
    val inMenu: StateFlow<Boolean> = _inMenu.asStateFlow()

    private val input = ControlInputTranslator(_controls.value)

    private var planned: PlannedLaunch? = null
    private var runtime: InstalledRuntime? = null
    private var display = DisplaySize(1280, 720)
    private var started = false

    private val logs = SessionLogs(container.paths.logs)

    fun onButtonPressed(button: ControlButton) = dispatch(input.press(button))

    fun onButtonReleased(button: ControlButton) = dispatch(input.release(button))

    fun onJoystickMoved(joystick: ControlJoystick, dx: Float, dy: Float) =
        dispatch(input.moveJoystick(joystick, dx, dy))

    fun onJoystickReleased(joystick: ControlJoystick) = dispatch(input.releaseJoystick(joystick))

    /** Focus loss: let go of everything rather than walk into a wall. */
    fun onFocusLost() = dispatch(input.releaseAll())

    private fun dispatch(output: ControlInputTranslator.Output) {
        GlfwBridge.send(output.events)
        _inMenu.value = input.inMenu
        output.sideEffects.forEach { effect ->
            when (effect) {
                // Counted rather than flagged, so a second request is visible
                // as a change even though the answer is the same each time.
                ControlInputTranslator.SideEffect.TOGGLE_KEYBOARD -> _keyboardRequests.value++
            }
        }
    }

    private val _keyboardRequests = MutableStateFlow(0)
    val keyboardRequests: StateFlow<Int> = _keyboardRequests.asStateFlow()

    fun onSurfaceSize(width: Int, height: Int) {
        if (width > 0 && height > 0) display = DisplaySize(width, height)
    }

    /**
     * Works out the launch without starting it, so a missing runtime or an
     * uninstalled version is reported as a message rather than as a dead black
     * screen.
     */
    suspend fun prepare() {
        _state.value = runCatching { buildPlan() }
            .fold(
                onSuccess = { GameState.Ready(it) },
                onFailure = { GameState.Failed(it.message ?: it::class.simpleName.orEmpty()) },
            )
    }

    private suspend fun buildPlan(): String = withContext(Dispatchers.IO) {
        val instance = container.instances.load().firstOrNull { it.id == instanceId }
            ?: error("Esta instancia ya no existe")

        if (!container.installer.isInstalled(instance.resolvedVersionId)) {
            error("La versión ${instance.resolvedVersionId} no está instalada")
        }
        // Resolved rather than raw, so a modded instance gets its loader's
        // main class and merged classpath rather than vanilla's.
        val version = container.catalog.resolvedVersion(instance.resolvedVersionId)

        // Re-planned from what is on disk rather than remembered from the
        // install: nothing downloads here, and a plan carried across a process
        // boundary would be a copy of the truth instead of the truth.
        val install = InstallPlanner(container.paths).plan(version, null)

        val installed = container.runtimeProvider.installedRuntimes()
            .firstOrNull { it.runtime == install.javaRuntime }
            ?: error("El runtime ${install.javaRuntime.displayName} no está instalado")

        val pack = container.runtimeProvider.installedPack(install.javaRuntime)
            ?: error("El pack instalado no declara su contenido")

        val backend = chooseBackend(instance, pack)

        val plan = LaunchPlanner(container.paths).plan(
            instance = instance,
            install = install,
            runtime = installed,
            pack = pack,
            backend = backend,
            account = LaunchAccount.offline(container.settings.settings.first().playerName),
            display = display,
            // libglfw.so ships in the launcher, not in the pack: it implements
            // the GLFW ABI against this launcher's own event core. Naming its
            // directory here is what lets LWJGL find it at all.
            bundledLibraryDirectories = listOf(File(container.nativeLibraryDir)),
        )

        val layout = container.controlLayouts.load()
            .firstOrNull { it.id == container.settings.settings.first().activeControlLayoutId }
            ?: ControlPresets.all().first()
        _controls.value = layout
        input.useLayout(layout)

        runtime = installed
        planned = plan
        summarise(instance, installed, backend, install)
    }

    /**
     * Starts the VM. Called once the Surface exists, never before: the bridge
     * has nothing to create a window on until then.
     */
    suspend fun start() {
        if (started) return
        val plan = planned ?: return
        val installed = runtime ?: return
        started = true

        // Before the VM, not after. The bridge resolves its EGL table on the
        // first egl call and keeps it, so naming the library later would be
        // ignored and the game would quietly run on the system driver.
        GlfwBridge.useEglLibrary(plan.eglLibrary?.absolutePath)

        _state.value = GameState.Running
        Log.i(TAG, "Starting ${plan.backend.displayName} session for $instanceId")

        // Rotated here rather than appended to: the tail of a log holding
        // three runs points at whichever one printed last, which is not
        // necessarily this one.
        val log = logs.beginSession(instanceId)
        val result = JvmBridge.launch(
            runtime = installed,
            command = plan.command,
            environment = environment(installed, plan),
            logFile = log,
        )

        if (result is JvmBridge.Result.Failed) {
            // The path matters as much as the reason: what HotSpot or Minecraft
            // said is in there, and the reason alone is often just "it did not
            // start".
            _state.value = GameState.Failed(result.reason, logs.tail(log), log)
        }
    }

    /**
     * The backend's own directory goes on LD_LIBRARY_PATH as well: Mesa's
     * libEGL has its own dependencies, and the bridge opens it by absolute path
     * with RTLD_NOW, so those have to be findable or the open fails outright.
     */
    private fun environment(installed: InstalledRuntime, plan: PlannedLaunch): Map<String, String> {
        val base = JvmBridge.buildEnvironment(
            runtime = installed,
            graphicsBackendId = plan.backend.id,
            nativeLibraryDir = File(container.nativeLibraryDir),
            homeDirectory = container.paths.instance(instanceId),
        )
        return base + mapOf(
            "LD_LIBRARY_PATH" to listOf(
                plan.graphicsLibraryDirectory.absolutePath,
                base["LD_LIBRARY_PATH"].orEmpty(),
            ).filter { it.isNotEmpty() }.joinToString(":"),
        )
    }

    /**
     * The instance's choice when it made one, otherwise whichever backend the
     * pack actually ships that suits this version - a pack without Zink cannot
     * serve a request for it, and saying so early beats failing at window
     * creation.
     */
    private fun chooseBackend(instance: Instance, pack: RuntimePack): GraphicsBackend {
        val available = pack.backends
        if (available.isEmpty()) error("El pack instalado no trae ningún backend gráfico")
        instance.graphicsBackend?.let { chosen ->
            if (chosen in available) return chosen
        }
        val recommended = GraphicsBackend.recommendedFor(
            minecraftVersion = instance.minecraftVersion,
            vulkanAvailable = container.device.supportsVulkan,
        )
        return if (recommended in available) recommended else available.first()
    }

    private fun summarise(
        instance: Instance,
        installed: InstalledRuntime,
        backend: GraphicsBackend,
        install: InstallPlan,
    ): String = buildString {
        append(instance.name).append(" · ").append(instance.minecraftVersion).append('\n')
        append(installed.runtime.displayName).append(' ').append(installed.version).append('\n')
        append(backend.displayName).append(" · ")
        append(install.classpath.size).append(" jars · ")
        append(instance.memoryMegabytes).append(" MB")
    }

    private companion object {
        const val TAG = "NikLauncher"
    }
}
