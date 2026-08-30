package com.niklauncher.core.manifest

import kotlinx.serialization.Serializable

/** The root index served by Mojang's piston-meta `version_manifest_v2.json`. */
@Serializable
data class VersionManifest(
    val latest: LatestVersions = LatestVersions(),
    val versions: List<VersionSummary> = emptyList(),
) {
    fun find(id: String): VersionSummary? = versions.firstOrNull { it.id == id }

    fun releases(): List<VersionSummary> = versions.filter { it.type == VersionType.RELEASE }
}

@Serializable
data class LatestVersions(
    val release: String = "",
    val snapshot: String = "",
)

@Serializable
data class VersionSummary(
    val id: String,
    val type: VersionType = VersionType.UNKNOWN,
    val url: String = "",
    val time: String? = null,
    val releaseTime: String? = null,
    val sha1: String? = null,
    val complianceLevel: Int = 0,
)
