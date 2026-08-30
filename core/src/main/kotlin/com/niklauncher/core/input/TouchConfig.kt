package com.niklauncher.core.input

import kotlinx.serialization.Serializable

/**
 * How touch input feels.
 *
 * [smoothing] defaults to zero on purpose. Averaging pointer deltas hides
 * jitter but does it by delaying the response, and this project's priority is
 * a game that feels direct rather than one that looks smooth in a graph. It is
 * left configurable for players who prefer the trade the other way.
 */
@Serializable
data class TouchConfig(
    val lookSensitivity: Float = 1.0f,
    /** A press shorter than this, that barely moved, counts as a tap. */
    val tapMaxDurationMs: Long = 200,
    /** How far a finger may drift and still be a tap rather than a look. */
    val tapSlopPixels: Float = 24f,
    /** A stationary press held this long starts holding the attack button. */
    val longPressMs: Long = 350,
    val invertLook: Boolean = false,
    /** 0 = none, approaching 1 = heavy. Every increment adds latency. */
    val smoothing: Float = 0f,
) {
    companion object {
        val DEFAULT = TouchConfig()
    }
}

/**
 * Whether the game has the cursor grabbed (playing) or released (in a menu).
 * The same finger means completely different things in the two states.
 */
enum class PointerMode {
    /** In-world: dragging looks around, taps attack. */
    GRABBED,

    /** In a menu or inventory: the finger is a mouse pointer. */
    MENU,
}
