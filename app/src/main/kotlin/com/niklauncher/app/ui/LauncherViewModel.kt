package com.niklauncher.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.niklauncher.app.data.AppContainer
import com.niklauncher.app.data.DeviceCapabilities
import com.niklauncher.core.instance.Instance
import com.niklauncher.core.instance.ModLoader
import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.JavaRuntime
import com.niklauncher.core.settings.LauncherSettings
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

    init {
        viewModelScope.launch { container.instances.load() }
        refreshRuntimeState()
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
