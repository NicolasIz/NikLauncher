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
import com.niklauncher.core.instance.ModLoader
import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.JavaRuntime
import com.niklauncher.core.settings.LauncherSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/** What the launcher knows about the native runtime layer right now. */
data class RuntimeState(
    val installed: List<JavaRuntime> = emptyList(),
    val backends: List<GraphicsBackend> = emptyList(),
    val checked: Boolean = false,
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

    private val _catalogState = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalogState: StateFlow<CatalogState> = _catalogState.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _probeReport = MutableStateFlow<ProbeReport?>(null)
    val probeReport: StateFlow<ProbeReport?> = _probeReport.asStateFlow()

    private var installJob: Job? = null

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

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        viewModelScope.launch { container.settings.update(transform) }
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
