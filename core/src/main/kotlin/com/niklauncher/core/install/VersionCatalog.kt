package com.niklauncher.core.install

import com.niklauncher.core.NikLauncher
import com.niklauncher.core.assets.AssetIndex
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.manifest.AssetIndexReference
import com.niklauncher.core.manifest.ManifestCodec
import com.niklauncher.core.manifest.VersionJson
import com.niklauncher.core.manifest.VersionManifest
import com.niklauncher.core.manifest.VersionResolver
import com.niklauncher.core.util.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Fetches and caches Minecraft version metadata.
 *
 * Everything is cached on disk, because the launcher has to stay usable on a
 * phone with no connection: once a version is installed, re-opening its
 * instance must not require a round trip to Mojang.
 */
class VersionCatalog(
    private val metadata: MetadataClient,
    private val paths: GamePaths,
    private val manifestUrl: String = NikLauncher.VERSION_MANIFEST_URL,
    /** How long a cached manifest is served before a refresh is attempted. */
    private val manifestTtlMillis: Long = 6 * 60 * 60 * 1000L,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val manifestCache: File get() = File(paths.cache, "version_manifest_v2.json")

    /**
     * The version index. A cached copy inside its TTL is used as-is; otherwise
     * a refresh is attempted and, if that fails, the stale copy is served
     * rather than leaving the user with nothing.
     */
    suspend fun manifest(forceRefresh: Boolean = false): VersionManifest = withContext(Dispatchers.IO) {
        val cached = manifestCache.takeIf { it.isFile }
        val fresh = cached != null && now() - cached.lastModified() < manifestTtlMillis

        if (!forceRefresh && fresh) {
            runCatching { ManifestCodec.decodeManifest(cached!!.readText()) }
                .getOrNull()
                ?.let { return@withContext it }
        }

        try {
            val text = metadata.fetchText(manifestUrl)
            val parsed = ManifestCodec.decodeManifest(text)
            manifestCache.parentFile?.mkdirs()
            manifestCache.writeText(text)
            parsed
        } catch (error: Exception) {
            cached
                ?.let { runCatching { ManifestCodec.decodeManifest(it.readText()) }.getOrNull() }
                ?: throw error
        }
    }

    /**
     * The descriptor for [versionId] with its `inheritsFrom` chain already
     * flattened, so callers never have to think about mod loaders.
     */
    suspend fun resolvedVersion(versionId: String): VersionJson =
        VersionResolver.resolve(versionId) { id -> rawVersion(id) }

    /**
     * A single descriptor, from disk when present and from the network
     * otherwise. Locally installed descriptors take priority: that is how a
     * mod loader's own version, which the Mojang manifest never lists, is found.
     */
    suspend fun rawVersion(versionId: String): VersionJson = withContext(Dispatchers.IO) {
        val local = paths.versionJson(versionId)
        if (local.isFile) {
            runCatching { ManifestCodec.decodeVersion(local.readText()) }
                .getOrNull()
                ?.let { return@withContext it }
        }

        val summary = manifest().find(versionId)
            ?: throw IOException("Unknown Minecraft version '$versionId'")

        val text = metadata.fetchText(summary.url)
        summary.sha1?.let { expected ->
            val actual = Hashing.sha1(text.toByteArray(Charsets.UTF_8))
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IOException("Checksum mismatch for version $versionId metadata")
            }
        }

        local.parentFile?.mkdirs()
        local.writeText(text)
        ManifestCodec.decodeVersion(text)
    }

    /** The asset index a version refers to, cached and hash-checked. */
    suspend fun assetIndex(reference: AssetIndexReference): AssetIndex = withContext(Dispatchers.IO) {
        val cached = paths.assetIndex(reference.id)
        if (Hashing.verify(cached, reference.sha1, reference.size)) {
            runCatching { ManifestCodec.json.decodeFromString(AssetIndex.serializer(), cached.readText()) }
                .getOrNull()
                ?.let { return@withContext it }
        }

        val text = metadata.fetchText(reference.url)
        reference.sha1?.let { expected ->
            val actual = Hashing.sha1(text.toByteArray(Charsets.UTF_8))
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IOException("Checksum mismatch for asset index ${reference.id}")
            }
        }

        cached.parentFile?.mkdirs()
        cached.writeText(text)
        ManifestCodec.json.decodeFromString(AssetIndex.serializer(), text)
    }

    /**
     * Version ids already present on disk, usable with no connection.
     *
     * Suspending because it walks the versions directory: on a phone with many
     * installs that is enough disk work to drop frames if it ran on the main
     * thread.
     */
    suspend fun installedVersionIds(): List<String> = withContext(Dispatchers.IO) {
        paths.versions.listFiles { file: File -> file.isDirectory }
            ?.filter { paths.versionJson(it.name).isFile }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }
}
