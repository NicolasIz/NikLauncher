package com.niklauncher.app.input

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.niklauncher.core.control.ControlButton
import com.niklauncher.core.control.ControlJoystick
import com.niklauncher.core.control.ControlLayout
import com.niklauncher.core.control.JoystickTranslator
import kotlin.math.roundToInt

/**
 * Draws the on-screen controls over the game surface.
 *
 * The overlay only occupies the space its controls need: touches anywhere else
 * fall through to the look-and-attack surface underneath. That is why each
 * control is its own small hit target rather than one full-screen layer.
 */
@Composable
fun ControlOverlay(
    layout: ControlLayout,
    inMenu: Boolean,
    onButtonPressed: (ControlButton) -> Unit,
    onButtonReleased: (ControlButton) -> Unit,
    onJoystickMoved: (ControlJoystick, Float, Float) -> Unit,
    onJoystickReleased: (ControlJoystick) -> Unit,
    modifier: Modifier = Modifier,
    editable: Boolean = false,
    onElementMoved: (String, Float, Float) -> Unit = { _, _, _ -> },
    selectedElementId: String? = null,
    onElementSelected: (String) -> Unit = {},
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        layout.joysticks.forEach { joystick ->
            JoystickControl(
                joystick = joystick,
                parentWidth = widthPx,
                parentHeight = heightPx,
                enabled = !inMenu && !editable,
                editable = editable,
                selected = selectedElementId == joystick.id,
                onMoved = { dx, dy -> onJoystickMoved(joystick, dx, dy) },
                onReleased = { onJoystickReleased(joystick) },
                onElementMoved = onElementMoved,
                onSelected = { onElementSelected(joystick.id) },
            )
        }

        layout.buttons.forEach { button ->
            if (inMenu && !button.visibleInMenu && !editable) return@forEach
            ButtonControl(
                button = button,
                parentWidth = widthPx,
                parentHeight = heightPx,
                editable = editable,
                selected = selectedElementId == button.id,
                onPressed = { onButtonPressed(button) },
                onReleased = { onButtonReleased(button) },
                onElementMoved = onElementMoved,
                onSelected = { onElementSelected(button.id) },
            )
        }
    }
}

@Composable
private fun ButtonControl(
    button: ControlButton,
    parentWidth: Float,
    parentHeight: Float,
    editable: Boolean,
    selected: Boolean,
    onPressed: () -> Unit,
    onReleased: () -> Unit,
    onElementMoved: (String, Float, Float) -> Unit,
    onSelected: () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { button.widthDp.dp.toPx() }
    val heightPx = with(density) { button.heightDp.dp.toPx() }
    var latched by remember(button.id) { mutableStateOf(false) }

    val left = (button.x * parentWidth - widthPx / 2f).roundToInt()
    val top = (button.y * parentHeight - heightPx / 2f).roundToInt()

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left, top) }
            .size(button.widthDp.dp, button.heightDp.dp)
            .clip(RoundedCornerShape(percent = button.cornerPercent))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                } else {
                    Color.Black.copy(alpha = button.opacity)
                },
            )
            .then(
                if (editable) {
                    Modifier.pointerInput(button.id) {
                        detectDragGestures(
                            onDragStart = { onSelected() },
                            onDrag = { change, drag ->
                                change.consume()
                                onElementMoved(
                                    button.id,
                                    drag.x / parentWidth,
                                    drag.y / parentHeight,
                                )
                            },
                        )
                    }
                } else {
                    Modifier.pointerInput(button.id) {
                        detectPress(
                            onDown = {
                                if (button.toggle) {
                                    latched = !latched
                                    if (latched) onPressed() else onReleased()
                                } else {
                                    onPressed()
                                }
                            },
                            // A latched control keeps its key held after the
                            // finger lifts; that is the whole point of a toggle.
                            onUp = { if (!button.toggle) onReleased() },
                        )
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = button.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = if (latched) 1f else 0.85f),
        )
    }
}

@Composable
private fun JoystickControl(
    joystick: ControlJoystick,
    parentWidth: Float,
    parentHeight: Float,
    enabled: Boolean,
    editable: Boolean,
    selected: Boolean,
    onMoved: (Float, Float) -> Unit,
    onReleased: () -> Unit,
    onElementMoved: (String, Float, Float) -> Unit,
    onSelected: () -> Unit,
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { joystick.radiusDp.dp.toPx() }
    val diameter: Dp = (joystick.radiusDp * 2).dp
    var knob by remember(joystick.id) { mutableStateOf(Offset.Zero) }

    val left = (joystick.x * parentWidth - radiusPx).roundToInt()
    val top = (joystick.y * parentHeight - radiusPx).roundToInt()

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(left, top) }
            .size(diameter)
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                } else {
                    Color.Black.copy(alpha = joystick.opacity)
                },
            )
            .then(
                if (editable) {
                    Modifier.pointerInput(joystick.id) {
                        detectDragGestures(
                            onDragStart = { onSelected() },
                            onDrag = { change, drag ->
                                change.consume()
                                onElementMoved(joystick.id, drag.x / parentWidth, drag.y / parentHeight)
                            },
                        )
                    }
                } else if (enabled) {
                    Modifier.pointerInput(joystick.id) {
                        detectDragGestures(
                            onDragStart = { start ->
                                knob = start - Offset(radiusPx, radiusPx)
                                emitDeflection(knob, radiusPx, onMoved)
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                knob += drag
                                emitDeflection(knob, radiusPx, onMoved)
                            },
                            onDragEnd = {
                                knob = Offset.Zero
                                onReleased()
                            },
                            onDragCancel = {
                                knob = Offset.Zero
                                onReleased()
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        knob.x.coerceIn(-radiusPx, radiusPx).roundToInt(),
                        knob.y.coerceIn(-radiusPx, radiusPx).roundToInt(),
                    )
                }
                .size((joystick.radiusDp * 0.8f).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.25f))
                .alpha(if (editable) 0.5f else 1f),
        )
    }
}

private fun emitDeflection(knob: Offset, radiusPx: Float, onMoved: (Float, Float) -> Unit) {
    val (dx, dy) = JoystickTranslator.deflection(knob.x, knob.y, radiusPx)
    onMoved(dx, dy)
}

/**
 * Press/release detection for a control button.
 *
 * `waitForUpOrCancellation` returns null when the gesture is cancelled - the
 * finger slid off, or a parent took the gesture - and the button must release
 * in that case too, or a key stays held down with nothing on screen showing it.
 */
private suspend fun PointerInputScope.detectPress(
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onDown()
        waitForUpOrCancellation()
        onUp()
    }
}
