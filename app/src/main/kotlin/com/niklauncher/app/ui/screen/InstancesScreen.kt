package com.niklauncher.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.platform.LocalContext
import com.niklauncher.app.game.GameActivity
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.niklauncher.app.R
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.RuntimeInstallState
import com.niklauncher.app.ui.component.NikWordmark
import com.niklauncher.core.instance.Instance

@Composable
fun InstancesScreen(viewModel: LauncherViewModel) {
    val context = LocalContext.current
    val instances by viewModel.instances.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
    val runtimeInstall by viewModel.runtimeInstall.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_create)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { NikWordmark() }

            item {
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            item {
                Text(
                    text = "${viewModel.device.model} · ${viewModel.device.totalMemoryMegabytes} MB RAM · " +
                        "${viewModel.device.cpuCores} núcleos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Without the runtime pack nothing can run, so this is the one
            // thing the screen has to offer rather than merely report. It said
            // the runtime would arrive "in Phase 2" long after the installer
            // was written and tested, and never called it - which left a fresh
            // install with an accurate-looking message and no way forward.
            if (runtimeState.checked && !runtimeState.ready) {
                item {
                    RuntimeInstallCard(
                        state = runtimeInstall,
                        hasSource = runtimeState.hasPackSource,
                        onInstall = viewModel::installRuntime,
                    )
                }
            }

            if (instances.isEmpty()) {
                item { EmptyInstances() }
            } else {
                items(instances, key = { it.id }) { instance ->
                    InstanceCard(
                        instance = instance,
                        playable = runtimeState.ready,
                        onPlay = {
                            viewModel.rememberLastPlayed(instance.id)
                            context.startActivity(GameActivity.intent(context, instance.id))
                        },
                        onDelete = { viewModel.deleteInstance(instance.id) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateInstanceDialog(
            defaultMemory = settings.defaultMemoryMegabytes,
            recommendedMemory = viewModel.device.recommendedMemoryMegabytes(),
            onDismiss = { showCreateDialog = false },
            onCreate = { name, version, memory ->
                viewModel.createInstance(name, version, memory)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun EmptyInstances() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun InstanceCard(
    instance: Instance,
    playable: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = instance.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${instance.minecraftVersion} · ${instance.loader.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Eliminar ${instance.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    leadingIcon = { Icon(Icons.Filled.Memory, contentDescription = null) },
                    label = { Text("${instance.memoryMegabytes} MB") },
                )
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(instance.performanceProfile.displayName) },
                )
            }

            Button(
                onClick = onPlay,
                enabled = playable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(R.string.home_play),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CreateInstanceDialog(
    defaultMemory: Int,
    recommendedMemory: Int,
    onDismiss: () -> Unit,
    onCreate: (String, String, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var memory by remember { mutableIntStateOf(defaultMemory.coerceAtMost(recommendedMemory)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text("Versión de Minecraft") },
                    placeholder = { Text("1.21.1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column {
                    Text(
                        text = "Memoria: $memory MB (recomendado $recommendedMemory MB)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = memory.toFloat(),
                        onValueChange = { memory = (it / 256f).toInt() * 256 },
                        valueRange = Instance.MIN_MEMORY_MB.toFloat()..Instance.MAX_MEMORY_MB.toFloat(),
                    )
                }
            }
        },
        confirmButton = {
            Box {
                TextButton(
                    onClick = { onCreate(name, version, memory) },
                    enabled = version.isNotBlank(),
                ) { Text("Crear") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

/**
 * The runtime pack, and the button that installs it.
 *
 * Everything the launcher does past this point needs the pack: it carries the
 * JVM and the graphics translation layer. So this card is not a status
 * message with a warning icon - it is the action, with the size named up
 * front, the progress in megabytes rather than a bare bar, and the reason
 * shown when it fails.
 */
@Composable
private fun RuntimeInstallCard(
    state: RuntimeInstallState,
    hasSource: Boolean,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.runtime_missing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Text(
                text = stringResource(R.string.runtime_missing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            when (state) {
                is RuntimeInstallState.Idle -> {
                    if (hasSource) {
                        Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.runtime_install))
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.runtime_no_source),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }

                is RuntimeInstallState.Downloading -> {
                    Text(
                        text = stringResource(R.string.runtime_installing) + "  " +
                            describeTransfer(state.transferred, state.total),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    // Determinate whenever the server told us a length, so the
                    // wait has an end the player can see.
                    if (state.total > 0) {
                        LinearProgressIndicator(
                            progress = { state.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                is RuntimeInstallState.Verifying -> {
                    Text(
                        text = stringResource(R.string.runtime_verifying),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is RuntimeInstallState.Failed -> {
                    Text(
                        text = stringResource(R.string.runtime_install_failed),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // The reason verbatim: "checksum did not match" and "no
                    // compatible pack for this device" call for different
                    // things, and only the message distinguishes them.
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.runtime_retry))
                    }
                }
            }
        }
    }
}

/** "128 MB de 249 MB" - megabytes, because a fraction alone says nothing about the wait. */
private fun describeTransfer(transferred: Long, total: Long): String {
    val mb = 1024L * 1024L
    return if (total > 0) "${transferred / mb} MB de ${total / mb} MB" else "${transferred / mb} MB"
}
