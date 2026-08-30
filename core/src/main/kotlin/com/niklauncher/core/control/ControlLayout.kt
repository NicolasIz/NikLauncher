package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw
import kotlinx.serialization.Serializable

/**
 * A customisable on-screen control layout.
 *
 * Positions are normalised to 0..1 of the viewport rather than stored in
 * pixels, so a layout the player arranged once keeps working when the window
 * changes size - rotating the phone, or the different viewport a resolution
 * scale produces.
 */
@Serializable
data class ControlLayout(
    val id: String,
    val name: String,
    val buttons: List<ControlButton> = emptyList(),
    val joysticks: List<ControlJoystick> = emptyList(),
    /** Bumped when the shape changes, so old saved layouts can be migrated. */
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
) {
    /**
     * Clamps everything into a usable range and drops duplicate ids.
     *
     * A hand-edited or partially migrated layout must never be able to push a
     * control off-screen where the player cannot reach it to fix it.
     */
    fun normalised(): ControlLayout {
        val seen = mutableSetOf<String>()
        return copy(
            buttons = buttons.filter { seen.add(it.id) }.map { it.normalised() },
            joysticks = joysticks.filter { seen.add(it.id) }.map { it.normalised() },
            schemaVersion = CURRENT_SCHEMA_VERSION,
        )
    }

    fun withButton(button: ControlButton): ControlLayout {
        val index = buttons.indexOfFirst { it.id == button.id }
        return copy(
            buttons = if (index >= 0) buttons.toMutableList().also { it[index] = button } else buttons + button,
        )
    }

    fun withoutElement(elementId: String): ControlLayout = copy(
        buttons = buttons.filterNot { it.id == elementId },
        joysticks = joysticks.filterNot { it.id == elementId },
    )

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class ControlButton(
    val id: String,
    val label: String,
    /** Centre of the control, as a fraction of viewport width/height. */
    val x: Float,
    val y: Float,
    val widthDp: Float = 56f,
    val heightDp: Float = 56f,
    val opacity: Float = 0.55f,
    val cornerPercent: Int = 50,
    val action: ControlAction,
    /** A toggle latches instead of releasing when the finger lifts. */
    val toggle: Boolean = false,
    /** Whether the control stays visible while a menu is open. */
    val visibleInMenu: Boolean = false,
) {
    fun normalised(): ControlButton = copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        widthDp = widthDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        heightDp = heightDp.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        opacity = opacity.coerceIn(0.1f, 1f),
        cornerPercent = cornerPercent.coerceIn(0, 50),
    )

    companion object {
        const val MIN_SIZE_DP = 32f
        const val MAX_SIZE_DP = 140f
    }
}

/** An analogue movement pad, translated into the game's WASD keys. */
@Serializable
data class ControlJoystick(
    val id: String,
    val x: Float,
    val y: Float,
    val radiusDp: Float = 80f,
    val opacity: Float = 0.4f,
    /** Deflection past which a direction key is considered pressed. */
    val activationThreshold: Float = 0.35f,
    /** Full deflection additionally presses sprint. */
    val sprintThreshold: Float = 0.9f,
    val upKey: Int = Glfw.KEY_W,
    val downKey: Int = Glfw.KEY_S,
    val leftKey: Int = Glfw.KEY_A,
    val rightKey: Int = Glfw.KEY_D,
    val sprintKey: Int = Glfw.KEY_LEFT_CONTROL,
) {
    fun normalised(): ControlJoystick = copy(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        radiusDp = radiusDp.coerceIn(48f, 200f),
        opacity = opacity.coerceIn(0.1f, 1f),
        activationThreshold = activationThreshold.coerceIn(0.1f, 0.9f),
        sprintThreshold = sprintThreshold.coerceIn(0.5f, 1f),
    )
}
