package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw

/**
 * The layouts NikLauncher ships with.
 *
 * The default deliberately keeps the middle of the screen clear: that area is
 * the look-and-attack surface, and every control placed there is one the player
 * hits by accident while turning.
 */
object ControlPresets {

    const val DEFAULT_ID = "default"

    fun default(): ControlLayout = ControlLayout(
        id = DEFAULT_ID,
        name = "Predeterminado",
        joysticks = listOf(
            ControlJoystick(id = "move", x = 0.14f, y = 0.74f),
        ),
        buttons = listOf(
            ControlButton(
                id = "jump",
                label = "Saltar",
                x = 0.90f,
                y = 0.74f,
                widthDp = 68f,
                heightDp = 68f,
                action = ControlAction.key(Glfw.KEY_SPACE),
            ),
            ControlButton(
                id = "sneak",
                label = "Agacharse",
                x = 0.76f,
                y = 0.86f,
                action = ControlAction.key(Glfw.KEY_LEFT_SHIFT),
                // Latching matters here: holding a finger down to stay crouched
                // on a ledge is exactly when you need the other hand free.
                toggle = true,
            ),
            ControlButton(
                id = "place",
                label = "Usar",
                x = 0.90f,
                y = 0.58f,
                widthDp = 62f,
                heightDp = 62f,
                action = ControlAction.mouse(Glfw.MOUSE_BUTTON_RIGHT),
            ),
            ControlButton(
                id = "inventory",
                label = "Inv",
                x = 0.90f,
                y = 0.30f,
                action = ControlAction.key(Glfw.KEY_E),
            ),
            ControlButton(
                id = "drop",
                label = "Tirar",
                x = 0.76f,
                y = 0.30f,
                widthDp = 48f,
                heightDp = 48f,
                action = ControlAction.key(Glfw.KEY_Q),
            ),
            ControlButton(
                id = "hotbar-prev",
                label = "◀",
                x = 0.34f,
                y = 0.92f,
                widthDp = 44f,
                heightDp = 44f,
                action = ControlAction(ControlActionKind.SCROLL, 1),
            ),
            ControlButton(
                id = "hotbar-next",
                label = "▶",
                x = 0.66f,
                y = 0.92f,
                widthDp = 44f,
                heightDp = 44f,
                action = ControlAction(ControlActionKind.SCROLL, -1),
            ),
            ControlButton(
                id = "cursor",
                label = "Cursor",
                x = 0.08f,
                y = 0.08f,
                widthDp = 52f,
                heightDp = 40f,
                cornerPercent = 25,
                action = ControlAction.toggleCursor,
                visibleInMenu = true,
            ),
            ControlButton(
                id = "menu",
                label = "Esc",
                x = 0.20f,
                y = 0.08f,
                widthDp = 48f,
                heightDp = 40f,
                cornerPercent = 25,
                action = ControlAction.key(Glfw.KEY_ESCAPE),
                visibleInMenu = true,
            ),
            ControlButton(
                id = "chat",
                label = "Chat",
                x = 0.32f,
                y = 0.08f,
                widthDp = 52f,
                heightDp = 40f,
                cornerPercent = 25,
                action = ControlAction.key(Glfw.KEY_T),
            ),
            ControlButton(
                id = "perspective",
                label = "F5",
                x = 0.44f,
                y = 0.08f,
                widthDp = 44f,
                heightDp = 40f,
                cornerPercent = 25,
                action = ControlAction.key(Glfw.KEY_F5),
            ),
        ),
    )

    /**
     * A stripped-back layout for players on a physical keyboard and mouse, who
     * only need the few controls a keyboard cannot reach.
     */
    fun keyboardCompanion(): ControlLayout = ControlLayout(
        id = "keyboard",
        name = "Con teclado",
        buttons = listOf(
            ControlButton(
                id = "cursor",
                label = "Cursor",
                x = 0.08f,
                y = 0.08f,
                widthDp = 52f,
                heightDp = 40f,
                cornerPercent = 25,
                action = ControlAction.toggleCursor,
                visibleInMenu = true,
            ),
            ControlButton(
                id = "menu",
                label = "Esc",
                x = 0.20f,
                y = 0.08f,
                widthDp = 48f,
                heightDp = 40f,
                cornerPercent = 25,
                action = ControlAction.key(Glfw.KEY_ESCAPE),
                visibleInMenu = true,
            ),
        ),
    )

    fun all(): List<ControlLayout> = listOf(default(), keyboardCompanion())
}
