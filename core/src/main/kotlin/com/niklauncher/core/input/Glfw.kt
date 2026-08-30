package com.niklauncher.core.input

/**
 * The GLFW constants Minecraft's input path speaks.
 *
 * Minecraft talks to GLFW through LWJGL, so on Android every touch, key and
 * gamepad press has to arrive as a synthetic GLFW event with exactly these
 * values. They are fixed by GLFW's ABI, which is why they are safe to hard-code.
 */
object Glfw {

    // Actions
    const val RELEASE = 0
    const val PRESS = 1
    const val REPEAT = 2

    // Modifier bitmask
    const val MOD_SHIFT = 0x0001
    const val MOD_CONTROL = 0x0002
    const val MOD_ALT = 0x0004
    const val MOD_SUPER = 0x0008

    // Mouse buttons
    const val MOUSE_BUTTON_LEFT = 0
    const val MOUSE_BUTTON_RIGHT = 1
    const val MOUSE_BUTTON_MIDDLE = 2

    // Printable keys
    const val KEY_SPACE = 32
    const val KEY_APOSTROPHE = 39
    const val KEY_COMMA = 44
    const val KEY_MINUS = 45
    const val KEY_PERIOD = 46
    const val KEY_SLASH = 47
    const val KEY_0 = 48
    const val KEY_1 = 49
    const val KEY_2 = 50
    const val KEY_3 = 51
    const val KEY_4 = 52
    const val KEY_5 = 53
    const val KEY_6 = 54
    const val KEY_7 = 55
    const val KEY_8 = 56
    const val KEY_9 = 57
    const val KEY_SEMICOLON = 59
    const val KEY_EQUAL = 61
    const val KEY_A = 65
    const val KEY_B = 66
    const val KEY_C = 67
    const val KEY_D = 68
    const val KEY_E = 69
    const val KEY_F = 70
    const val KEY_G = 71
    const val KEY_H = 72
    const val KEY_I = 73
    const val KEY_J = 74
    const val KEY_K = 75
    const val KEY_L = 76
    const val KEY_M = 77
    const val KEY_N = 78
    const val KEY_O = 79
    const val KEY_P = 80
    const val KEY_Q = 81
    const val KEY_R = 82
    const val KEY_S = 83
    const val KEY_T = 84
    const val KEY_U = 85
    const val KEY_V = 86
    const val KEY_W = 87
    const val KEY_X = 88
    const val KEY_Y = 89
    const val KEY_Z = 90
    const val KEY_LEFT_BRACKET = 91
    const val KEY_BACKSLASH = 92
    const val KEY_RIGHT_BRACKET = 93
    const val KEY_GRAVE_ACCENT = 96

    // Function keys
    const val KEY_ESCAPE = 256
    const val KEY_ENTER = 257
    const val KEY_TAB = 258
    const val KEY_BACKSPACE = 259
    const val KEY_INSERT = 260
    const val KEY_DELETE = 261
    const val KEY_RIGHT = 262
    const val KEY_LEFT = 263
    const val KEY_DOWN = 264
    const val KEY_UP = 265
    const val KEY_PAGE_UP = 266
    const val KEY_PAGE_DOWN = 267
    const val KEY_HOME = 268
    const val KEY_END = 269
    const val KEY_CAPS_LOCK = 280
    const val KEY_F1 = 290
    const val KEY_F2 = 291
    const val KEY_F3 = 292
    const val KEY_F4 = 293
    const val KEY_F5 = 294
    const val KEY_F6 = 295
    const val KEY_F7 = 296
    const val KEY_F8 = 297
    const val KEY_F9 = 298
    const val KEY_F10 = 299
    const val KEY_F11 = 300
    const val KEY_F12 = 301
    const val KEY_LEFT_SHIFT = 340
    const val KEY_LEFT_CONTROL = 341
    const val KEY_LEFT_ALT = 342
    const val KEY_LEFT_SUPER = 343
    const val KEY_RIGHT_SHIFT = 344
    const val KEY_RIGHT_CONTROL = 345
    const val KEY_RIGHT_ALT = 346

    /** How the cursor behaves; Minecraft grabs it during play and frees it in menus. */
    const val CURSOR_NORMAL = 0x00034001
    const val CURSOR_DISABLED = 0x00034003
}
