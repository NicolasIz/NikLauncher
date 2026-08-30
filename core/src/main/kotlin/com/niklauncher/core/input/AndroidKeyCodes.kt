package com.niklauncher.core.input

/**
 * The `android.view.KeyEvent` codes we translate from.
 *
 * Mirrored as plain integers so :core stays free of Android imports and the
 * mapping below can be unit-tested on a desktop JVM. The values are frozen
 * public API, so duplicating them is safe.
 */
object AndroidKeyCodes {
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23

    const val KEY_0 = 7
    const val KEY_9 = 16
    const val KEY_A = 29
    const val KEY_Z = 54

    const val COMMA = 55
    const val PERIOD = 56
    const val ALT_LEFT = 57
    const val ALT_RIGHT = 58
    const val SHIFT_LEFT = 59
    const val SHIFT_RIGHT = 60
    const val TAB = 61
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val GRAVE = 68
    const val MINUS = 69
    const val EQUALS = 70
    const val LEFT_BRACKET = 71
    const val RIGHT_BRACKET = 72
    const val BACKSLASH = 73
    const val SEMICOLON = 74
    const val APOSTROPHE = 75
    const val SLASH = 76

    const val PAGE_UP = 92
    const val PAGE_DOWN = 93
    const val ESCAPE = 111
    const val FORWARD_DEL = 112
    const val CTRL_LEFT = 113
    const val CTRL_RIGHT = 114
    const val CAPS_LOCK = 115
    const val INSERT = 124
    const val MOVE_HOME = 122
    const val MOVE_END = 123

    const val F1 = 131
    const val F12 = 142

    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109

    const val BACK = 4

    /** Bit flags from `KeyEvent.getMetaState()`. */
    const val META_SHIFT_ON = 0x1
    const val META_ALT_ON = 0x02
    const val META_CTRL_ON = 0x1000
    const val META_META_ON = 0x10000
}

/**
 * Translates Android key input into GLFW key input.
 *
 * Physical keyboards and gamepads both arrive as Android key codes; Minecraft
 * only understands GLFW ones. Gamepad buttons are mapped to the keyboard keys
 * their function corresponds to, because Minecraft's own gamepad support does
 * not exist - the game only ever sees a keyboard and a mouse.
 */
object AndroidKeyMapper {

    fun toGlfwKey(androidKeyCode: Int): Int? = when (androidKeyCode) {
        in AndroidKeyCodes.KEY_A..AndroidKeyCodes.KEY_Z ->
            Glfw.KEY_A + (androidKeyCode - AndroidKeyCodes.KEY_A)

        in AndroidKeyCodes.KEY_0..AndroidKeyCodes.KEY_9 ->
            Glfw.KEY_0 + (androidKeyCode - AndroidKeyCodes.KEY_0)

        in AndroidKeyCodes.F1..AndroidKeyCodes.F12 ->
            Glfw.KEY_F1 + (androidKeyCode - AndroidKeyCodes.F1)

        AndroidKeyCodes.SPACE -> Glfw.KEY_SPACE
        AndroidKeyCodes.ENTER -> Glfw.KEY_ENTER
        AndroidKeyCodes.TAB -> Glfw.KEY_TAB
        AndroidKeyCodes.DEL -> Glfw.KEY_BACKSPACE
        AndroidKeyCodes.FORWARD_DEL -> Glfw.KEY_DELETE
        AndroidKeyCodes.ESCAPE -> Glfw.KEY_ESCAPE
        AndroidKeyCodes.INSERT -> Glfw.KEY_INSERT
        AndroidKeyCodes.MOVE_HOME -> Glfw.KEY_HOME
        AndroidKeyCodes.MOVE_END -> Glfw.KEY_END
        AndroidKeyCodes.PAGE_UP -> Glfw.KEY_PAGE_UP
        AndroidKeyCodes.PAGE_DOWN -> Glfw.KEY_PAGE_DOWN
        AndroidKeyCodes.CAPS_LOCK -> Glfw.KEY_CAPS_LOCK

        AndroidKeyCodes.DPAD_UP -> Glfw.KEY_UP
        AndroidKeyCodes.DPAD_DOWN -> Glfw.KEY_DOWN
        AndroidKeyCodes.DPAD_LEFT -> Glfw.KEY_LEFT
        AndroidKeyCodes.DPAD_RIGHT -> Glfw.KEY_RIGHT

        AndroidKeyCodes.SHIFT_LEFT -> Glfw.KEY_LEFT_SHIFT
        AndroidKeyCodes.SHIFT_RIGHT -> Glfw.KEY_RIGHT_SHIFT
        AndroidKeyCodes.CTRL_LEFT -> Glfw.KEY_LEFT_CONTROL
        AndroidKeyCodes.CTRL_RIGHT -> Glfw.KEY_RIGHT_CONTROL
        AndroidKeyCodes.ALT_LEFT -> Glfw.KEY_LEFT_ALT
        AndroidKeyCodes.ALT_RIGHT -> Glfw.KEY_RIGHT_ALT

        AndroidKeyCodes.COMMA -> Glfw.KEY_COMMA
        AndroidKeyCodes.PERIOD -> Glfw.KEY_PERIOD
        AndroidKeyCodes.MINUS -> Glfw.KEY_MINUS
        AndroidKeyCodes.EQUALS -> Glfw.KEY_EQUAL
        AndroidKeyCodes.LEFT_BRACKET -> Glfw.KEY_LEFT_BRACKET
        AndroidKeyCodes.RIGHT_BRACKET -> Glfw.KEY_RIGHT_BRACKET
        AndroidKeyCodes.BACKSLASH -> Glfw.KEY_BACKSLASH
        AndroidKeyCodes.SEMICOLON -> Glfw.KEY_SEMICOLON
        AndroidKeyCodes.APOSTROPHE -> Glfw.KEY_APOSTROPHE
        AndroidKeyCodes.SLASH -> Glfw.KEY_SLASH
        AndroidKeyCodes.GRAVE -> Glfw.KEY_GRAVE_ACCENT

        else -> null
    }

    /** Converts an Android meta state into GLFW's modifier bitmask. */
    fun toGlfwModifiers(metaState: Int): Int {
        var modifiers = 0
        if (metaState and AndroidKeyCodes.META_SHIFT_ON != 0) modifiers = modifiers or Glfw.MOD_SHIFT
        if (metaState and AndroidKeyCodes.META_CTRL_ON != 0) modifiers = modifiers or Glfw.MOD_CONTROL
        if (metaState and AndroidKeyCodes.META_ALT_ON != 0) modifiers = modifiers or Glfw.MOD_ALT
        if (metaState and AndroidKeyCodes.META_META_ON != 0) modifiers = modifiers or Glfw.MOD_SUPER
        return modifiers
    }

    fun isGamepadButton(androidKeyCode: Int): Boolean =
        androidKeyCode in AndroidKeyCodes.BUTTON_A..AndroidKeyCodes.BUTTON_SELECT
}
