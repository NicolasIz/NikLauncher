package com.niklauncher.core.download

import java.io.File

/**
 * One file to fetch. [sha1] and [size], when known, are verified after the
 * transfer and are also what lets an already-present file be skipped entirely.
 */
data class DownloadRequest(
    val url: String,
    val destination: File,
    val sha1: String? = null,
    val size: Long = 0,
    /** Shown in progress reporting. */
    val label: String = destination.name,
    /** Mirrors tried, in order, if [url] fails. */
    val mirrors: List<String> = emptyList(),
) {
    val candidateUrls: List<String> get() = listOf(url) + mirrors
}

sealed interface DownloadOutcome {
    val request: DownloadRequest

    /** The file was already present and verified; nothing was transferred. */
    data class Skipped(override val request: DownloadRequest) : DownloadOutcome

    data class Completed(
        override val request: DownloadRequest,
        val bytesTransferred: Long,
        val resumed: Boolean,
    ) : DownloadOutcome

    data class Failed(
        override val request: DownloadRequest,
        val cause: Throwable,
        val attempts: Int,
    ) : DownloadOutcome
}

/** Aggregate progress across a batch, suitable for driving a UI. */
data class DownloadProgress(
    val completedFiles: Int,
    val totalFiles: Int,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val currentLabel: String?,
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (bytesTransferred.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
            totalFiles > 0 -> (completedFiles.toFloat() / totalFiles).coerceIn(0f, 1f)
            else -> 0f
        }
}
