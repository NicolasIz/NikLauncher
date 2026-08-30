package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw
import com.niklauncher.core.input.InputEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JoystickTranslatorTest {

    private val joystick = ControlJoystick(id = "move", x = 0.15f, y = 0.75f)

    private fun translator() = JoystickTranslator(joystick)

    @Test
    fun `pushing forward presses W`() {
        val events = translator().update(dx = 0f, dy = -0.6f)
        assertEquals(listOf(InputEvent.Key(Glfw.KEY_W, Glfw.PRESS)), events)
    }

    @Test
    fun `a centred stick presses nothing`() {
        assertTrue(translator().update(dx = 0f, dy = 0f).isEmpty())
    }

    @Test
    fun `deflection below the threshold is ignored`() {
        assertTrue(translator().update(dx = 0.1f, dy = 0.1f).isEmpty())
    }

    @Test
    fun `holding a direction does not re-press it every frame`() {
        val subject = translator()
        assertEquals(1, subject.update(0f, -0.6f).size)
        // Re-sending a press each frame is a real source of input stutter.
        assertTrue(subject.update(0f, -0.7f).isEmpty())
        assertTrue(subject.update(0f, -0.8f).isEmpty())
    }

    @Test
    fun `changing direction releases the old key and presses the new one`() {
        val subject = translator()
        subject.update(0f, -0.6f)

        val events = subject.update(0f, 0.6f)

        assertEquals(InputEvent.Key(Glfw.KEY_W, Glfw.RELEASE), events[0])
        assertEquals(InputEvent.Key(Glfw.KEY_S, Glfw.PRESS), events[1])
    }

    @Test
    fun `diagonals press both keys`() {
        val events = translator().update(dx = 0.6f, dy = -0.6f).map { it as InputEvent.Key }.map { it.glfwKey }
        assertTrue(events.containsAll(listOf(Glfw.KEY_W, Glfw.KEY_D)))
    }

    @Test
    fun `full forward deflection also sprints`() {
        val events = translator().update(dx = 0f, dy = -1f).map { it as InputEvent.Key }.map { it.glfwKey }
        assertTrue(events.contains(Glfw.KEY_LEFT_CONTROL), "expected sprint at full deflection")
    }

    @Test
    fun `sprinting backwards is not a thing`() {
        val events = translator().update(dx = 0f, dy = 1f).map { it as InputEvent.Key }.map { it.glfwKey }
        assertTrue(!events.contains(Glfw.KEY_LEFT_CONTROL))
    }

    @Test
    fun `releasing the stick releases every key it held`() {
        val subject = translator()
        subject.update(dx = 0.6f, dy = -1f)

        val released = subject.release().map { it as InputEvent.Key }
        assertTrue(released.all { it.action == Glfw.RELEASE })
        assertTrue(released.map { it.glfwKey }.containsAll(listOf(Glfw.KEY_W, Glfw.KEY_D, Glfw.KEY_LEFT_CONTROL)))
        assertTrue(subject.release().isEmpty(), "releasing twice must not emit again")
    }

    @Test
    fun `deflection is normalised to the pad radius`() {
        val (dx, dy) = JoystickTranslator.deflection(offsetX = 40f, offsetY = 0f, radiusPx = 80f)
        assertEquals(0.5f, dx)
        assertEquals(0f, dy)
    }

    @Test
    fun `deflection beyond the pad edge is clamped to the unit circle`() {
        val (dx, dy) = JoystickTranslator.deflection(offsetX = 200f, offsetY = 200f, radiusPx = 80f)
        val magnitude = kotlin.math.hypot(dx, dy)
        assertTrue(magnitude <= 1.001f, "expected clamped magnitude, got $magnitude")
    }

    @Test
    fun `a zero radius does not divide by zero`() {
        assertEquals(0f to 0f, JoystickTranslator.deflection(10f, 10f, radiusPx = 0f))
    }
}
