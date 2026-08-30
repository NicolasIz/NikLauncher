package com.niklauncher.core.install

import com.niklauncher.core.download.DownloadRequest
import com.niklauncher.core.library.MavenCoordinate
import com.niklauncher.core.library.SkippedLibrary
import com.niklauncher.core.manifest.VersionJson
import com.niklauncher.core.runtime.JavaRuntime
import java.io.File

/**
 * Everything that must exist on disk before a version can start, worked out
 * before a single byte is fetched.
 *
 * Planning up front rather than downloading as we walk the manifest is what
 * makes an accurate progress bar and a resumable install possible, and it
 * surfaces an unsatisfiable version before the user waits on a transfer.
 */
data class InstallPlan(
    val versionId: String,
    val version: VersionJson,
    val javaRuntime: JavaRuntime,
    val client: DownloadRequest?,
    val libraries: List<DownloadRequest>,
    val assets: List<DownloadRequest>,
    /** Classpath in load order; loader classes precede vanilla ones. */
    val classpath: List<File>,
    val skippedLibraries: List<SkippedLibrary>,
    /** Libraries the native runtime pack supplies instead of us downloading. */
    val runtimeProvidedLibraries: List<MavenCoordinate>,
    val assetIndexId: String?,
    val requiresNamedAssetCopies: Boolean,
) {
    val requests: List<DownloadRequest>
        get() = buildList {
            client?.let { add(it) }
            addAll(libraries)
            addAll(assets)
        }

    val totalBytes: Long get() = requests.sumOf { it.size }

    val fileCount: Int get() = requests.size
}

/** Stage of an install, for progress reporting. */
enum class InstallStage {
    RESOLVING_METADATA,
    PLANNING,
    DOWNLOADING,
    FINALISING,
    COMPLETE,
}

data class InstallProgress(
    val stage: InstallStage,
    val completedFiles: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0,
    val totalBytes: Long = 0,
    val currentLabel: String? = null,
) {
    val fraction: Float
        get() = when {
            stage == InstallStage.COMPLETE -> 1f
            totalBytes > 0 -> (bytesTransferred.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalFiles > 0 -> (completedFiles.toFloat() / totalFiles).coerceIn(0f, 1f)
            else -> 0f
        }
}

sealed interface InstallResult {
    data class Success(val plan: InstallPlan) : InstallResult

    /**
     * Some files could not be fetched. The plan is kept so the caller can retry
     * only what failed rather than restarting the whole install.
     */
    data class Incomplete(
        val plan: InstallPlan,
        val failures: List<FailedFile>,
    ) : InstallResult

    data class Failed(val cause: Throwable) : InstallResult
}

data class FailedFile(val label: String, val url: String, val cause: Throwable)
