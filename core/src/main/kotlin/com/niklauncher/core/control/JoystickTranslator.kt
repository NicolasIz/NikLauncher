package com.niklauncher.core.control

import com.niklauncher.core.input.Glfw
import com.niklauncher.core.input.InputEvent
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Converts a joystick's deflection into movement key presses.
 *
 * Minecraft's movement is digital - it only knows whether W is down - so an
 * analogue stick has to be quantised. The translator is edge-triggered: it
 * emits a press only when a direction becomes active and a release only when it
 * stops, because re-sending a press every frame floods the game's input queue
 * and is a real source of stutter.
 */
class JoystickTranslator(private var joystick: ControlJoystick) {

    private val pressed = mutableSetOf<Int>()

    fun updateConfig(joystick: ControlJoystick) {
        this.joystick = joystick
    }

    /**
     * @param dx deflection on the horizontal axis, -1..1
     * @param dy deflection on the vertical axis, -1..1, positive downward
     */
    fun update(dx: Float, dy: Float): List<InputEvent> {
        val magnitude = hypot(dx, dy).coerceAtMost(1f)
        val threshold = joystick.activationThreshold

        val wanted = mutableSetOf<Int>()
        if (magnitude >= threshold) {
            if (dy < -threshold) wanted += joystick.upKey
            if (dy > threshold) wanted += joystick.downKey
            if (dx < -threshold) wanted += joystick.leftKey
            if (dx > threshold) wanted += joystick.rightKey
            // Sprint only makes sense while actually going forward.
            if (magnitude >= joystick.sprintThreshold && dy < -threshold) {
                wanted += joystick.sprintKey
            }
        }

        return diff(wanted)
    }

    /** Releases every key this joystick holds, for when the finger lifts. */
    fun release(): List<InputEvent> = diff(emptySet())

    private fun diff(wanted: Set<Int>): List<InputEvent> {
        val events = mutableListOf<InputEvent>()
        for (key in pressed - wanted) {
            events += InputEvent.Key(key, Glfw.RELEASE)
        }
        for (key in wanted - pressed) {
            events += InputEvent.Key(key, Glfw.PRESS)
        }
        pressed.clear()
        pressed += wanted
        return events
    }

    /** Deflection from a touch offset within the pad, normalised to -1..1. */
    companion object {
        fun deflection(offsetX: Float, offsetY: Float, radiusPx: Float): Pair<Float, Float> {
            if (radiusPx <= 0f) return 0f to 0f
            val dx = (offsetX / radiusPx).coerceIn(-1f, 1f)
            val dy = (offsetY / radiusPx).coerceIn(-1f, 1f)
            // Guard against a divide-by-zero when the finger is dead centre.
            val magnitude = hypot(dx, dy)
            return if (magnitude <= 1f || magnitude == 0f) {
                dx to dy
            } else {
                (dx / magnitude) to (dy / magnitude)
            }
        }

        fun isCentred(dx: Float, dy: Float, deadZone: Float): Boolean =
            abs(dx) < deadZone && abs(dy) < deadZone
    }
}
