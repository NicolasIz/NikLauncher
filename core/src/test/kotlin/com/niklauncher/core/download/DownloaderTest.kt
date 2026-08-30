package com.niklauncher.core.download

import com.niklauncher.core.util.Hashing
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DownloaderTest {

    private val workspace: File = Files.createTempDirectory("niklauncher-download").toFile()

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private val payload = "NikLauncher payload for verification".toByteArray()
    private val payloadSha1 = Hashing.sha1(payload)
    private val url = "https://example.test/file.jar"

    private fun transport(
        supportsRanges: Boolean = true,
        truncateAfter: Int? = null,
        failuresBeforeSuccess: Int = 0,
        statusOverride: Int? = null,
    ) = FakeHttpTransport(
        content = mapOf(url to payload),
        supportsRanges = supportsRanges,
        truncateAfter = truncateAfter,
        failuresBeforeSuccess = failuresBeforeSuccess,
        statusOverride = statusOverride,
    )

    private fun request(destination: File, sha1: String? = payloadSha1, size: Long = payload.size.toLong()) =
        DownloadRequest(url = url, destination = destination, sha1 = sha1, size = size)

    @Test
    fun `downloads and verifies a file`() = runTest {
        val target = File(workspace, "file.jar")
        val outcome = Downloader(transport().asTransport()).download(request(target))

        assertIs<DownloadOutcome.Completed>(outcome)
        assertTrue(target.isFile)
        assertEquals(payloadSha1, Hashing.sha1(target))
        assertFalse(File(workspace, "file.jar.part").exists())
    }

    @Test
    fun `skips a file that is already present and valid`() = runTest {
        val target = File(workspace, "cached.jar")
        target.writeBytes(payload)

        val fake = transport()
        val outcome = Downloader(fake.asTransport()).download(request(target))

        assertIs<DownloadOutcome.Skipped>(outcome)
        assertEquals(0, fake.openCount, "a valid cached file must not be re-fetched")
    }

    @Test
    fun `re-downloads a file whose hash does not match`() = runTest {
        val target = File(workspace, "corrupt.jar")
        target.writeBytes("corrupted contents".toByteArray())

        val outcome = Downloader(transport().asTransport()).download(request(target))

        assertIs<DownloadOutcome.Completed>(outcome)
        assertEquals(payloadSha1, Hashing.sha1(target))
    }

    @Test
    fun `rejects a payload whose checksum is wrong and leaves no file behind`() = runTest {
        val target = File(workspace, "bad.jar")
        val outcome = Downloader(transport().asTransport(), maxAttempts = 1, initialBackoffMillis = 0)
            .download(request(target, sha1 = "0000000000000000000000000000000000000000", size = 0))

        assertIs<DownloadOutcome.Failed>(outcome)
        assertFalse(target.exists(), "a mismatched download must not be published")
        assertFalse(File(workspace, "bad.jar.part").exists())
    }

    @Test
    fun `resumes from a partial file using a range request`() = runTest {
        val target = File(workspace, "resume.jar")
        val part = File(workspace, "resume.jar.part")
        val prefixLength = 10
        part.writeBytes(payload.copyOfRange(0, prefixLength))

        val fake = transport()
        val outcome = Downloader(fake.asTransport()).download(request(target))

        val completed = assertIs<DownloadOutcome.Completed>(outcome)
        assertTrue(completed.resumed, "expected the transfer to resume")
        assertEquals(prefixLength.toLong(), fake.lastRangeRequested)
        assertEquals(payloadSha1, Hashing.sha1(target))
    }

    @Test
    fun `restarts cleanly when the server ignores the range request`() = runTest {
        val target = File(workspace, "norange.jar")
        File(workspace, "norange.jar.part").writeBytes(payload.copyOfRange(0, 10))

        val outcome = Downloader(transport(supportsRanges = false).asTransport()).download(request(target))

        assertIs<DownloadOutcome.Completed>(outcome)
        assertEquals(payloadSha1, Hashing.sha1(target))
    }

    @Test
    fun `retries a transient failure and then succeeds`() = runTest {
        val target = File(workspace, "flaky.jar")
        val fake = transport(failuresBeforeSuccess = 2)

        val outcome = Downloader(fake.asTransport(), initialBackoffMillis = 0).download(request(target))

        assertIs<DownloadOutcome.Completed>(outcome)
        assertEquals(3, fake.openCount)
    }

    @Test
    fun `gives up after the attempt limit`() = runTest {
        val target = File(workspace, "dead.jar")
        val fake = transport(failuresBeforeSuccess = 99)

        val outcome = Downloader(fake.asTransport(), maxAttempts = 3, initialBackoffMillis = 0)
            .download(request(target))

        assertIs<DownloadOutcome.Failed>(outcome)
        assertEquals(3, fake.openCount)
    }

    @Test
    fun `does not retry a 404`() = runTest {
        val target = File(workspace, "missing.jar")
        val fake = FakeHttpTransport(content = emptyMap())

        val outcome = Downloader(fake.asTransport(), maxAttempts = 4, initialBackoffMillis = 0)
            .download(DownloadRequest(url = "https://example.test/nope.jar", destination = target))

        assertIs<DownloadOutcome.Failed>(outcome)
        assertEquals(1, fake.openCount, "a 404 is not worth retrying")
    }

    @Test
    fun `detects a truncated transfer through the size check`() = runTest {
        val target = File(workspace, "short.jar")
        val outcome = Downloader(transport(truncateAfter = 5).asTransport(), maxAttempts = 1, initialBackoffMillis = 0)
            .download(request(target))

        assertIs<DownloadOutcome.Failed>(outcome)
        assertFalse(target.exists())
    }

    @Test
    fun `falls back to a mirror when the primary url fails`() = runTest {
        val target = File(workspace, "mirrored.jar")
        val mirrorUrl = "https://mirror.test/file.jar"
        val fake = FakeHttpTransport(content = mapOf(mirrorUrl to payload))

        val outcome = Downloader(fake.asTransport(), maxAttempts = 1, initialBackoffMillis = 0).download(
            DownloadRequest(
                url = "https://primary.test/file.jar",
                destination = target,
                sha1 = payloadSha1,
                size = payload.size.toLong(),
                mirrors = listOf(mirrorUrl),
            ),
        )

        assertIs<DownloadOutcome.Completed>(outcome)
        assertEquals(payloadSha1, Hashing.sha1(target))
    }

    @Test
    fun `reports progress across a batch`() = runTest {
        val files = (1..5).map { File(workspace, "batch-$it.jar") }
        val content = files.associate { "https://example.test/${it.name}" to payload }
        val fake = FakeHttpTransport(content = content)
        val requests = files.map {
            DownloadRequest(
                url = "https://example.test/${it.name}",
                destination = it,
                sha1 = payloadSha1,
                size = payload.size.toLong(),
            )
        }

        val updates = mutableListOf<DownloadProgress>()
        val outcomes = Downloader(fake.asTransport()).downloadAll(requests) { synchronized(updates) { updates += it } }

        assertTrue(outcomes.all { it is DownloadOutcome.Completed })
        assertTrue(files.all { it.isFile })
        val last = updates.last()
        assertEquals(5, last.completedFiles)
        assertEquals(5, last.totalFiles)
        assertEquals(1f, last.fraction)
    }
}
