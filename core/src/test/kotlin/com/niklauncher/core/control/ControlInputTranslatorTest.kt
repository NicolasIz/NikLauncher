package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw
import com.niklauncher.core.input.InputEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlInputTranslatorTest {

    private fun button(
        id: String,
        action: ControlAction,
        toggle: Boolean = false,
    ) = ControlButton(id = id, label = id, x = 0.5f, y = 0.5f, action = action, toggle = toggle)

    private val jump = button("jump", ControlAction(ControlActionKind.KEY, Glfw.KEY_SPACE))
    private val sneak = button("sneak", ControlAction(ControlActionKind.KEY, Glfw.KEY_LEFT_SHIFT), toggle = true)
    private val attack = button("attack", ControlAction(ControlActionKind.MOUSE_BUTTON, Glfw.MOUSE_BUTTON_LEFT))
    private val cursor = button("cursor", ControlAction(ControlActionKind.TOGGLE_CURSOR))
    private val keyboard = button("keyboard", ControlAction(ControlActionKind.TOGGLE_KEYBOARD))
    private val stick = ControlJoystick(id = "move", x = 0.2f, y = 0.75f)

    private fun translator() = ControlInputTranslator(
        ControlLayout(
            id = "test",
            name = "Test",
            buttons = listOf(jump, sneak, attack, cursor, keyboard),
            joysticks = listOf(stick),
        ),
    )

    @Test
    fun `a plain button presses and releases`() {
        val t = translator()
        assertEquals(listOf(InputEvent.Key(Glfw.KEY_SPACE, Glfw.PRESS)), t.press(jump).events)
        assertEquals(listOf(InputEvent.Key(Glfw.KEY_SPACE, Glfw.RELEASE)), t.release(jump).events)
    }

    @Test
    fun `a toggle latches instead of releasing when the finger lifts`() {
        val t = translator()
        assertEquals(listOf(InputEvent.Key(Glfw.KEY_LEFT_SHIFT, Glfw.PRESS)), t.press(sneak).events)
        assertTrue(t.release(sneak).events.isEmpty(), "lifting off a toggle must not release it")
        assertEquals(listOf(InputEvent.Key(Glfw.KEY_LEFT_SHIFT, Glfw.RELEASE)), t.press(sneak).events)
    }

    @Test
    fun `releasing a button that was never pressed sends nothing`() {
        // Reachable in practice: a finger that slides off a control lifts
        // outside it, and the overlay reports the release either way.
        assertTrue(translator().release(jump).events.isEmpty())
    }

    @Test
    fun `the cursor toggle changes mode and sends no event`() {
        val t = translator()
        assertFalse(t.inMenu)
        assertTrue(t.press(cursor).events.isEmpty(), "Minecraft owns its cursor state; pushing one would fight it")
        assertTrue(t.inMenu)
        t.release(cursor)
        t.press(cursor)
        assertFalse(t.inMenu)
    }

    @Test
    fun `the keyboard toggle is a side effect, not an event`() {
        val output = translator().press(keyboard)
        assertTrue(output.events.isEmpty())
        assertEquals(listOf(ControlInputTranslator.SideEffect.TOGGLE_KEYBOARD), output.sideEffects)
    }

    @Test
    fun `losing focus releases what is held, latched toggles included`() {
        val t = translator()
        t.press(jump)
        t.press(attack)
        t.press(sneak)
        t.release(sneak) // still latched

        val released = t.releaseAll().events
        assertTrue(released.contains(InputEvent.Key(Glfw.KEY_SPACE, Glfw.RELEASE)))
        assertTrue(released.contains(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE)))
        assertTrue(
            released.contains(InputEvent.Key(Glfw.KEY_LEFT_SHIFT, Glfw.RELEASE)),
            "coming back to a game still sneaking is worse than pressing it again",
        )
        assertTrue(t.releaseAll().events.isEmpty(), "a second release must not repeat them")
    }

    @Test
    fun `a joystick pushed forward walks, and releasing stops`() {
        val t = translator()
        val walking = t.moveJoystick(stick, 0f, -1f).events
        assertTrue(walking.contains(InputEvent.Key(stick.upKey, Glfw.PRESS)))
        assertTrue(t.releaseJoystick(stick).events.contains(InputEvent.Key(stick.upKey, Glfw.RELEASE)))
    }

    @Test
    fun `swapping the layout keeps working and forgets the old joysticks`() {
        val t = translator()
        t.press(jump)
        t.useLayout(ControlLayout(id = "other", name = "Other", buttons = listOf(jump)))
        // The button is still held, so it must still be released on focus loss.
        assertTrue(t.releaseAll().events.contains(InputEvent.Key(Glfw.KEY_SPACE, Glfw.RELEASE)))
    }
}
