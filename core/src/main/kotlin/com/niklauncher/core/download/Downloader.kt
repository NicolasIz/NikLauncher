package com.niklauncher.core.download

import com.niklauncher.core.util.Hashing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Fetches files with verification, resume and bounded concurrency.
 *
 * Design notes that matter on a phone:
 *  - Every transfer lands in a `.part` file and is only moved into place after
 *    its hash checks out, so an interrupted run can never leave a corrupt file
 *    that later looks valid.
 *  - A partial `.part` is resumed via a range request rather than restarted,
 *    which matters when a 300 MB asset download drops on mobile data.
 *  - Concurrency is capped low by default. Saturating the link with dozens of
 *    parallel sockets measurably heats the device, and the whole project's
 *    priority order puts temperature above raw speed.
 */
class Downloader(
    private val transport: HttpTransport,
    private val maxConcurrency: Int = DEFAULT_CONCURRENCY,
    private val maxAttempts: Int = 4,
    private val initialBackoffMillis: Long = 500,
    private val bufferSize: Int = 128 * 1024,
) {

    /** Downloads one file, retrying and falling back to mirrors as needed. */
    suspend fun download(
        request: DownloadRequest,
        onBytes: ((Long) -> Unit)? = null,
    ): DownloadOutcome {
        if (Hashing.verify(request.destination, request.sha1, request.size)) {
            return DownloadOutcome.Skipped(request)
        }

        var attempt = 0
        var lastError: Throwable? = null

        for (url in request.candidateUrls) {
            while (attempt < maxAttempts) {
                attempt++
                try {
                    val result = withContext(Dispatchers.IO) { transfer(url, request, onBytes) }
                    return DownloadOutcome.Completed(request, result.bytes, result.resumed)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    lastError = error
                    if (error is HttpStatusException && !error.isRetryable) break
                    if (attempt < maxAttempts) {
                        delay(initialBackoffMillis shl (attempt - 1))
                    }
                }
            }
            attempt = 0
        }

        return DownloadOutcome.Failed(
            request,
            lastError ?: IOException("No candidate URL for ${request.label}"),
            maxAttempts,
        )
    }

    /**
     * Downloads a batch. Progress is reported as files complete and as bytes
     * arrive; the batch does not abort on the first failure, so the caller gets
     * a full picture of what went wrong instead of one error at a time.
     */
    suspend fun downloadAll(
        requests: List<DownloadRequest>,
        onProgress: ((DownloadProgress) -> Unit)? = null,
    ): List<DownloadOutcome> = coroutineScope {
        if (requests.isEmpty()) return@coroutineScope emptyList()

        val gate = Semaphore(maxConcurrency)
        val totalBytes = requests.sumOf { it.size }
        val transferred = AtomicLong(0)
        val completed = AtomicInteger(0)

        fun report(label: String?) {
            onProgress?.invoke(
                DownloadProgress(
                    completedFiles = completed.get(),
                    totalFiles = requests.size,
                    bytesTransferred = transferred.get(),
                    totalBytes = totalBytes,
                    currentLabel = label,
                ),
            )
        }

        report(null)

        val results = requests.map { request ->
            async {
                gate.withPermit {
                    val outcome = download(request) { delta ->
                        transferred.addAndGet(delta)
                        report(request.label)
                    }
                    completed.incrementAndGet()
                    // A skipped file still counts toward the byte total, or the
                    // bar would stall on a mostly-cached install.
                    if (outcome is DownloadOutcome.Skipped) {
                        transferred.addAndGet(request.size)
                    }
                    report(request.label)
                    outcome
                }
            }
        }.awaitAll()

        report(null)
        results
    }

    private class TransferResult(val bytes: Long, val resumed: Boolean)

    private suspend fun transfer(
        url: String,
        request: DownloadRequest,
        onBytes: ((Long) -> Unit)?,
    ): TransferResult {
        val destination = request.destination
        destination.parentFile?.mkdirs()
        val part = File(destination.parentFile, destination.name + PART_SUFFIX)

        val existing = if (part.isFile) part.length() else 0L
        // Only resume when we know the target size and have a sane prefix.
        val resumeFrom = if (existing > 0 && (request.size <= 0 || existing < request.size)) existing else null
        if (resumeFrom == null && part.exists()) part.delete()

        var written = 0L
        var resumed = false

        transport.open(url, resumeFrom).use { body ->
            if (body.statusCode !in 200..299) {
                throw HttpStatusException(url, body.statusCode)
            }
            resumed = body.resumed && resumeFrom != null

            RandomAccessFile(part, "rw").use { output ->
                if (resumed) {
                    output.seek(resumeFrom!!)
                    written = resumeFrom
                } else {
                    output.setLength(0)
                }

                val buffer = ByteArray(bufferSize)
                body.stream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onBytes?.invoke(read.toLong())
                    }
                }
            }
        }

        if (request.size > 0 && part.length() != request.size) {
            part.delete()
            throw IOException(
                "Size mismatch for ${request.label}: expected ${request.size}, got ${part.length()}",
            )
        }

        if (!request.sha1.isNullOrBlank()) {
            val actual = Hashing.sha1(part)
            if (!actual.equals(request.sha1, ignoreCase = true)) {
                part.delete()
                throw IOException(
                    "Checksum mismatch for ${request.label}: expected ${request.sha1}, got $actual",
                )
            }
        }

        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) {
            part.copyTo(destination, overwrite = true)
            part.delete()
        }

        return TransferResult(written, resumed)
    }

    companion object {
        const val PART_SUFFIX = ".part"

        /** Deliberately modest: more sockets mostly means more heat, not more speed. */
        const val DEFAULT_CONCURRENCY = 6
    }
}
