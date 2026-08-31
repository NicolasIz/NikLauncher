package com.niklauncher.core.settings

import com.niklauncher.core.runtime.PerformanceProfile
import kotlinx.serialization.Serializable

/** Device-wide preferences, distinct from the per-instance settings. */
@Serializable
data class LauncherSettings(
    /**
     * The name an offline session plays under. Until Microsoft sign-in lands
     * this is the only identity there is, and Minecraft derives the player's
     * UUID from it, so changing it changes whose world you walk into.
     */
    val playerName: String = DEFAULT_PLAYER_NAME,
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
    /** Which control layout the game opens with. */
    val activeControlLayoutId: String = "default",
    /**
     * Where the runtime pack index is published.
     *
     * Still a setting rather than a constant, because which packs NikLauncher
     * offers is a licensing and provenance decision - but it now has a default,
     * since there is somewhere real to point it. The packs behind it are built
     * from the upstream sources each component.json names, and contain nothing
     * of Minecraft's.
     */
    val runtimePackIndexUrl: String = DEFAULT_PACK_INDEX_URL,
) {
    val defaultPerformanceProfile: PerformanceProfile
        get() = PerformanceProfile.fromId(defaultPerformanceProfileId) ?: PerformanceProfile.BALANCED

    companion object {
        const val DEFAULT_PLAYER_NAME = "Player"

        const val DEFAULT_PACK_INDEX_URL =
            "https://github.com/NicolasIz/NikLauncher/releases/download/runtime-packs/runtime-packs.json"

        val DEFAULT = LauncherSettings()
    }
}
