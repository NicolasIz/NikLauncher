package com.niklauncher.core.input

import kotlinx.serialization.Serializable

/**
 * What each gamepad control does, expressed as the keyboard or mouse input
 * Minecraft actually listens for.
 *
 * Minecraft has no native controller support, so a gamepad can only work by
 * impersonating a keyboard and mouse. Keeping the mapping as data - rather than
 * a `when` buried in the input handler - is what lets the player rebind it.
 */
@Serializable
data class GamepadMapping(
    val buttons: Map<Int, ControlBinding> = defaultButtons(),
    /** Look sensitivity for the right stick, in pixels per second at full deflection. */
    val lookSensitivity: Float = 900f,
    /** Below this deflection a stick is treated as centred, to ignore drift. */
    val deadZone: Float = 0.15f,
    /** Movement is digital because Minecraft's own keys are; this is the threshold. */
    val movementThreshold: Float = 0.5f,
) {
    fun bindingFor(androidKeyCode: Int): ControlBinding? = buttons[androidKeyCode]

    companion object {
        fun defaultButtons(): Map<Int, ControlBinding> = mapOf(
            AndroidKeyCodes.BUTTON_A to ControlBinding.key(Glfw.KEY_SPACE),
            AndroidKeyCodes.BUTTON_B to ControlBinding.key(Glfw.KEY_LEFT_SHIFT),
            AndroidKeyCodes.BUTTON_X to ControlBinding.mouse(Glfw.MOUSE_BUTTON_RIGHT),
            AndroidKeyCodes.BUTTON_Y to ControlBinding.key(Glfw.KEY_E),
            AndroidKeyCodes.BUTTON_L1 to ControlBinding.key(Glfw.KEY_Q),
            AndroidKeyCodes.BUTTON_R1 to ControlBinding.mouse(Glfw.MOUSE_BUTTON_LEFT),
            AndroidKeyCodes.BUTTON_L2 to ControlBinding.mouse(Glfw.MOUSE_BUTTON_RIGHT),
            AndroidKeyCodes.BUTTON_R2 to ControlBinding.mouse(Glfw.MOUSE_BUTTON_LEFT),
            AndroidKeyCodes.BUTTON_THUMBL to ControlBinding.key(Glfw.KEY_LEFT_CONTROL),
            AndroidKeyCodes.BUTTON_THUMBR to ControlBinding.mouse(Glfw.MOUSE_BUTTON_MIDDLE),
            AndroidKeyCodes.BUTTON_START to ControlBinding.key(Glfw.KEY_ESCAPE),
            AndroidKeyCodes.BUTTON_SELECT to ControlBinding.key(Glfw.KEY_TAB),
        )

        val DEFAULT = GamepadMapping()
    }
}

/** Whether a binding sends a key or a mouse button, and which one. */
@Serializable
data class ControlBinding(
    val isMouse: Boolean,
    val code: Int,
) {
    fun toEvent(action: Int, modifiers: Int = 0): InputEvent =
        if (isMouse) {
            InputEvent.MouseButton(code, action, modifiers)
        } else {
            InputEvent.Key(code, action, modifiers)
        }

    companion object {
        fun key(glfwKey: Int) = ControlBinding(isMouse = false, code = glfwKey)
        fun mouse(button: Int) = ControlBinding(isMouse = true, code = button)
    }
}
