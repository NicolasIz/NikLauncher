package com.niklauncher.core.settings

import com.niklauncher.core.runtime.PerformanceProfile
import kotlinx.serialization.Serializable

/** Device-wide preferences, distinct from the per-instance settings. */
@Serializable
data class LauncherSettings(
    val defaultMemoryMegabytes: Int = 2048,
    val defaultPerformanceProfileId: String = PerformanceProfile.BALANCED.id,
    /** Keep the screen awake while the game runs. */
    val keepScreenOn: Boolean = true,
    /** Show snapshots and old betas in the version picker. */
    val showNonReleaseVersions: Boolean = false,
    /** Write a diagnostic log per session; Phase 4 turns these into crash reports. */
    val verboseLogging: Boolean = false,
    /** Cap on parallel downloads; higher values heat the device for little gain. */
    val downloadConcurrency: Int = 6,
    val lastPlayedInstanceId: String? = null,
) {
    val defaultPerformanceProfile: PerformanceProfile
        get() = PerformanceProfile.fromId(defaultPerformanceProfileId) ?: PerformanceProfile.BALANCED

    companion object {
        val DEFAULT = LauncherSettings()
    }
}
