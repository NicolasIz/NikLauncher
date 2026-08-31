package com.niklauncher.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niklauncher.app.data.AppContainer
import com.niklauncher.app.data.DeviceCapabilities
import com.niklauncher.app.data.NativeProbe
import com.niklauncher.app.data.ProbeReport
import com.niklauncher.core.install.InstallResult
import com.niklauncher.core.install.InstallStage
import com.niklauncher.core.install.InstallProgress
import com.niklauncher.core.control.ControlButton
import com.niklauncher.core.control.ControlLayout
import com.niklauncher.core.control.ControlPresets
import com.niklauncher.core.instance.Instance
import com.niklauncher.core.io.SessionLogs
import com.niklauncher.core.instance.ModLoader
import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.JavaRuntime
import com.niklauncher.core.settings.LauncherSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** What the launcher knows about the native runtime layer right now. */
/**
 * How the runtime pack install is going.
 *
 * A pack is a few hundred megabytes, so this is never instantaneous and never
 * something to hide behind a spinner with no numbers - and it is the one
 * download the launcher cannot work without.
 */
sealed interface RuntimeInstallState {
    data object Idle : RuntimeInstallState
    data class Downloading(val fraction: Float, val transferred: Long, val total: Long) :
        RuntimeInstallState
    /**
     * The bytes are down and the archive is being extracted and checked. Its
     * own state because it takes real time on a pack this size, and a progress
     * bar stuck at 100% with no explanation reads as a hang.
     */
    data object Verifying : RuntimeInstallState
    data class Failed(val reason: String) : RuntimeInstallState
}

data class RuntimeState(
    val installed: List<JavaRuntime> = emptyList(),
    val backends: List<GraphicsBackend> = emptyList(),
    val checked: Boolean = false,
    /** False when no runtime pack source has been configured yet. */
    val hasPackSource: Boolean = false,
) {
    val ready: Boolean get() = installed.isNotEmpty()
}

class LauncherViewModel(private val container: AppContainer) : ViewModel() {

    val device: DeviceCapabilities = container.device

    val instances: StateFlow<List<Instance>> = container.instances.instances

