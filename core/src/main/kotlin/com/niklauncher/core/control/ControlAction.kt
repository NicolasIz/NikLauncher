package com.niklauncher.core.control

import com.niklauncher.core.input.ControlBinding
import com.niklauncher.core.input.Glfw
import com.niklauncher.core.input.InputEvent
import kotlinx.serialization.Serializable

/**
 * What pressing an on-screen control does.
 *
 * Modelled as a kind plus an integer rather than a sealed hierarchy so the
 * whole layout serialises as plain JSON. Layouts are user data that has to
 * survive app updates, and a polymorphic encoding is far easier to break.
 */
@Serializable
enum class ControlActionKind {
    /** Sends a GLFW key. */
    KEY,

    /** Sends a GLFW mouse button. */
    MOUSE_BUTTON,

    /** Switches between look mode and menu-pointer mode. */
    TOGGLE_CURSOR,

    /** Scrolls the hotbar; the code carries the direction (+1 / -1). */
    SCROLL,

    /** Shows or hides the soft keyboard, for chat and signs. */
    TOGGLE_KEYBOARD,

    /** Does nothing; used by placeholders while editing a layout. */
    NONE,
}

@Serializable
data class ControlAction(
    val kind: ControlActionKind,
    /** GLFW key code, mouse button, or scroll direction, depending on [kind]. */
    val code: Int = 0,
) {
    /**
     * The event this action emits, or null when the action is not a simple
     * key/button and the caller has to handle it (cursor mode, keyboard, ...).
     */
    fun toEvent(action: Int): InputEvent? = when (kind) {
        ControlActionKind.KEY -> InputEvent.Key(code, action)
        ControlActionKind.MOUSE_BUTTON -> InputEvent.MouseButton(code, action)
        ControlActionKind.SCROLL ->
            if (action == Glfw.PRESS) InputEvent.Scroll(0f, code.toFloat()) else null
        else -> null
    }

    fun asBinding(): ControlBinding? = when (kind) {
        ControlActionKind.KEY -> ControlBinding.key(code)
        ControlActionKind.MOUSE_BUTTON -> ControlBinding.mouse(code)
        else -> null
    }

    companion object {
        fun key(glfwKey: Int) = ControlAction(ControlActionKind.KEY, glfwKey)
        fun mouse(button: Int) = ControlAction(ControlActionKind.MOUSE_BUTTON, button)
        val toggleCursor = ControlAction(ControlActionKind.TOGGLE_CURSOR)
        val toggleKeyboard = ControlAction(ControlActionKind.TOGGLE_KEYBOARD)
        val none = ControlAction(ControlActionKind.NONE)
    }
}
