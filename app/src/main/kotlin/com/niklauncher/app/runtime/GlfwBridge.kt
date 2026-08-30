package com.niklauncher.app.runtime

import android.view.Surface
import com.niklauncher.core.input.Glfw
import com.niklauncher.core.input.InputEvent

/**
 * Feeds NikLauncher's input into Minecraft.
 *
 * Everything the player does arrives here as an [InputEvent] - already
 * translated from touch, keyboard or gamepad by the pure Kotlin layer - and is
 * pushed into the native GLFW bridge, which the game drains inside
 * `glfwPollEvents`.
 *
 * The split is deliberate: deciding *what* a gesture means is testable Kotlin,
 * while this class only carries the result across the JNI boundary.
 */
object GlfwBridge {

    private const val LIBRARY_NAME = "nikglfw"

    private val loadError: Throwable? = runCatching { System.loadLibrary(LIBRARY_NAME) }.exceptionOrNull()

    val isAvailable: Boolean get() = loadError == null

    val unavailableReason: String? get() = loadError?.message

    private external fun nativeSetSurface(surface: Surface?)
    private external fun nativePushKey(key: Int, scancode: Int, action: Int, mods: Int)
    private external fun nativePushChar(codepoint: Int, mods: Int)
    private external fun nativePushMouseButton(button: Int, action: Int, mods: Int)
    private external fun nativePushCursorPos(x: Double, y: Double)
    private external fun nativePushScroll(x: Double, y: Double)
    private external fun nativePushWindowSize(width: Int, height: Int)
    private external fun nativePushFocus(focused: Boolean)
    private external fun nativePushClose()
    private external fun nativeReleaseAll()
    private external fun nativeDroppedEvents(): Long
    private external fun nativePendingEvents(): Int

    /**
     * Hands the game its drawing surface. Must happen before Minecraft creates
     * its window, since the EGL surface is built on it.
     */
    fun attachSurface(surface: Surface?) {
        if (isAvailable) nativeSetSurface(surface)
    }

    fun send(event: InputEvent) {
        if (!isAvailable) return
        when (event) {
            is InputEvent.Key -> nativePushKey(event.glfwKey, event.scancode, event.action, event.modifiers)
            is InputEvent.Char -> nativePushChar(event.codepoint, 0)
            is InputEvent.MouseButton -> nativePushMouseButton(event.button, event.action, event.modifiers)
            is InputEvent.CursorPos -> nativePushCursorPos(event.x.toDouble(), event.y.toDouble())
            is InputEvent.Scroll -> nativePushScroll(event.xOffset.toDouble(), event.yOffset.toDouble())
            // The cursor mode is the game's own state, set through glfwSetInputMode
            // from inside Minecraft; pushing it as an event would fight that.
            is InputEvent.CursorMode -> Unit
        }
    }

    fun send(events: List<InputEvent>) {
        events.forEach(::send)
    }

    fun setWindowSize(width: Int, height: Int) {
        if (isAvailable) nativePushWindowSize(width, height)
    }

    /** Focus loss also releases everything held, on the native side. */
    fun setFocused(focused: Boolean) {
        if (isAvailable) nativePushFocus(focused)
    }

    fun requestClose() {
        if (isAvailable) nativePushClose()
    }

    fun releaseAllInput() {
        if (isAvailable) nativeReleaseAll()
    }

    /**
     * Diagnostics. [droppedEvents] above zero means the game thread stalled
     * long enough for the input queue to back up - useful when a player reports
     * input feeling lost rather than merely late.
     */
    fun droppedEvents(): Long = if (isAvailable) nativeDroppedEvents() else 0

    fun pendingEvents(): Int = if (isAvailable) nativePendingEvents() else 0

    /** Convenience for the common case of a plain key press or release. */
    fun sendKey(glfwKey: Int, pressed: Boolean, modifiers: Int = 0) {
        send(
            InputEvent.Key(
                glfwKey = glfwKey,
                action = if (pressed) Glfw.PRESS else Glfw.RELEASE,
                modifiers = modifiers,
            ),
        )
    }
}