    val settings: StateFlow<LauncherSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherSettings.DEFAULT)

    private val _runtimeState = MutableStateFlow(RuntimeState())
    val runtimeState: StateFlow<RuntimeState> = _runtimeState.asStateFlow()

    private val _runtimeInstall = MutableStateFlow<RuntimeInstallState>(RuntimeInstallState.Idle)
    val runtimeInstall: StateFlow<RuntimeInstallState> = _runtimeInstall.asStateFlow()

    private var runtimeInstallJob: Job? = null

    private val _catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalogState: StateFlow<CatalogState> = _catalogState.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _probeReport = MutableStateFlow<ProbeReport?>(null)
    val probeReport: StateFlow<ProbeReport?> = _probeReport.asStateFlow()

    private var installJob: Job? = null

    private val sessionLogs = SessionLogs(container.paths.logs)

    private val _lastSessionLog = MutableStateFlow<SessionLogs.Entry?>(null)

    /**
     * The last session's log, if there is one.
     *
     * Read on demand rather than watched: the game runs in its own process, so
     * nothing here is notified when that process writes or dies - and when
     * Minecraft calls System.exit there is no failure screen at all, only this
     * file. Refreshing when the screen is opened is what makes it findable.
     */
    val lastSessionLog: StateFlow<SessionLogs.Entry?> = _lastSessionLog.asStateFlow()

    fun refreshSessionLogs() {
        viewModelScope.launch {
            _lastSessionLog.value = withContext(Dispatchers.IO) { sessionLogs.latest() }
        }
    }

    /**
     * The end of a log, for showing without opening a file the phone cannot
     * browse. Short by default: this is a preview that says which run it was
     * and roughly how it ended, and the whole file is one share away.
     */
    suspend fun sessionLogTail(entry: SessionLogs.Entry, lines: Int = PREVIEW_LINES): String? =
        withContext(Dispatchers.IO) { sessionLogs.tail(entry.file, lines) }

    init {
        viewModelScope.launch { container.instances.load() }
        viewModelScope.launch { container.controlLayouts.load() }
        refreshRuntimeState()
        loadCatalog()
    }

    fun loadCatalog(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _catalogState.value = CatalogState.Loading
            _catalogState.value = try {
                val manifest = container.catalog.manifest(forceRefresh)
                CatalogState.Ready(
                    versions = manifest.versions,
                    installed = container.catalog.installedVersionIds().toSet(),
                    latestRelease = manifest.latest.release,
                )
            } catch (error: Throwable) {
                // Offline is the common case here, so say so plainly rather
                // than showing an empty list that looks like a bug.
                CatalogState.Error(error.message ?: "No se pudo cargar el catálogo de versiones")
            }
        }
    }

    fun install(versionId: String) {
        if (installJob?.isActive == true) return
        installJob = viewModelScope.launch {
            _installState.value = InstallState.Running(
                versionId,
                InstallProgress(InstallStage.RESOLVING_METADATA),
            )
            when (val result = container.installer.install(versionId) { progress ->
                _installState.value = InstallState.Running(versionId, progress)
            }) {
                is InstallResult.Success -> {
                    _installState.value = InstallState.Done(versionId)
                    refreshInstalled()
                }
                is InstallResult.Incomplete -> {
                    _installState.value = InstallState.Failed(
                        versionId,
                        "Faltaron ${result.failures.size} archivos. Reintentar solo descarga lo que falta.",
                        result.failures.take(5).map { it.label },
                    )
                    refreshInstalled()
                }
                is InstallResult.Failed -> {
                    _installState.value = InstallState.Failed(
                        versionId,
                        result.cause.message ?: "La instalación falló",
                    )
                }
            }
        }
    }

    fun cancelInstall() {
        installJob?.cancel()
        installJob = null
        _installState.value = InstallState.Idle
    }

    fun dismissInstallState() {
        if (_installState.value !is InstallState.Running) _installState.value = InstallState.Idle
    }

    private suspend fun refreshInstalled() {
        val current = _catalogState.value
        if (current is CatalogState.Ready) {
            _catalogState.value = current.copy(installed = container.catalog.installedVersionIds().toSet())
        }
    }

    fun runNativeProbe(context: android.content.Context) {
        viewModelScope.launch { _probeReport.value = NativeProbe.run(context) }
    }

    // --- Control layouts -------------------------------------------------

    val controlLayouts: StateFlow<List<ControlLayout>> = container.controlLayouts.layouts

    private val _editingLayout = MutableStateFlow<ControlLayout?>(null)
    val editingLayout: StateFlow<ControlLayout?> = _editingLayout.asStateFlow()

    private val _selectedElementId = MutableStateFlow<String?>(null)
    val selectedElementId: StateFlow<String?> = _selectedElementId.asStateFlow()

    fun beginEditing(layoutId: String) {
        _editingLayout.value = controlLayouts.value.firstOrNull { it.id == layoutId }
            ?: ControlPresets.default()
        _selectedElementId.value = null
    }

    fun selectElement(elementId: String?) {
        _selectedElementId.value = elementId
    }

    /**
     * Nudges an element by a fraction of the viewport. Editing works in the
     * same normalised space the layout is stored in, so a control never lands
     * somewhere that only makes sense at one screen size.
     */
    fun moveElement(elementId: String, deltaX: Float, deltaY: Float) {
        val layout = _editingLayout.value ?: return
        _editingLayout.value = layout.copy(
            buttons = layout.buttons.map {
                if (it.id == elementId) it.copy(x = it.x + deltaX, y = it.y + deltaY).normalised() else it
            },
            joysticks = layout.joysticks.map {
                if (it.id == elementId) it.copy(x = it.x + deltaX, y = it.y + deltaY).normalised() else it
            },
        )
    }

    fun updateSelectedButton(transform: (ControlButton) -> ControlButton) {
        val layout = _editingLayout.value ?: return
        val id = _selectedElementId.value ?: return
        _editingLayout.value = layout.copy(
            buttons = layout.buttons.map { if (it.id == id) transform(it).normalised() else it },
        )
    }

    fun deleteSelectedElement() {
        val layout = _editingLayout.value ?: return
        val id = _selectedElementId.value ?: return
        _editingLayout.value = layout.withoutElement(id)
        _selectedElementId.value = null
    }

    fun saveEditedLayout() {
        val layout = _editingLayout.value ?: return
        viewModelScope.launch {
            container.controlLayouts.upsert(layout)
            _editingLayout.value = null
            _selectedElementId.value = null
        }
    }

    fun discardEdits() {
        _editingLayout.value = null
        _selectedElementId.value = null
    }

    fun resetControlLayouts() {
        viewModelScope.launch {
            container.controlLayouts.resetToDefaults()
            _editingLayout.value = null
            _selectedElementId.value = null
        }
    }

    fun setActiveLayout(layoutId: String) {
        updateSettings { it.copy(activeControlLayoutId = layoutId) }
    }

    fun refreshRuntimeState() {
        viewModelScope.launch {
            val provider = container.runtimeProvider
            _runtimeState.value = RuntimeState(
                installed = provider.installedRuntimes().map { it.runtime },
                backends = provider.availableBackends(),
                checked = true,
                hasPackSource = runCatching { container.packCatalog.hasSource() }.getOrDefault(false),
            )
        }
    }

    fun createInstance(name: String, minecraftVersion: String, memoryMegabytes: Int) {
        val trimmedName = name.trim().ifBlank { "Instancia" }
        val trimmedVersion = minecraftVersion.trim()
        if (trimmedVersion.isEmpty()) return

        viewModelScope.launch {
            container.instances.upsert(
                Instance(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    minecraftVersion = trimmedVersion,
                    loader = ModLoader.VANILLA,
                    memoryMegabytes = memoryMegabytes,
                    performanceProfileId = settings.value.defaultPerformanceProfileId,
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun deleteInstance(instanceId: String) {
        viewModelScope.launch { container.instances.delete(instanceId) }
    }

    /**
     * Noted before the session starts rather than after it ends: a game process
     * that crashes still tells us what the player was last trying to play,
     * which is exactly when that is worth knowing.
     */
    fun rememberLastPlayed(instanceId: String) {
        viewModelScope.launch {
            container.settings.update { it.copy(lastPlayedInstanceId = instanceId) }
        }
    }

    /**
     * Downloads and installs the Java runtime pack.
     *
     * Nothing in the launcher works without this - it carries the JVM itself
     * and the graphics translation - and until now nothing ever called it, so
     * a fresh install had no way to become usable. The installer verifies the
     * archive before reporting success, so reaching Idle here means the pack
     * is on disk and its checksum matched.
     */
    fun installRuntime() {
        if (runtimeInstallJob?.isActive == true) return
        runtimeInstallJob = viewModelScope.launch {
            _runtimeInstall.value = RuntimeInstallState.Downloading(0f, 0, 0)
            try {
                container.runtimeProvider.ensureInstalled(JavaRuntime.JRE_21) { progress ->
                    _runtimeInstall.value = if (progress.fraction >= 1f) {
                        RuntimeInstallState.Verifying
                    } else {
                        RuntimeInstallState.Downloading(
                            fraction = progress.fraction,
                            transferred = progress.bytesTransferred,
                            total = progress.totalBytes,
                        )
                    }
                }
                // Extraction and checksum happen inside ensureInstalled; by the
                // time it returns there is nothing left to wait for.
                _runtimeInstall.value = RuntimeInstallState.Idle
                refreshRuntimeState()
            } catch (error: CancellationException) {
                _runtimeInstall.value = RuntimeInstallState.Idle
                throw error
            } catch (error: Exception) {
                _runtimeInstall.value = RuntimeInstallState.Failed(
                    error.message ?: error::class.simpleName.orEmpty(),
                )
            }
        }
    }

    fun cancelRuntimeInstall() {
        runtimeInstallJob?.cancel()
        runtimeInstallJob = null
        _runtimeInstall.value = RuntimeInstallState.Idle
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { container.settings.update(transform) }
    }

    private companion object {
        /** Enough of a log to see how a run ended, without filling the screen. */
        const val PREVIEW_LINES = 12
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
                "Unsupported ViewModel: $modelClass"
            }
            return LauncherViewModel(container) as T
        }
    }
}
