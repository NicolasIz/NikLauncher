package com.niklauncher.core.runtime

import kotlinx.serialization.Serializable

/**
 * A downloadable bundle carrying the parts NikLauncher cannot write in Kotlin:
 * an ARM64 JVM, the OpenGL translation layer and the GLFW bridge.
 *
 * Packs are described as data fetched from an index rather than compiled in, so
 * a new runtime can be published without shipping a new APK, and so the
 * launcher itself contains no third-party binaries.
 */
@Serializable
data class RuntimePack(
    val id: String,
    /** Which [JavaRuntime] this satisfies, by its id. */
    val runtimeId: String,
    val version: String,
    val abi: String = DEFAULT_ABI,
    val url: String,
    val sha1: String? = null,
    val size: Long = 0,
    /** Where `libjvm.so` sits inside the extracted pack. */
    val libjvmPath: String = "lib/server/libjvm.so",
    /**
     * Jars the pack supplies instead of us downloading them - the Android LWJGL
     * build above all, which replaces the desktop one the manifest lists.
     */
    val providedClasspath: List<String> = emptyList(),
    /** Graphics backends bundled with this pack, by [GraphicsBackend.id]. */
    val graphicsBackendIds: List<String> = emptyList(),
    /**
     * Where the pack keeps each backend's shared objects, relative to its root.
     *
     * Data rather than a constant for the same reason [libjvmPath] is: the
     * layout is part of what a published pack declares about itself, so a
     * future pack can move it without needing a new launcher.
     */
    val graphicsDirectory: String = DEFAULT_GRAPHICS_DIRECTORY,
    /**
     * Largest kernel page size the pack's shared objects are aligned for.
     *
     * A pack built for 4 KB pages will not map on a 16 KB-page kernel, so this
     * has to be checked before install rather than discovered as a crash.
     */
    val maxPageSizeBytes: Int = 4096,
) {
    val runtime: JavaRuntime? get() = JavaRuntime.fromId(runtimeId)

    val backends: List<GraphicsBackend>
        get() = graphicsBackendIds.mapNotNull { GraphicsBackend.fromId(it) }

    fun supportsPageSize(devicePageSizeBytes: Int): Boolean = maxPageSizeBytes >= devicePageSizeBytes

    /**
     * The directory holding [backend]'s shared objects, relative to the pack
     * root.
     *
     * One directory per backend, and each carries its own copy of the LWJGL
     * natives, because `org.lwjgl.librarypath` is a single directory and is
     * also where LWJGL looks for its own libraries - so a backend's directory
     * has to be self-contained. The duplication is a few hundred kilobytes
     * against a runtime of a couple of hundred megabytes.
     */
    fun libraryDirectoryFor(backend: GraphicsBackend): String =
        graphicsDirectory.trimEnd('/') + "/" + backend.id

    companion object {
        const val DEFAULT_ABI = "arm64-v8a"
        const val DEFAULT_GRAPHICS_DIRECTORY = "nikgraphics"
    }
}

/** The published list of packs. */
@Serializable
data class RuntimePackIndex(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val packs: List<RuntimePack> = emptyList(),
) {
    /**
     * The best pack for a runtime on this device: the newest one whose ABI and
     * page-size alignment actually fit.
     */
    fun bestFor(
        runtime: JavaRuntime,
        abi: String = RuntimePack.DEFAULT_ABI,
        devicePageSizeBytes: Int = 4096,
    ): RuntimePack? = packs
        .filter { it.runtimeId == runtime.id }
        .filter { it.abi == abi }
        .filter { it.supportsPageSize(devicePageSizeBytes) }
        .maxWithOrNull { a, b ->
            com.niklauncher.core.library.VersionComparator.compare(a.version, b.version)
        }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Why a pack could not be installed. */
sealed class RuntimePackException(message: String) : Exception(message) {

    class NoCompatiblePack(val runtime: JavaRuntime) :
        RuntimePackException("No runtime pack available for " + runtime.displayName)

    class PageSizeMismatch(val pack: RuntimePack, val devicePageSizeBytes: Int) :
        RuntimePackException(
            "Pack " + pack.id + " is aligned for " + pack.maxPageSizeBytes +
                " byte pages but this device uses " + devicePageSizeBytes,
        )

    class DownloadFailed(val pack: RuntimePack, cause: String) :
        RuntimePackException("Could not download " + pack.id + ": " + cause)

    class Invalid(val pack: RuntimePack, val detail: String) :
        RuntimePackException("Pack " + pack.id + " is not usable: " + detail)
}
