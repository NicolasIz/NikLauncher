package com.niklauncher.core.runtime

import com.niklauncher.core.download.DownloadOutcome
import com.niklauncher.core.download.DownloadProgress
import com.niklauncher.core.download.DownloadRequest
import com.niklauncher.core.download.Downloader
import com.niklauncher.core.io.GamePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The real [NativeRuntimeProvider]: downloads, verifies and unpacks runtime
 * packs, and reports what is installed.
 *
 * An install is only recorded once the pack is on disk *and* its `libjvm.so`
 * has been found, and the marker is written last. A half-extracted pack
 * therefore reads as not installed and is retried, rather than being loaded and
 * crashing the game process on launch.
 */
class RuntimePackInstaller(
    private val paths: GamePaths,
    private val downloader: Downloader,
    /** Supplies the published pack index; injected so it can be faked in tests. */
    private val indexProvider: suspend () -> RuntimePackIndex,
    private val devicePageSizeBytes: Int = 4096,
    private val deviceAbi: String = RuntimePack.DEFAULT_ABI,
) : NativeRuntimeProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    override suspend fun installedRuntimes(): List<InstalledRuntime> = withContext(Dispatchers.IO) {
        JavaRuntime.entries.mapNotNull { runtime -> readInstalled(runtime) }
    }

    override suspend fun availableBackends(): List<GraphicsBackend> = withContext(Dispatchers.IO) {
        JavaRuntime.entries
            .mapNotNull { readManifest(it) }
            .flatMap { it.backends }
            .distinct()
    }

    override suspend fun ensureInstalled(
        runtime: JavaRuntime,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): InstalledRuntime {
        readInstalled(runtime)?.let { return it }

        val index = indexProvider()
        val pack = index.bestFor(runtime, deviceAbi, devicePageSizeBytes)
            ?: throw pickFailureReason(index, runtime)

        return withContext(Dispatchers.IO) { install(pack, runtime, onProgress) }
    }

    override suspend fun remove(runtime: JavaRuntime) = withContext(Dispatchers.IO) {
        paths.runtime(runtime.id).deleteRecursively()
        Unit
    }

    private suspend fun install(
        pack: RuntimePack,
        runtime: JavaRuntime,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): InstalledRuntime {
        val target = paths.runtime(runtime.id)
        // Always start from a clean directory: extracting over a previous,
        // possibly partial install is how mismatched files end up mixed.
        target.deleteRecursively()
        target.mkdirs()

        val archive = File(paths.cache, pack.id + archiveSuffix(pack.url))
        archive.parentFile?.mkdirs()

        val outcome = downloader.downloadAll(
            listOf(
                DownloadRequest(
                    url = pack.url,
                    destination = archive,
                    sha1 = pack.sha1,
                    size = pack.size,
                    label = pack.id,
                ),
            ),
            onProgress,
        ).single()

        if (outcome is DownloadOutcome.Failed) {
            throw RuntimePackException.DownloadFailed(pack, outcome.cause.message ?: "unknown error")
        }

        try {
            ArchiveExtractor.extract(archive, target)
        } catch (error: Exception) {
            target.deleteRecursively()
            throw RuntimePackException.Invalid(pack, error.message ?: "extraction failed")
        } finally {
            // The archive is large and re-downloadable; keeping it would double
            // the storage cost of every runtime.
            archive.delete()
        }

        val libjvm = File(target, pack.libjvmPath)
        if (!libjvm.isFile) {
            target.deleteRecursively()
            throw RuntimePackException.Invalid(pack, "libjvm.so not found at " + pack.libjvmPath)
        }

        File(target, MANIFEST_FILE).writeText(json.encodeToString(RuntimePack.serializer(), pack))

        return toInstalledRuntime(runtime, target, pack, libjvm)
    }

    private fun readInstalled(runtime: JavaRuntime): InstalledRuntime? {
        val pack = readManifest(runtime) ?: return null
        val home = paths.runtime(runtime.id)
        val libjvm = File(home, pack.libjvmPath)
        if (!libjvm.isFile) return null
        return toInstalledRuntime(runtime, home, pack, libjvm)
    }

    private fun readManifest(runtime: JavaRuntime): RuntimePack? {
        val manifest = File(paths.runtime(runtime.id), MANIFEST_FILE)
        if (!manifest.isFile) return null
        return runCatching { json.decodeFromString(RuntimePack.serializer(), manifest.readText()) }
            .getOrNull()
    }

    private fun toInstalledRuntime(
        runtime: JavaRuntime,
        home: File,
        pack: RuntimePack,
        libjvm: File,
    ) = InstalledRuntime(
        runtime = runtime,
        home = home,
        libjvm = libjvm,
        version = pack.version,
        architecture = pack.abi,
        providedClasspath = pack.providedClasspath.map { File(home, it) },
    )

    /**
     * Distinguishes "nothing published for this runtime" from "published, but
     * not for this device", so the user is told which of the two happened.
     */
    private fun pickFailureReason(index: RuntimePackIndex, runtime: JavaRuntime): RuntimePackException {
        val forRuntime = index.packs.filter { it.runtimeId == runtime.id && it.abi == deviceAbi }
        val blockedByPageSize = forRuntime.firstOrNull { !it.supportsPageSize(devicePageSizeBytes) }
        return if (forRuntime.isNotEmpty() && blockedByPageSize != null) {
            RuntimePackException.PageSizeMismatch(blockedByPageSize, devicePageSizeBytes)
        } else {
            RuntimePackException.NoCompatiblePack(runtime)
        }
    }

    private fun archiveSuffix(url: String): String = when {
        url.endsWith(".tar.gz", ignoreCase = true) -> ".tar.gz"
        url.endsWith(".tgz", ignoreCase = true) -> ".tgz"
        else -> ".zip"
    }

    private companion object {
        /** Written last, so its presence means the install completed. */
        const val MANIFEST_FILE = ".nikpack.json"
    }
}
