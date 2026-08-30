package com.niklauncher.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.niklauncher.app.input.ControlOverlay
import com.niklauncher.app.ui.LauncherViewModel
import com.niklauncher.app.ui.component.SectionHeader
import com.niklauncher.core.control.ControlButton
import com.niklauncher.core.control.ControlLayout

/**
 * Lets the player choose and rearrange the on-screen controls.
 *
 * The editor shows the real overlay inside a phone-shaped frame rather than an
 * abstract list: where a button sits relative to a thumb is the entire point,
 * and that cannot be judged from a settings row.
 */
@Composable
fun ControlsScreen(viewModel: LauncherViewModel) {
    val layouts by viewModel.controlLayouts.collectAsState()
    val editing by viewModel.editingLayout.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val selectedId by viewModel.selectedElementId.collectAsState()

    val current = editing
    if (current == null) {
        LayoutPicker(
            layouts = layouts,
            activeId = settings.activeControlLayoutId,
            onActivate = viewModel::setActiveLayout,
            onEdit = viewModel::beginEditing,
            onReset = viewModel::resetControlLayouts,
        )
    } else {
        LayoutEditor(
            layout = current,
            selectedId = selectedId,
            viewModel = viewModel,
        )
    }
}

@Composable
private fun LayoutPicker(
    layouts: List<ControlLayout>,
    activeId: String,
    onActivate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionHeader(
            title = "Controles",
            subtitle = "Elige un esquema y ajústalo a tu mano.",
        )

        layouts.forEach { layout ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(layout.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${layout.buttons.size} botones · ${layout.joysticks.size} joystick",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilterChip(
                        selected = layout.id == activeId,
                        onClick = { onActivate(layout.id) },
                        label = { Text(if (layout.id == activeId) "Activo" else "Usar") },
                    )
                }
                TextButton(onClick = { onEdit(layout.id) }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Editar disposición")
                }
            }
        }

        OutlinedButton(onClick = onReset) {
            Text("Restaurar esquemas por defecto")
        }
    }
}

@Composable
private fun LayoutEditor(
    layout: ControlLayout,
    selectedId: String?,
    viewModel: LauncherViewModel,
) {
    val selectedButton = layout.buttons.firstOrNull { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Editando: ${layout.name}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::discardEdits) { Text("Descartar") }
            Button(onClick = viewModel::saveEditedLayout) { Text("Guardar") }
        }

        Text(
            text = "Arrastra un control para moverlo. Tócalo para ajustarlo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // A 9:19.5 frame so positions read the way they will on the phone.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1B1526)),
        ) {
            ControlOverlay(
                layout = layout,
                inMenu = false,
                onButtonPressed = {},
                onButtonReleased = {},
                onJoystickMoved = { _, _, _ -> },
                onJoystickReleased = {},
                editable = true,
                onElementMoved = viewModel::moveElement,
                selectedElementId = selectedId,
                onElementSelected = viewModel::selectElement,
            )
        }

        if (selectedButton != null) {
            ButtonProperties(
                button = selectedButton,
                onChange = { transform -> viewModel.updateSelectedButton(transform) },
                onDelete = viewModel::deleteSelectedElement,
            )
        }
    }
}

@Composable
private fun ButtonProperties(
    button: ControlButton,
    onChange: ((ControlButton) -> ControlButton) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Text(button.label, style = MaterialTheme.typography.titleMedium)

        Text(
            text = "Tamaño: ${button.widthDp.toInt()} dp",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = button.widthDp,
            onValueChange = { value -> onChange { it.copy(widthDp = value, heightDp = value) } },
            valueRange = ControlButton.MIN_SIZE_DP..ControlButton.MAX_SIZE_DP,
        )

        Text(
            text = "Opacidad: ${(button.opacity * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = button.opacity,
            onValueChange = { value -> onChange { it.copy(opacity = value) } },
            valueRange = 0.1f..1f,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Mantener pulsado (toggle)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = button.toggle,
                onCheckedChange = { value -> onChange { it.copy(toggle = value) } },
            )
        }

        TextButton(onClick = onDelete) { Text("Eliminar control") }
    }
}
