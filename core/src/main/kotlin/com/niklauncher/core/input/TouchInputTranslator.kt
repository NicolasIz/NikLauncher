package com.niklauncher.core.input

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Turns raw touch pointers into the GLFW events Minecraft expects.
 *
 * This is a plain state machine with no Android types, so the whole feel of the
 * controls - what counts as a tap, when a hold starts mining, how look
 * translates to cursor motion - is unit-testable rather than something that can
 * only be judged by playing.
 *
 * Touches that land on an on-screen control are consumed by the overlay and
 * never reach this class; what arrives here is the bare look area.
 */
class TouchInputTranslator(
    private var config: TouchConfig = TouchConfig.DEFAULT,
    private var viewportWidth: Float = 1f,
    private var viewportHeight: Float = 1f,
) {

    private class Pointer(
        val startX: Float,
        val startY: Float,
        val startTimeMs: Long,
        var lastX: Float,
        var lastY: Float,
        var maxDrift: Float = 0f,
        var holdStarted: Boolean = false,
    )

    private val pointers = LinkedHashMap<Int, Pointer>()

    /** The virtual cursor GLFW reports while the real one is hidden. */
    var cursorX: Float = 0f
        private set
    var cursorY: Float = 0f
        private set

    var mode: PointerMode = PointerMode.GRABBED
        private set

    private var smoothedDx = 0f
    private var smoothedDy = 0f

    /** The pointer currently driving the look; later fingers do not fight it. */
    private var lookPointerId: Int? = null

    fun updateConfig(config: TouchConfig) {
        this.config = config
    }

    fun updateViewport(width: Float, height: Float) {
        viewportWidth = width.coerceAtLeast(1f)
        viewportHeight = height.coerceAtLeast(1f)
        cursorX = cursorX.coerceIn(0f, viewportWidth)
        cursorY = cursorY.coerceIn(0f, viewportHeight)
    }

    /**
     * Switches between play and menu handling.
     *
     * Any in-flight gesture is released first: a finger that was holding the
     * attack button when the inventory opened must not leave the button stuck
     * down, which is the classic way mobile Minecraft ports break mining.
     */
    fun setMode(mode: PointerMode): List<InputEvent> {
        if (mode == this.mode) return emptyList()
        val events = releaseEverything().toMutableList()
        this.mode = mode
        if (mode == PointerMode.MENU) {
            cursorX = viewportWidth / 2f
            cursorY = viewportHeight / 2f
            events += InputEvent.CursorMode(Glfw.CURSOR_NORMAL)
            events += InputEvent.CursorPos(cursorX, cursorY)
        } else {
            events += InputEvent.CursorMode(Glfw.CURSOR_DISABLED)
        }
        return events
    }

    fun onPointerDown(pointerId: Int, x: Float, y: Float, timeMs: Long): List<InputEvent> {
        pointers[pointerId] = Pointer(x, y, timeMs, x, y)

        return when (mode) {
            PointerMode.MENU -> {
                cursorX = x
                cursorY = y
                listOf(
                    InputEvent.CursorPos(x, y),
                    InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.PRESS),
                )
            }
            PointerMode.GRABBED -> {
                if (lookPointerId == null) lookPointerId = pointerId
                smoothedDx = 0f
                smoothedDy = 0f
                emptyList()
            }
        }
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float, timeMs: Long): List<InputEvent> {
        val pointer = pointers[pointerId] ?: return emptyList()

        val dx = x - pointer.lastX
        val dy = y - pointer.lastY
        pointer.lastX = x
        pointer.lastY = y
        pointer.maxDrift = maxOf(pointer.maxDrift, hypot(x - pointer.startX, y - pointer.startY))

        return when (mode) {
            PointerMode.MENU -> {
                cursorX = x
                cursorY = y
                listOf(InputEvent.CursorPos(x, y))
            }
            PointerMode.GRABBED -> {
                if (pointerId != lookPointerId) return emptyList()
                val (moveX, moveY) = smooth(dx, dy)
                cursorX += moveX * config.lookSensitivity
                cursorY += moveY * config.lookSensitivity * if (config.invertLook) -1f else 1f
                listOf(InputEvent.CursorPos(cursorX, cursorY))
            }
        }
    }

    fun onPointerUp(pointerId: Int, x: Float, y: Float, timeMs: Long): List<InputEvent> {
        val pointer = pointers.remove(pointerId) ?: return emptyList()
        if (pointerId == lookPointerId) lookPointerId = pointers.keys.firstOrNull()

        return when (mode) {
            PointerMode.MENU -> listOf(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE))
            PointerMode.GRABBED -> when {
                // A hold that already pressed the button only needs releasing.
                pointer.holdStarted -> listOf(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE))

                isTap(pointer, timeMs) -> listOf(
                    InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.PRESS),
                    InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE),
                )

                else -> emptyList()
            }
        }
    }

    fun onPointerCancel(pointerId: Int): List<InputEvent> {
        val pointer = pointers.remove(pointerId) ?: return emptyList()
        if (pointerId == lookPointerId) lookPointerId = pointers.keys.firstOrNull()
        return if (pointer.holdStarted || mode == PointerMode.MENU) {
            listOf(InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE))
        } else {
            emptyList()
        }
    }

    /**
     * Call once per frame. A finger held still generates no motion events, so
     * without a tick the transition from "pressing" to "mining" would never
     * fire.
     */
    fun onTick(timeMs: Long): List<InputEvent> {
        if (mode != PointerMode.GRABBED) return emptyList()
        val events = mutableListOf<InputEvent>()
        for (pointer in pointers.values) {
            if (pointer.holdStarted) continue
            if (pointer.maxDrift > config.tapSlopPixels) continue
            if (timeMs - pointer.startTimeMs < config.longPressMs) continue
            pointer.holdStarted = true
            events += InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.PRESS)
        }
        return events
    }

    /** Releases anything held, for use when the window loses focus. */
    fun releaseEverything(): List<InputEvent> {
        val events = mutableListOf<InputEvent>()
        val anyHeld = pointers.values.any { it.holdStarted } || mode == PointerMode.MENU
        if (anyHeld) {
            events += InputEvent.MouseButton(Glfw.MOUSE_BUTTON_LEFT, Glfw.RELEASE)
        }
        pointers.clear()
        lookPointerId = null
        smoothedDx = 0f
        smoothedDy = 0f
        return events
    }

    private fun isTap(pointer: Pointer, timeMs: Long): Boolean =
        timeMs - pointer.startTimeMs <= config.tapMaxDurationMs &&
            pointer.maxDrift <= config.tapSlopPixels

    private fun smooth(dx: Float, dy: Float): Pair<Float, Float> {
        val factor = config.smoothing.coerceIn(0f, 0.95f)
        if (factor <= 0f) return dx to dy
        smoothedDx = smoothedDx * factor + dx * (1f - factor)
        smoothedDy = smoothedDy * factor + dy * (1f - factor)
        // Below a fraction of a pixel the smoothed tail is invisible motion
        // that would keep the camera creeping after the finger stopped.
        if (abs(smoothedDx) < 0.01f) smoothedDx = 0f
        if (abs(smoothedDy) < 0.01f) smoothedDy = 0f
        return smoothedDx to smoothedDy
    }
}
