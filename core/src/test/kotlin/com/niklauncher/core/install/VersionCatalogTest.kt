package com.niklauncher.core.install

import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.util.Hashing
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersionCatalogTest {

    private val workspace: File = Files.createTempDirectory("niklauncher-catalog").toFile()
    private val paths = GamePaths(workspace).also { it.createDirectories() }
    private val manifestUrl = "https://meta.test/version_manifest_v2.json"

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private val versionJson = """
        {
          "id": "1.21.1",
          "type": "release",
          "mainClass": "net.minecraft.client.main.Main",
          "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 },
          "assetIndex": { "id": "17", "url": "https://meta.test/assets/17.json", "sha1": "%ASSET_SHA%", "size": %ASSET_SIZE% },
          "downloads": { "client": { "url": "https://meta.test/client.jar", "sha1": "abc", "size": 100 } },
          "libraries": []
        }
    """.trimIndent()

    private val assetIndexJson = """{"objects":{"icons/icon_16x16.png":{"hash":"aaaabbbb","size":12}}}"""

    private fun manifestJson(versionSha: String) = """
        {
          "latest": { "release": "1.21.1", "snapshot": "1.21.1" },
          "versions": [
            { "id": "1.21.1", "type": "release", "url": "https://meta.test/1.21.1.json", "sha1": "$versionSha" }
          ]
        }
    """.trimIndent()

    private fun transport(): FakeMetadataTransport {
        val assetSha = Hashing.sha1(assetIndexJson.toByteArray())
        val version = versionJson
            .replace("%ASSET_SHA%", assetSha)
            .replace("%ASSET_SIZE%", assetIndexJson.toByteArray().size.toString())
        val versionSha = Hashing.sha1(version.toByteArray())

        return FakeMetadataTransport().apply {
            put(manifestUrl, manifestJson(versionSha))
            put("https://meta.test/1.21.1.json", version)
            put("https://meta.test/assets/17.json", assetIndexJson)
        }
    }

    private fun catalog(
        transport: FakeMetadataTransport,
        now: () -> Long = System::currentTimeMillis,
    ) = VersionCatalog(MetadataClient(transport), paths, manifestUrl, now = now)

    @Test
    fun `fetches and parses the manifest`() = runTest {
        val manifest = catalog(transport()).manifest()
        assertEquals("1.21.1", manifest.latest.release)
        assertEquals(1, manifest.versions.size)
    }

    @Test
    fun `serves a cached manifest without hitting the network again`() = runTest {
        val fake = transport()
        val subject = catalog(fake)

        subject.manifest()
        val afterFirst = fake.requestedUrls.size
        subject.manifest()

        assertEquals(afterFirst, fake.requestedUrls.size, "second call should be served from cache")
    }

    @Test
    fun `refetches once the cache has expired`() = runTest {
        val fake = transport()
        var clock = System.currentTimeMillis()
        val subject = catalog(fake) { clock }

        subject.manifest()
        val afterFirst = fake.requestedUrls.count { it == manifestUrl }
        clock += 24 * 60 * 60 * 1000L
        subject.manifest()

        assertTrue(fake.requestedUrls.count { it == manifestUrl } > afterFirst)
    }

    @Test
    fun `falls back to a stale manifest when offline`() = runTest {
        val fake = transport()
        val subject = catalog(fake)
        subject.manifest()

        fake.offline = true
        val manifest = subject.manifest(forceRefresh = true)

        assertEquals("1.21.1", manifest.latest.release, "a stale catalogue beats no catalogue")
    }

    @Test
    fun `propagates the error when offline with no cache`() = runTest {
        val fake = transport().apply { offline = true }
        assertFailsWith<Exception> { catalog(fake).manifest() }
    }

    @Test
    fun `caches a version descriptor on disk and reuses it`() = runTest {
        val fake = transport()
        val subject = catalog(fake)

        val version = subject.rawVersion("1.21.1")
        assertEquals("net.minecraft.client.main.Main", version.mainClass)
        assertTrue(paths.versionJson("1.21.1").isFile)

        val before = fake.requestedUrls.size
        subject.rawVersion("1.21.1")
        assertEquals(before, fake.requestedUrls.size, "a cached descriptor should not be refetched")
    }

    @Test
    fun `rejects a version descriptor whose checksum does not match`() = runTest {
        val fake = transport()
        fake.put("https://meta.test/1.21.1.json", """{"id":"1.21.1","type":"release"}""")

        assertFailsWith<Exception> { catalog(fake).rawVersion("1.21.1") }
    }

    @Test
    fun `reports an unknown version rather than guessing`() = runTest {
        assertFailsWith<Exception> { catalog(transport()).rawVersion("9.9.9") }
    }

    @Test
    fun `fetches and verifies the asset index`() = runTest {
        val subject = catalog(transport())
        val version = subject.rawVersion("1.21.1")
        val index = subject.assetIndex(version.assetIndex!!)

        assertEquals(1, index.objects.size)
        assertEquals("aaaabbbb", index.objects.values.single().hash)
    }

    @Test
    fun `lists versions already installed on disk`() = runTest {
        catalog(transport()).rawVersion("1.21.1")
        assertEquals(listOf("1.21.1"), catalog(transport()).installedVersionIds())
    }

    @Test
    fun `a locally installed descriptor is preferred over the manifest`() = runTest {
        // This is how a mod loader version, which Mojang never lists, is found.
        paths.versionDirectory("fabric-loader-1.21.1").mkdirs()
        paths.versionJson("fabric-loader-1.21.1").writeText(
            """{"id":"fabric-loader-1.21.1","inheritsFrom":"1.21.1","mainClass":"KnotClient"}""",
        )

        val version = catalog(transport()).rawVersion("fabric-loader-1.21.1")

        assertEquals("KnotClient", version.mainClass)
        assertEquals("1.21.1", version.inheritsFrom)
    }

    @Test
    fun `resolves a loader version against its cached parent`() = runTest {
        val fake = transport()
        val subject = catalog(fake)
        subject.rawVersion("1.21.1")

        paths.versionDirectory("fabric-loader-1.21.1").mkdirs()
        paths.versionJson("fabric-loader-1.21.1").writeText(
            """{"id":"fabric-loader-1.21.1","inheritsFrom":"1.21.1","mainClass":"KnotClient"}""",
        )

        val merged = subject.resolvedVersion("fabric-loader-1.21.1")

        assertEquals("KnotClient", merged.mainClass)
        assertEquals(21, merged.javaVersion?.majorVersion, "java version comes from the parent")
        assertEquals("1.21.1", merged.jar)
    }
}
