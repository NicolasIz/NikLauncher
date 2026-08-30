package com.niklauncher.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GamepadMappingTest {

    private val mapping = GamepadMapping.DEFAULT

    @Test
    fun `A jumps and the right trigger attacks`() {
        assertEquals(ControlBinding.key(Glfw.KEY_SPACE), mapping.bindingFor(AndroidKeyCodes.BUTTON_A))
        assertEquals(
            ControlBinding.mouse(Glfw.MOUSE_BUTTON_LEFT),
            mapping.bindingFor(AndroidKeyCodes.BUTTON_R2),
        )
    }

    @Test
    fun `an unmapped button yields nothing rather than a wrong key`() {
        assertNull(mapping.bindingFor(AndroidKeyCodes.KEY_A))
    }

    @Test
    fun `bindings produce the right event kind`() {
        val keyEvent = ControlBinding.key(Glfw.KEY_E).toEvent(Glfw.PRESS)
        assertEquals(InputEvent.Key(Glfw.KEY_E, Glfw.PRESS), assertIs<InputEvent.Key>(keyEvent))

        val mouseEvent = ControlBinding.mouse(Glfw.MOUSE_BUTTON_RIGHT).toEvent(Glfw.RELEASE)
        assertEquals(
            InputEvent.MouseButton(Glfw.MOUSE_BUTTON_RIGHT, Glfw.RELEASE),
            assertIs<InputEvent.MouseButton>(mouseEvent),
        )
    }

    @Test
    fun `every default binding targets a real control`() {
        for ((button, binding) in mapping.buttons) {
            if (binding.isMouse) {
                assertEquals(true, binding.code in 0..2, "button $button maps to an unknown mouse button")
            } else {
                assertEquals(true, binding.code > 0, "button $button maps to an invalid key")
            }
        }
    }
}
