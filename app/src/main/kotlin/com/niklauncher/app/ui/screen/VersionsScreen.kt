package com.niklauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niklauncher.app.R
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.component.SectionHeader
import com.niklauncher.app.ui.component.StatusCard
import com.niklauncher.app.ui.component.StatusTone
import com.niklauncher.core.runtime.GraphicsBackend

@Composable
fun VersionsScreen(viewModel: LauncherViewModel) {
    val runtimeState by viewModel.runtimeState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(title = stringResource(R.string.versions_title))

        StatusCard(
            icon = Icons.Filled.CloudDownload,
            title = stringResource(R.string.versions_offline_title),
            body = stringResource(R.string.versions_offline_body),
            tone = StatusTone.NEUTRAL,
        )

        SectionHeader(
            title = "Backend gráfico",
            subtitle = "Qué traducción de OpenGL puede usar este dispositivo.",
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GraphicsBackend.entries.forEach { backend ->
                val available = backend in runtimeState.backends
                val usable = !backend.requiresVulkan || viewModel.device.supportsVulkan
                val state = when {
                    available -> "instalado"
                    usable -> "compatible, pendiente de Fase 2"
                    else -> "no compatible con este dispositivo"
                }
                Text(
                    text = "${backend.displayName} — $state",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = if (viewModel.device.supportsVulkan) {
                "Vulkan detectado en ${viewModel.device.socName}. Zink será la opción preferente para 1.17+."
            } else {
                "Vulkan no detectado; se usará LTW para 1.17+."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
