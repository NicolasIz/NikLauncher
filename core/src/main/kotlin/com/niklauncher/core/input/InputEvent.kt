package com.niklauncher.core.input

/**
 * A synthetic GLFW event on its way to Minecraft.
 *
 * Everything the player does - a touch, a gamepad stick, a Bluetooth keyboard -
 * is normalised into one of these before it reaches the native bridge, so the
 * translation logic can be written and tested without an Android device or a
 * running JVM.
 */
sealed interface InputEvent {

    data class Key(
        val glfwKey: Int,
        val action: Int,
        val modifiers: Int = 0,
        val scancode: Int = 0,
    ) : InputEvent

    data class MouseButton(
        val button: Int,
        val action: Int,
        val modifiers: Int = 0,
    ) : InputEvent

    /** Absolute cursor position in window pixels, which is what GLFW reports. */
    data class CursorPos(val x: Float, val y: Float) : InputEvent

    data class Scroll(val xOffset: Float, val yOffset: Float) : InputEvent

    /** A typed character, needed for chat and sign text. */
    data class Char(val codepoint: Int) : InputEvent

    data class CursorMode(val mode: Int) : InputEvent
}
