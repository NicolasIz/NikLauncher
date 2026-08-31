package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw
import com.niklauncher.core.input.InputEvent

/**
 * What the on-screen controls mean.
 *
 * The overlay reports touches; this decides what the game should be told. Kept
 * here rather than in the Android layer for the same reason the gesture
 * translator is: this is where the behaviour a player would notice lives -
 * latching, cursor mode, releasing what a lifted finger was holding - and none
 * of it needs a device to check.
 */
class ControlInputTranslator(layout: ControlLayout) {

    /** Buttons currently held, by control id. Latched toggles stay here. */
    private val held = mutableSetOf<String>()

    private val joysticks = mutableMapOf<String, JoystickTranslator>()

    private var layout: ControlLayout = layout
        set(value) {
            field = value
            joysticks.keys.retainAll(value.joysticks.map { it.id }.toSet())
            value.joysticks.forEach { joystick ->
                joysticks.getOrPut(joystick.id) { JoystickTranslator(joystick) }
                    .updateConfig(joystick)
            }
        }

    init {
        // Runs the setter, so the joystick translators exist from the start.
        this.layout = layout
    }

    /**
     * True while the player is pointing at a menu rather than looking around.
     *
     * The launcher's own idea, not the game's: it decides whether a drag turns
     * the camera or moves a pointer. Minecraft keeps its own cursor state and
     * would fight us if we pushed this as an event.
     */
    var inMenu: Boolean = false
        private set

    /** Something the Android layer has to do that is not an input event. */
    enum class SideEffect { TOGGLE_KEYBOARD }

    data class Output(
        val events: List<InputEvent> = emptyList(),
        val sideEffects: List<SideEffect> = emptyList(),
    )

    fun useLayout(layout: ControlLayout) {
        this.layout = layout
    }

    fun press(button: ControlButton): Output = when {
        button.toggle && held.contains(button.id) -> {
            held.remove(button.id)
            Output(events = listOfNotNull(button.action.toEvent(Glfw.RELEASE)))
        }

        button.toggle -> {
            held.add(button.id)
            Output(events = listOfNotNull(button.action.toEvent(Glfw.PRESS)))
        }

        else -> {
            held.add(button.id)
            act(button, Glfw.PRESS)
        }
    }

    /**
     * A latched toggle ignores the lift: that is what makes it a toggle, and it
     * is how a player holds sneak without holding a finger down.
     */
    fun release(button: ControlButton): Output {
        if (button.toggle) return Output()
        if (!held.remove(button.id)) return Output()
        return act(button, Glfw.RELEASE)
    }

    fun moveJoystick(joystick: ControlJoystick, dx: Float, dy: Float): Output =
        Output(events = translator(joystick).update(dx, dy))

    fun releaseJoystick(joystick: ControlJoystick): Output =
        Output(events = translator(joystick).release())

    /**
     * Everything held, let go at once.
     *
     * Latched toggles are cleared too: focus is gone, and coming back to a
     * game still sneaking because a toggle survived is worse than having to
     * press it again.
     */
    fun releaseAll(): Output {
        val events = buildList {
            layout.buttons
                .filter { held.contains(it.id) }
                .forEach { addAll(listOfNotNull(it.action.toEvent(Glfw.RELEASE))) }
            layout.joysticks.forEach { addAll(translator(it).release()) }
        }
        held.clear()
        return Output(events = events)
    }

    private fun act(button: ControlButton, action: Int): Output = when (button.action.kind) {
        ControlActionKind.TOGGLE_CURSOR -> {
            if (action == Glfw.PRESS) inMenu = !inMenu
            Output()
        }

        ControlActionKind.TOGGLE_KEYBOARD ->
            if (action == Glfw.PRESS) Output(sideEffects = listOf(SideEffect.TOGGLE_KEYBOARD)) else Output()

        ControlActionKind.NONE -> Output()

        else -> Output(events = listOfNotNull(button.action.toEvent(action)))
    }

    private fun translator(joystick: ControlJoystick): JoystickTranslator =
        joysticks.getOrPut(joystick.id) { JoystickTranslator(joystick) }
}
