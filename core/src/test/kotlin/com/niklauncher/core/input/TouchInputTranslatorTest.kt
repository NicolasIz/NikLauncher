package com.niklauncher.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TouchInputTranslatorTest {

    private fun translator(config: TouchConfig = TouchConfig.DEFAULT) =
        TouchInputTranslator(config, viewportWidth = 1000f, viewportHeight = 500f)

    @Test
    fun `a quick stationary press is a tap that clicks and releases`() {
        val subject = translator()

        subject.onPointerDown(0, 500f, 250f, timeMs = 0)
        val events = subject.onPointerUp(0, 502f, 251f, timeMs = 100)

        assertEquals(2, events.size)
        assertEquals(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.PRESS), events[0])
        assertEquals(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE), events[1])
    }

    @Test
    fun `a press held past the threshold starts holding the attack button`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)

        assertTrue(subject.onTick(timeMs = 100).isEmpty(), "too early to be a hold")

        val started = subject.onTick(timeMs = 400)
        assertEquals(listOf(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.PRESS)), started)

        // Ticking again must not re-press: repeated presses flood the game.
        assertTrue(subject.onTick(timeMs = 800).isEmpty())

        val released = subject.onPointerUp(0, 500f, 250f, timeMs = 900)
        assertEquals(listOf(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE)), released)
    }

    @Test
    fun `dragging looks around and does not click`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)

        val moved = subject.onPointerMove(0, 540f, 260f, timeMs = 50)
        val cursor = assertIs<InputEvent.CursorPos>(moved.single())
        assertEquals(40f, cursor.x)
        assertEquals(10f, cursor.y)

        assertTrue(subject.onPointerUp(0, 540f, 260f, timeMs = 80).isEmpty(), "a drag is not a tap")
    }

    @Test
    fun `a drag past the slop is never treated as a tap even if quick`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)
        subject.onPointerMove(0, 600f, 250f, timeMs = 20)
        subject.onPointerMove(0, 500f, 250f, timeMs = 40)

        // Back where it started, and inside the tap duration - but it moved.
        assertTrue(subject.onPointerUp(0, 500f, 250f, timeMs = 60).isEmpty())
    }

    @Test
    fun `a moving finger never starts a hold`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)
        subject.onPointerMove(0, 560f, 250f, timeMs = 100)

        assertTrue(subject.onTick(timeMs = 900).isEmpty(), "looking around must not start mining")
    }

    @Test
    fun `sensitivity scales look movement`() {
        val subject = translator(TouchConfig.DEFAULT.copy(lookSensitivity = 2f))
        subject.onPointerDown(0, 100f, 100f, timeMs = 0)

        val cursor = assertIs<InputEvent.CursorPos>(subject.onPointerMove(0, 110f, 105f, timeMs = 16).single())
        assertEquals(20f, cursor.x)
        assertEquals(10f, cursor.y)
    }

    @Test
    fun `inverted look flips the vertical axis only`() {
        val subject = translator(TouchConfig.DEFAULT.copy(invertLook = true))
        subject.onPointerDown(0, 100f, 100f, timeMs = 0)

        val cursor = assertIs<InputEvent.CursorPos>(subject.onPointerMove(0, 110f, 110f, timeMs = 16).single())
        assertEquals(10f, cursor.x)
        assertEquals(-10f, cursor.y)
    }

    @Test
    fun `a second finger does not fight the one already looking`() {
        val subject = translator()
        subject.onPointerDown(0, 300f, 250f, timeMs = 0)
        subject.onPointerDown(1, 700f, 250f, timeMs = 10)

        assertTrue(subject.onPointerMove(1, 750f, 250f, timeMs = 20).isEmpty())
        assertTrue(subject.onPointerMove(0, 320f, 250f, timeMs = 30).isNotEmpty())
    }

    @Test
    fun `menu mode moves an absolute pointer and clicks where you touch`() {
        val subject = translator()
        subject.setMode(PointerMode.MENU)

        val down = subject.onPointerDown(0, 420f, 130f, timeMs = 0)
        assertEquals(InputEvent.CursorPos(420f, 130f), down[0])
        assertEquals(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.PRESS), down[1])

        val moved = subject.onPointerMove(0, 430f, 140f, timeMs = 20)
        assertEquals(InputEvent.CursorPos(430f, 140f), moved.single())

        val up = subject.onPointerUp(0, 430f, 140f, timeMs = 40)
        assertEquals(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE), up.single())
    }

    @Test
    fun `switching to menu mode releases a held attack button`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)
        subject.onTick(timeMs = 400)

        val events = subject.setMode(PointerMode.MENU)

        assertTrue(
            events.contains(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE)),
            "a held button must not stay stuck when the inventory opens",
        )
        assertTrue(events.any { it is InputEvent.CursorMode })
    }

    @Test
    fun `entering menu mode frees the cursor and grabbing hides it again`() {
        val subject = translator()

        val toMenu = subject.setMode(PointerMode.MENU)
        assertEquals(Glfw.CURSOR_NORMAL, toMenu.filterIsInstance<InputEvent.CursorMode>().single().mode)

        val toGame = subject.setMode(PointerMode.GRABBED)
        assertEquals(Glfw.CURSOR_DISABLED, toGame.filterIsInstance<InputEvent.CursorMode>().single().mode)
    }

    @Test
    fun `setting the same mode twice does nothing`() {
        val subject = translator()
        assertTrue(subject.setMode(PointerMode.GRABBED).isEmpty())
    }

    @Test
    fun `a cancelled hold releases the button`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)
        subject.onTick(timeMs = 400)

        assertEquals(
            listOf(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE)),
            subject.onPointerCancel(0),
        )
    }

    @Test
    fun `a cancelled tap releases nothing`() {
        val subject = translator()
        subject.onPointerDown(0, 500f, 250f, timeMs = 0)
        assertTrue(subject.onPointerCancel(0).isEmpty())
    }

    @Test
    fun `smoothing trades latency for steadiness and can be turned off`() {
        val direct = translator(TouchConfig.DEFAULT.copy(smoothing = 0f))
        direct.onPointerDown(0, 0f, 0f, timeMs = 0)
        val sharp = assertIs<InputEvent.CursorPos>(direct.onPointerMove(0, 100f, 0f, timeMs = 16).single())
        assertEquals(100f, sharp.x, "no smoothing must pass the delta through untouched")

        val smoothed = translator(TouchConfig.DEFAULT.copy(smoothing = 0.5f))
        smoothed.onPointerDown(0, 0f, 0f, timeMs = 0)
        val soft = assertIs<InputEvent.CursorPos>(smoothed.onPointerMove(0, 100f, 0f, timeMs = 16).single())
        assertTrue(soft.x < sharp.x, "smoothing should lag the raw delta")
    }

    @Test
    fun `events for an unknown pointer are ignored`() {
        val subject = translator()
        assertTrue(subject.onPointerMove(99, 10f, 10f, timeMs = 0).isEmpty())
        assertTrue(subject.onPointerUp(99, 10f, 10f, timeMs = 0).isEmpty())
    }
}
