package com.niklauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.niklauncher.app.R
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.component.SectionHeader
import com.niklauncher.core.instance.Instance
import com.niklauncher.core.runtime.PerformanceProfile

@Composable
fun SettingsScreen(viewModel: LauncherViewModel) {
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionHeader(title = stringResource(R.string.settings_title))

        Column {
            Text(
                text = "${stringResource(R.string.settings_memory)}: ${settings.defaultMemoryMegabytes} MB",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Recomendado para ${viewModel.device.model}: " +
                    "${viewModel.device.recommendedMemoryMegabytes()} MB",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = settings.defaultMemoryMegabytes.toFloat(),
                onValueChange = { value ->
                    val rounded = (value / 256f).toInt() * 256
                    viewModel.updateSettings { it.copy(defaultMemoryMegabytes = rounded) }
                },
                valueRange = Instance.MIN_MEMORY_MB.toFloat()..Instance.MAX_MEMORY_MB.toFloat(),
            )
        }

        Column {
            Text(
                text = stringResource(R.string.settings_performance),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "La prioridad es estabilidad y temperatura antes que FPS máximos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PerformanceProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = settings.defaultPerformanceProfileId == profile.id,
                        onClick = {
                            viewModel.updateSettings { it.copy(defaultPerformanceProfileId = profile.id) }
                        },
                        label = { Text(profile.displayName) },
                    )
                }
            }
            val tuning = settings.defaultPerformanceProfile.defaultTuning()
            Text(
                text = "Objetivo ${tuning.targetFps} FPS · distancia ${tuning.maxRenderDistance} chunks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        SettingSwitch(
            title = stringResource(R.string.settings_keep_screen_on),
            checked = settings.keepScreenOn,
            onCheckedChange = { value -> viewModel.updateSettings { it.copy(keepScreenOn = value) } },
        )

        SettingSwitch(
            title = stringResource(R.string.settings_show_snapshots),
            checked = settings.showNonReleaseVersions,
            onCheckedChange = { value -> viewModel.updateSettings { it.copy(showNonReleaseVersions = value) } },
        )

        SettingSwitch(
            title = stringResource(R.string.settings_verbose_logging),
            checked = settings.verboseLogging,
            onCheckedChange = { value -> viewModel.updateSettings { it.copy(verboseLogging = value) } },
        )

        Column {
            Text(
                text = "${stringResource(R.string.settings_downloads)}: ${settings.downloadConcurrency}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Más descargas en paralelo calientan el dispositivo sin acelerar mucho la instalación.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = settings.downloadConcurrency.toFloat(),
                onValueChange = { value ->
                    viewModel.updateSettings { it.copy(downloadConcurrency = value.toInt().coerceIn(1, 12)) }
                },
                valueRange = 1f..12f,
                steps = 10,
            )
        }

        Text(
            text = "NikLauncher ${com.niklauncher.core.NikLauncher.VERSION} · Fase 1",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
