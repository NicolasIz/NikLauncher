package com.niklauncher.core.runtime

import com.niklauncher.core.install.MetadataClient
import com.niklauncher.core.io.GamePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Fetches the published list of runtime packs.
 *
 * The source URL is configuration rather than a constant, because which packs
 * NikLauncher offers is a decision about licensing and provenance, not a
 * detail to bury in code. With no source configured the catalogue is empty and
 * the launcher says so, instead of silently having nothing to install.
 */
class RuntimePackCatalog(
    private val metadata: MetadataClient,
    private val paths: GamePaths,
    private val indexUrlProvider: suspend () -> String?,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val cacheFile: File get() = File(paths.cache, "runtime-packs.json")

    /**
     * The pack index, refreshed when possible and served from cache otherwise
     * so an installed device stays usable offline.
     */
    suspend fun index(): RuntimePackIndex = withContext(Dispatchers.IO) {
        val url = indexUrlProvider()?.takeIf { it.isNotBlank() }
            ?: return@withContext readCache() ?: RuntimePackIndex()

        try {
            val text = metadata.fetchText(url)
            val parsed = json.decodeFromString(RuntimePackIndex.serializer(), text)
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(text)
            parsed
        } catch (error: Exception) {
            readCache() ?: throw error
        }
    }

    /** True when a source is configured at all. */
    suspend fun hasSource(): Boolean = !indexUrlProvider().isNullOrBlank()

    private fun readCache(): RuntimePackIndex? {
        if (!cacheFile.isFile) return null
        return runCatching { json.decodeFromString(RuntimePackIndex.serializer(), cacheFile.readText()) }
            .getOrNull()
    }
}
