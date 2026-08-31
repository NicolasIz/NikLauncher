package com.niklauncher.app.runtime

import android.util.Log
import com.niklauncher.core.launch.LaunchCommand
import com.niklauncher.core.runtime.InstalledRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kotlin side of the JVM bridge.
 *
 * Starting a JVM this way is one-way: the Invocation API gives no reliable way
 * to tear a VM down and build another in the same process, so a session ends
 * when this process ends. That is why the game runs in `:runtime` rather than
 * in the launcher's own process - it keeps a Minecraft crash from taking the
 * launcher with it, and lets a second session start cleanly.
 */
object JvmBridge {

    private const val TAG = "NikLauncher"
    private const val LIBRARY_NAME = "nikjvm"

    private val loadError: Throwable? = runCatching { System.loadLibrary(LIBRARY_NAME) }.exceptionOrNull()

    private external fun nativeRedirectOutput(path: String): String?
    private external fun nativeCreateJvm(libjvmPath: String, options: Array<String>): String?
    private external fun nativeInvokeMain(mainClass: String, args: Array<String>): String?
    private external fun nativeIsRunning(): Boolean

    val isAvailable: Boolean get() = loadError == null

    val isRunning: Boolean get() = isAvailable && nativeIsRunning()

    sealed interface Result {
        data object Success : Result
        data class Failed(val reason: String) : Result
    }

    /**
     * Starts the VM and hands control to Minecraft. Blocks until the game
     * exits, so callers run it off the main thread.
     */
    suspend fun launch(
        runtime: InstalledRuntime,
        command: LaunchCommand,
        environment: Map<String, String> = emptyMap(),
        logFile: File? = null,
    ): Result = withContext(Dispatchers.IO) {
        loadError?.let {
            return@withContext Result.Failed("No se pudo cargar el puente nativo: " + it.message)
        }

        if (!runtime.libjvm.isFile) {
            return@withContext Result.Failed("libjvm.so no existe en " + runtime.libjvm.path)
        }

        environment.forEach { (key, value) -> setEnvironmentVariable(key, value) }

        // Before the VM: HotSpot reports a failed creation on stderr, and
        // redirecting afterwards would miss exactly the message that explains
        // why there is nothing to redirect for.
        logFile?.let { file ->
            file.parentFile?.mkdirs()
            nativeRedirectOutput(file.absolutePath)
                ?.let { Log.w(TAG, "Could not capture VM output: " + it) }
        }

        Log.i(TAG, "Starting " + runtime.runtime.displayName + " for " + command.mainClass)

        nativeCreateJvm(runtime.libjvm.absolutePath, command.jvmArguments.toTypedArray())
            ?.let { return@withContext Result.Failed(it) }

        nativeInvokeMain(command.mainClass, command.gameArguments.toTypedArray())
            ?.let { return@withContext Result.Failed(it) }

        Result.Success
    }

    /**
     * The environment the JVM and the graphics layer read at startup.
     *
     * These are consumed during VM creation, so they have to be in place before
     * [launch] rather than exported afterwards.
     */
    fun buildEnvironment(
        runtime: InstalledRuntime,
        graphicsBackendId: String,
        nativeLibraryDir: File,
        homeDirectory: File,
    ): Map<String, String> = mapOf(
        "JAVA_HOME" to runtime.home.absolutePath,
        "HOME" to homeDirectory.absolutePath,
        "TMPDIR" to File(homeDirectory, "tmp").absolutePath,
        "LD_LIBRARY_PATH" to listOf(
            File(runtime.home, "lib").absolutePath,
            File(runtime.home, "lib/server").absolutePath,
            nativeLibraryDir.absolutePath,
        ).joinToString(":"),
        "NIK_GRAPHICS_BACKEND" to graphicsBackendId,
    )

    private external fun nativeSetEnv(name: String, value: String)

    private fun setEnvironmentVariable(name: String, value: String) {
        runCatching { nativeSetEnv(name, value) }
            .onFailure { Log.w(TAG, "Could not set " + name, it) }
    }
}
