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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.niklauncher.app.ui.component.NikWordmark
import com.niklauncher.app.ui.component.StatusCard
import com.niklauncher.app.ui.component.StatusTone
import com.niklauncher.core.instance.Instance

@Composable
fun InstancesScreen(viewModel: LauncherViewModel) {
    val instances by viewModel.instances.collectAsState()
    val runtimeState by viewModel.runtimeState.collectAsState()
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

            // The runtime is not installable until Phase 2. Saying so plainly
            // beats offering a Play button that cannot work.
            if (runtimeState.checked && !runtimeState.ready) {
                item {
                    StatusCard(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.runtime_missing),
                        body = stringResource(R.string.runtime_missing_body),
                        tone = StatusTone.WARNING,
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
                onClick = {},
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
