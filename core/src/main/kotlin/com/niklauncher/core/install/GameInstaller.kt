package com.niklauncher.core.install

import com.niklauncher.core.assets.AssetIndex
import com.niklauncher.core.download.DownloadOutcome
import com.niklauncher.core.download.Downloader
import com.niklauncher.core.io.GamePaths
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Drives a version install end to end: resolve metadata, plan, download,
 * then build any legacy asset tree the version needs.
 *
 * A partial install is reported as [InstallResult.Incomplete] rather than as a
 * thrown error, because the common failure on a phone is a handful of files
 * lost to a flaky connection - and re-running the install then only re-fetches
 * those, since verified files are skipped.
 */
class GameInstaller(
    private val catalog: VersionCatalog,
    private val downloader: Downloader,
    private val paths: GamePaths,
    private val planner: InstallPlanner = InstallPlanner(paths),
) {

    suspend fun install(
        versionId: String,
        gameDirectory: File = paths.root,
        onProgress: (InstallProgress) -> Unit = {},
    ): InstallResult {
        return try {
            onProgress(InstallProgress(InstallStage.RESOLVING_METADATA))
            val version = catalog.resolvedVersion(versionId)
            val assetIndex: AssetIndex? = version.assetIndex?.let { catalog.assetIndex(it) }

            onProgress(InstallProgress(InstallStage.PLANNING))
            val plan = planner.plan(version, assetIndex)

            paths.createDirectories()

            onProgress(
                InstallProgress(
                    stage = InstallStage.DOWNLOADING,
                    totalFiles = plan.fileCount,
                    totalBytes = plan.totalBytes,
                ),
            )

            val outcomes = downloader.downloadAll(plan.requests) { progress ->
                onProgress(
                    InstallProgress(
                        stage = InstallStage.DOWNLOADING,
                        completedFiles = progress.completedFiles,
                        totalFiles = progress.totalFiles,
                        bytesTransferred = progress.bytesTransferred,
                        totalBytes = progress.totalBytes,
                        currentLabel = progress.currentLabel,
                    ),
                )
            }

            val failures = outcomes.filterIsInstance<DownloadOutcome.Failed>().map {
                FailedFile(it.request.label, it.request.url, it.cause)
            }

            if (failures.isEmpty() && assetIndex != null && plan.assetIndexId != null) {
                onProgress(InstallProgress(InstallStage.FINALISING, totalBytes = plan.totalBytes, bytesTransferred = plan.totalBytes))
                AssetLayout.materialise(assetIndex, plan.assetIndexId, paths, gameDirectory)
            }

            onProgress(
                InstallProgress(
                    stage = InstallStage.COMPLETE,
                    completedFiles = plan.fileCount,
                    totalFiles = plan.fileCount,
                    bytesTransferred = plan.totalBytes,
                    totalBytes = plan.totalBytes,
                ),
            )

            if (failures.isEmpty()) InstallResult.Success(plan) else InstallResult.Incomplete(plan, failures)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            InstallResult.Failed(error)
        }
    }

    /** True when every file a version needs is present and verified. */
    suspend fun isInstalled(versionId: String): Boolean = runCatching {
        val version = catalog.resolvedVersion(versionId)
        val assetIndex = version.assetIndex?.let { catalog.assetIndex(it) }
        val plan = planner.plan(version, assetIndex)
        plan.requests.all { request ->
            com.niklauncher.core.util.Hashing.verify(request.destination, request.sha1, request.size)
        }
    }.getOrDefault(false)
}
