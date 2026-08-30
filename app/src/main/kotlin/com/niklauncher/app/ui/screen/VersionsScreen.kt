package com.niklauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.niklauncher.app.ui.CatalogState
import com.niklauncher.app.ui.InstallState
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.component.SectionHeader
import com.niklauncher.app.ui.component.StatusCard
import com.niklauncher.app.ui.component.StatusTone
import com.niklauncher.core.install.InstallStage
import com.niklauncher.core.manifest.VersionSummary
import com.niklauncher.core.manifest.VersionType

@Composable
fun VersionsScreen(viewModel: LauncherViewModel) {
    val catalog by viewModel.catalogState.collectAsState()
    val install by viewModel.installState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showAllTypes by remember { mutableStateOf(settings.showNonReleaseVersions) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            SectionHeader(title = "Versiones")

            (install as? InstallState.Running)?.let { running ->
                InstallProgressCard(running, onCancel = viewModel::cancelInstall)
            }
            (install as? InstallState.Failed)?.let { failed ->
                InstallFailureCard(failed, onRetry = { viewModel.install(failed.versionId) })
            }
        }

        when (val state = catalog) {
            CatalogState.Loading -> LoadingState()

            is CatalogState.Error -> Column(modifier = Modifier.padding(20.dp)) {
                StatusCard(
                    icon = Icons.Filled.CloudOff,
                    title = "No se pudo cargar el catálogo",
                    body = state.message,
                    tone = StatusTone.WARNING,
                )
                TextButton(onClick = { viewModel.loadCatalog(forceRefresh = true) }) {
                    Text("Reintentar")
                }
            }

            is CatalogState.Ready -> {
                val visible = if (showAllTypes) {
                    state.versions
                } else {
                    state.versions.filter { it.type == VersionType.RELEASE }
                }

                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !showAllTypes,
                        onClick = { showAllTypes = false },
                        label = { Text("Releases") },
                    )
                    FilterChip(
                        selected = showAllTypes,
                        onClick = { showAllTypes = true },
                        label = { Text("Todas") },
                    )
                }

                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visible, key = { it.id }) { version ->
                        VersionRow(
                            version = version,
                            installed = version.id in state.installed,
                            isLatest = version.id == state.latestRelease,
                            busy = (install as? InstallState.Running)?.versionId == version.id,
                            enabled = install !is InstallState.Running,
                            onInstall = { viewModel.install(version.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            text = "Cargando catálogo de Mojang…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun VersionRow(
    version: VersionSummary,
    installed: Boolean,
    isLatest: Boolean,
    busy: Boolean,
    enabled: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = version.id,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isLatest) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Text(
                                text = "última",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                Text(
                    text = version.type.name.lowercase().replace('_', ' '),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                busy -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                installed -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Instalada",
                    tint = MaterialTheme.colorScheme.primary,
                )
                else -> IconButton(onClick = onInstall, enabled = enabled) {
                    Icon(Icons.Filled.Download, contentDescription = "Instalar ${version.id}")
                }
            }
        }
    }
}

@Composable
private fun InstallProgressCard(state: InstallState.Running, onCancel: () -> Unit) {
    val progress = state.progress
    val stageLabel = when (progress.stage) {
        InstallStage.RESOLVING_METADATA -> "Resolviendo metadatos"
        InstallStage.PLANNING -> "Planificando la instalación"
        InstallStage.DOWNLOADING -> "Descargando"
        InstallStage.FINALISING -> "Preparando assets"
        InstallStage.COMPLETE -> "Completado"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("${state.versionId} · $stageLabel", style = MaterialTheme.typography.titleMedium)
            if (progress.totalFiles > 0) {
                Text(
                    text = "${progress.completedFiles} / ${progress.totalFiles} archivos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 4.dp)) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun InstallFailureCard(state: InstallState.Failed, onRetry: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        StatusCard(
            icon = Icons.Filled.CloudOff,
            title = "La instalación de ${state.versionId} no se completó",
            body = state.message + state.detail.joinToString(
                prefix = if (state.detail.isEmpty()) "" else "\n",
                separator = "\n",
            ),
            tone = StatusTone.WARNING,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text("Reintentar")
        }
    }
}
