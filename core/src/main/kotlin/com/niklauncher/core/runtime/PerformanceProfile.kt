package com.niklauncher.core.runtime

/**
 * Session-level performance posture.
 *
 * NikLauncher's stated priority order is stability, then temperature, then
 * power, then performance, then peak frame rate - so even [PERFORMANCE] keeps a
 * frame cap and thermal backoff enabled. Uncapped rendering on a phone buys a
 * few minutes of high frame rate and then throttles for the rest of the
 * session.
 */
enum class PerformanceProfile(val id: String, val displayName: String) {
    /** Coolest and longest-lasting; the right default for long sessions. */
    THERMAL("thermal", "Temperatura"),

    BALANCED("balanced", "Equilibrado"),

    /** Highest sustained output that still respects thermal headroom. */
    PERFORMANCE("performance", "Rendimiento");

    fun defaultTuning(): PerformanceTuning = when (this) {
        THERMAL -> PerformanceTuning(
            profile = this,
            targetFps = 30,
            maxRenderDistance = 6,
            thermalHeadroomFloor = 0.65f,
            allowThermalDownscale = true,
        )
        BALANCED -> PerformanceTuning(
            profile = this,
            targetFps = 60,
            maxRenderDistance = 8,
            thermalHeadroomFloor = 0.80f,
            allowThermalDownscale = true,
        )
        PERFORMANCE -> PerformanceTuning(
            profile = this,
            targetFps = 90,
            maxRenderDistance = 12,
            thermalHeadroomFloor = 0.90f,
            allowThermalDownscale = true,
        )
    }

    companion object {
        fun fromId(id: String): PerformanceProfile? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Concrete knobs derived from a profile. [thermalHeadroomFloor] is the
 * `getThermalHeadroom()` reading at which Phase 4 starts reducing load; 1.0
 * means the device is at its sustainable limit.
 */
data class PerformanceTuning(
    val profile: PerformanceProfile,
    val targetFps: Int,
    val maxRenderDistance: Int,
    val thermalHeadroomFloor: Float,
    val allowThermalDownscale: Boolean,
)
