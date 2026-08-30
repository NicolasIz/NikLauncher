package com.niklauncher.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidKeyMapperTest {

    @Test
    fun `maps the letter range`() {
        assertEquals(Glfw.KEY_A, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.KEY_A))
        assertEquals(Glfw.KEY_Z, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.KEY_Z))
        assertEquals(Glfw.KEY_W, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.KEY_A + 22))
    }

    @Test
    fun `maps the digit range`() {
        assertEquals(Glfw.KEY_0, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.KEY_0))
        assertEquals(Glfw.KEY_9, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.KEY_9))
    }

    @Test
    fun `maps the function key range`() {
        assertEquals(Glfw.KEY_F1, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.F1))
        assertEquals(Glfw.KEY_F12, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.F12))
        assertEquals(Glfw.KEY_F5, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.F1 + 4))
    }

    @Test
    fun `maps the keys Minecraft actually needs`() {
        assertEquals(Glfw.KEY_SPACE, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.SPACE))
        assertEquals(Glfw.KEY_LEFT_SHIFT, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.SHIFT_LEFT))
        assertEquals(Glfw.KEY_LEFT_CONTROL, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.CTRL_LEFT))
        assertEquals(Glfw.KEY_ESCAPE, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.ESCAPE))
        assertEquals(Glfw.KEY_ENTER, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.ENTER))
        // Android's DEL is backspace; FORWARD_DEL is the delete key.
        assertEquals(Glfw.KEY_BACKSPACE, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.DEL))
        assertEquals(Glfw.KEY_DELETE, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.FORWARD_DEL))
    }

    @Test
    fun `maps arrow keys`() {
        assertEquals(Glfw.KEY_UP, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.DPAD_UP))
        assertEquals(Glfw.KEY_LEFT, AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.DPAD_LEFT))
    }

    @Test
    fun `returns null for keys with no GLFW equivalent`() {
        assertNull(AndroidKeyMapper.toGlfwKey(AndroidKeyCodes.BACK))
        assertNull(AndroidKeyMapper.toGlfwKey(9999))
    }

    @Test
    fun `translates modifier flags`() {
        assertEquals(0, AndroidKeyMapper.toGlfwModifiers(0))
        assertEquals(Glfw.MOD_SHIFT, AndroidKeyMapper.toGlfwModifiers(AndroidKeyCodes.META_SHIFT_ON))
        assertEquals(
            Glfw.MOD_SHIFT or Glfw.MOD_CONTROL,
            AndroidKeyMapper.toGlfwModifiers(AndroidKeyCodes.META_SHIFT_ON or AndroidKeyCodes.META_CTRL_ON),
        )
    }

    @Test
    fun `recognises gamepad buttons`() {
        assertTrue(AndroidKeyMapper.isGamepadButton(AndroidKeyCodes.BUTTON_A))
        assertTrue(AndroidKeyMapper.isGamepadButton(AndroidKeyCodes.BUTTON_START))
        assertTrue(!AndroidKeyMapper.isGamepadButton(AndroidKeyCodes.KEY_A))
    }
}
