package com.niklauncher.core.library

/** Where a classpath entry comes from once resolution has run. */
enum class LibrarySource {
    /** Fetched from a Maven repository into `libraries/`. */
    DOWNLOAD,

    /** Shipped by the native runtime pack; never downloaded. */
    RUNTIME_PROVIDED,
}

data class ResolvedLibrary(
    val coordinate: MavenCoordinate,
    /** Path relative to the `libraries/` root. */
    val path: String,
    val url: String?,
    val sha1: String?,
    val size: Long,
    val source: LibrarySource,
)

/** Why a declared library did not make it onto the classpath. */
enum class SkipReason {
    /** The manifest's own rules excluded it for this environment. */
    RULES,

    /** A desktop native bundle (`natives-linux`, `natives-windows`, ...). */
    DESKTOP_NATIVE,

    /** Superseded by a newer version of the same module. */
    OUTDATED_DUPLICATE,

    /** The coordinate could not be parsed. */
    MALFORMED,
}

data class SkippedLibrary(
    val name: String,
    val reason: SkipReason,
    val detail: String? = null,
)

/**
 * Outcome of resolving a version's `libraries` block.
 *
 * [skipped] is kept rather than discarded: when a version refuses to launch on
 * a device, the first question is always which libraries were dropped and why.
 */
data class LibraryResolution(
    val classpath: List<ResolvedLibrary>,
    val runtimeProvided: List<MavenCoordinate>,
    val skipped: List<SkippedLibrary>,
)
