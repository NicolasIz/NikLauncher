package com.niklauncher.core.manifest

import com.niklauncher.core.rules.Rule
import kotlinx.serialization.Serializable

/**
 * A single version descriptor (`versions/<id>/<id>.json`).
 *
 * Both argument encodings are modelled: [minecraftArguments] is the flat string
 * used up to 1.12, [arguments] the rule-aware structure used from 1.13 on. Mod
 * loaders additionally set [inheritsFrom] to layer their own descriptor on top
 * of a vanilla one.
 */
@Serializable
data class VersionJson(
    val id: String,
    val type: VersionType = VersionType.UNKNOWN,
    val mainClass: String? = null,
    val inheritsFrom: String? = null,
    val jar: String? = null,
    val assets: String? = null,
    val minecraftArguments: String? = null,
    val arguments: Arguments? = null,
    val libraries: List<Library> = emptyList(),
    val assetIndex: AssetIndexReference? = null,
    val downloads: VersionDownloads? = null,
    val javaVersion: JavaVersionReference? = null,
    val complianceLevel: Int = 0,
    val releaseTime: String? = null,
    val time: String? = null,
)

@Serializable
data class Arguments(
    val game: List<Argument> = emptyList(),
    val jvm: List<Argument> = emptyList(),
)

@Serializable
data class VersionDownloads(
    val client: DownloadEntry? = null,
    val server: DownloadEntry? = null,
    @kotlinx.serialization.SerialName("client_mappings")
    val clientMappings: DownloadEntry? = null,
)

@Serializable
data class DownloadEntry(
    val url: String,
    val sha1: String? = null,
    val size: Long = 0,
    val path: String? = null,
    val id: String? = null,
    val totalSize: Long = 0,
)

@Serializable
data class AssetIndexReference(
    val id: String,
    val url: String,
    val sha1: String? = null,
    val size: Long = 0,
    val totalSize: Long = 0,
)

/** Declares which major Java release a version needs (8, 17, 21, ...). */
@Serializable
data class JavaVersionReference(
    val component: String = "jre-legacy",
    val majorVersion: Int = 8,
)

@Serializable
data class Library(
    val name: String,
    val downloads: LibraryDownloads? = null,
    val rules: List<Rule> = emptyList(),
    val natives: Map<String, String> = emptyMap(),
    val extract: ExtractRule? = null,
    /** Alternate Maven root, used by Fabric/Forge descriptors. */
    val url: String? = null,
)

@Serializable
data class LibraryDownloads(
    val artifact: DownloadEntry? = null,
    val classifiers: Map<String, DownloadEntry> = emptyMap(),
)

@Serializable
data class ExtractRule(
    val exclude: List<String> = emptyList(),
)
