package com.niklauncher.core.instance

import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.PerformanceProfile
import kotlinx.serialization.Serializable

/** Which mod loader an instance runs. */
@Serializable
enum class ModLoader(val id: String, val displayName: String) {
    VANILLA("vanilla", "Vanilla"),
    FABRIC("fabric", "Fabric"),
    FORGE("forge", "Forge"),
    NEOFORGE("neoforge", "NeoForge");

    companion object {
        fun fromId(id: String): ModLoader = entries.firstOrNull { it.id == id } ?: VANILLA
    }
}

/**
 * One playable installation.
 *
 * Every graphics and performance setting lives here rather than globally,
 * because the right answer genuinely differs per instance: a 1.8 PvP install
 * wants GL4ES at a high frame cap, while a 1.21 modpack wants Zink and a
 * conservative thermal profile.
 */
@Serializable
data class Instance(
    val id: String,
    val name: String,
    val minecraftVersion: String,
    val loader: ModLoader = ModLoader.VANILLA,
    val loaderVersion: String? = null,
    /** Resolved version id on disk, which differs from [minecraftVersion] for modded installs. */
    val resolvedVersionId: String = minecraftVersion,
    val memoryMegabytes: Int = DEFAULT_MEMORY_MB,
    val graphicsBackendId: String? = null,
    val performanceProfileId: String = PerformanceProfile.BALANCED.id,
    val javaRuntimeId: String? = null,
    val extraJvmArguments: List<String> = emptyList(),
    val createdAtEpochMillis: Long = 0,
    val lastPlayedEpochMillis: Long = 0,
) {
    val graphicsBackend: GraphicsBackend?
        get() = graphicsBackendId?.let { GraphicsBackend.fromId(it) }

    val performanceProfile: PerformanceProfile
        get() = PerformanceProfile.fromId(performanceProfileId) ?: PerformanceProfile.BALANCED

    companion object {
        /**
         * A conservative default. Minecraft runs in the launcher's own process,
         * so the heap competes with the app and with Android's own limits;
         * over-allocating gets the process killed rather than making it faster.
         */
        const val DEFAULT_MEMORY_MB = 2048
        const val MIN_MEMORY_MB = 512
        const val MAX_MEMORY_MB = 8192
    }
}
