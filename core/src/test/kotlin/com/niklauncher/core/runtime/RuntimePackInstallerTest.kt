package com.niklauncher.core.runtime

import com.niklauncher.core.download.Downloader
import com.niklauncher.core.install.FakeMetadataTransport
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.util.Hashing
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimePackInstallerTest {

    private val workspace: File = Files.createTempDirectory("niklauncher-runtime").toFile()
    private val paths = GamePaths(workspace).also { it.createDirectories() }

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private val packUrl = "https://packs.test/jre21-arm64.zip"

    /** A minimal pack payload: enough structure for the installer to validate. */
    private fun packBytes(includeLibjvm: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            if (includeLibjvm) {
                zip.putNextEntry(ZipEntry("lib/server/libjvm.so"))
                zip.write("fake-elf".toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("release"))
            zip.write("JAVA_VERSION=\"21.0.5\"".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("lwjgl/lwjgl-android.jar"))
            zip.write("jar".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun pack(
        version: String = "21.0.5",
        maxPageSizeBytes: Int = 16384,
        bytes: ByteArray = packBytes(),
        abi: String = RuntimePack.DEFAULT_ABI,
    ) = RuntimePack(
        id = "jre21-arm64-" + version,
        runtimeId = JavaRuntime.JRE_21.id,
        version = version,
        abi = abi,
        url = packUrl,
        sha1 = Hashing.sha1(bytes),
        size = bytes.size.toLong(),
        providedClasspath = listOf("lwjgl/lwjgl-android.jar"),
        graphicsBackendIds = listOf(GraphicsBackend.ZINK.id, GraphicsBackend.GL4ES.id),
        maxPageSizeBytes = maxPageSizeBytes,
    )

    private fun installer(
        index: RuntimePackIndex,
        transport: FakeMetadataTransport,
        devicePageSizeBytes: Int = 4096,
    ) = RuntimePackInstaller(
        paths = paths,
        downloader = Downloader(transport, initialBackoffMillis = 0),
        indexProvider = { index },
        devicePageSizeBytes = devicePageSizeBytes,
    )

    private fun transportServing(bytes: ByteArray) = FakeMetadataTransport().apply {
        putBinary(packUrl, bytes)
    }

    @Test
    fun `installs a pack and reports where libjvm lives`() = runTest {
        val bytes = packBytes()
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transportServing(bytes))

        val installed = subject.ensureInstalled(JavaRuntime.JRE_21)

        assertEquals(JavaRuntime.JRE_21, installed.runtime)
        assertEquals("21.0.5", installed.version)
        assertTrue(installed.libjvm.isFile, "libjvm.so was not extracted")
        assertEquals("fake-elf", installed.libjvm.readText())
        assertEquals(1, installed.providedClasspath.size)
        assertTrue(installed.providedClasspath.single().isFile)
    }

    @Test
    fun `an installed pack is reported afterwards`() = runTest {
        val bytes = packBytes()
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transportServing(bytes))

        assertTrue(subject.installedRuntimes().isEmpty())
        subject.ensureInstalled(JavaRuntime.JRE_21)

        assertEquals(listOf(JavaRuntime.JRE_21), subject.installedRuntimes().map { it.runtime })
    }

    @Test
    fun `an already installed pack is not downloaded again`() = runTest {
        val bytes = packBytes()
        val transport = transportServing(bytes)
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transport)

        subject.ensureInstalled(JavaRuntime.JRE_21)
        val afterFirst = transport.requestedUrls.size
        subject.ensureInstalled(JavaRuntime.JRE_21)

        assertEquals(afterFirst, transport.requestedUrls.size)
    }

    @Test
    fun `backends come from the installed pack`() = runTest {
        val bytes = packBytes()
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transportServing(bytes))

        assertTrue(subject.availableBackends().isEmpty())
        subject.ensureInstalled(JavaRuntime.JRE_21)

        val backends = subject.availableBackends()
        assertTrue(backends.contains(GraphicsBackend.ZINK))
        assertTrue(backends.contains(GraphicsBackend.GL4ES))
    }

    @Test
    fun `the downloaded archive is deleted once unpacked`() = runTest {
        val bytes = packBytes()
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transportServing(bytes))

        subject.ensureInstalled(JavaRuntime.JRE_21)

        val leftovers = paths.cache.listFiles()?.filter { it.name.startsWith("jre21") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "the archive should not double the storage cost: " + leftovers)
    }

    @Test
    fun `a pack missing libjvm is rejected and leaves nothing behind`() = runTest {
        val bytes = packBytes(includeLibjvm = false)
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transportServing(bytes))

        assertFailsWith<RuntimePackException.Invalid> { subject.ensureInstalled(JavaRuntime.JRE_21) }

        assertFalse(paths.runtime(JavaRuntime.JRE_21.id).exists())
        assertTrue(subject.installedRuntimes().isEmpty())
    }

    @Test
    fun `a corrupted download is rejected`() = runTest {
        val declared = packBytes()
        // The served bytes do not match the checksum the index published.
        val transport = transportServing("not the pack".toByteArray())
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = declared))), transport)

        assertFailsWith<RuntimePackException.DownloadFailed> { subject.ensureInstalled(JavaRuntime.JRE_21) }
    }

    @Test
    fun `no published pack yields a clear failure`() = runTest {
        val subject = installer(RuntimePackIndex(), transportServing(packBytes()))

        val error = assertFailsWith<RuntimePackException.NoCompatiblePack> {
            subject.ensureInstalled(JavaRuntime.JRE_21)
        }
        assertEquals(JavaRuntime.JRE_21, error.runtime)
    }

    @Test
    fun `a pack aligned for smaller pages than the device is refused`() = runTest {
        // Exactly the failure the on-device probe was written to predict.
        val bytes = packBytes()
        val index = RuntimePackIndex(packs = listOf(pack(bytes = bytes, maxPageSizeBytes = 4096)))
        val subject = installer(index, transportServing(bytes), devicePageSizeBytes = 16384)

        val error = assertFailsWith<RuntimePackException.PageSizeMismatch> {
            subject.ensureInstalled(JavaRuntime.JRE_21)
        }
        assertEquals(16384, error.devicePageSizeBytes)
    }

    @Test
    fun `a pack for another architecture is not offered`() = runTest {
        val bytes = packBytes()
        val index = RuntimePackIndex(packs = listOf(pack(bytes = bytes, abi = "armeabi-v7a")))

        assertFailsWith<RuntimePackException.NoCompatiblePack> {
            installer(index, transportServing(bytes)).ensureInstalled(JavaRuntime.JRE_21)
        }
    }

    @Test
    fun `removing a runtime clears it from disk`() = runTest {
        val bytes = packBytes()
        val subject = installer(RuntimePackIndex(packs = listOf(pack(bytes = bytes))), transportServing(bytes))
        subject.ensureInstalled(JavaRuntime.JRE_21)

        subject.remove(JavaRuntime.JRE_21)

        assertFalse(paths.runtime(JavaRuntime.JRE_21.id).exists())
        assertTrue(subject.installedRuntimes().isEmpty())
    }

    @Test
    fun `the index picks the newest compatible pack`() {
        val index = RuntimePackIndex(
            packs = listOf(
                pack(version = "21.0.9"),
                pack(version = "21.0.10"),
                pack(version = "21.0.2"),
            ),
        )

        assertEquals("21.0.10", index.bestFor(JavaRuntime.JRE_21)?.version)
    }

    @Test
    fun `the index skips packs the device cannot map`() {
        val index = RuntimePackIndex(
            packs = listOf(
                pack(version = "21.0.10", maxPageSizeBytes = 4096),
                pack(version = "21.0.2", maxPageSizeBytes = 16384),
            ),
        )

        assertEquals(
            "21.0.2",
            index.bestFor(JavaRuntime.JRE_21, devicePageSizeBytes = 16384)?.version,
            "a newer pack that cannot map is worse than an older one that can",
        )
    }
}
