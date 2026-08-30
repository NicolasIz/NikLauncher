package com.niklauncher.core.install

import com.niklauncher.core.download.Downloader
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.util.Hashing
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameInstallerTest {

    private val workspace: File = Files.createTempDirectory("niklauncher-install").toFile()
    private val paths = GamePaths(workspace).also { it.createDirectories() }
    private val manifestUrl = "https://meta.test/version_manifest_v2.json"

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private val clientBytes = "fake client jar".toByteArray()
    private val libraryBytes = "fake brigadier jar".toByteArray()
    private val assetBytes = "fake icon bytes".toByteArray()

    private val clientSha = Hashing.sha1(clientBytes)
    private val librarySha = Hashing.sha1(libraryBytes)
    private val assetSha = Hashing.sha1(assetBytes)

    private val assetIndexJson: String
        get() = """{"objects":{"icons/icon_16x16.png":{"hash":"$assetSha","size":${assetBytes.size}}}}"""

    private val versionJson: String
        get() = """
            {
              "id": "1.21.1",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 },
              "assetIndex": {
                "id": "17",
                "url": "https://meta.test/assets/17.json",
                "sha1": "${Hashing.sha1(assetIndexJson.toByteArray())}",
                "size": ${assetIndexJson.toByteArray().size}
              },
              "downloads": {
                "client": { "url": "https://piston.test/client.jar", "sha1": "$clientSha", "size": ${clientBytes.size} }
              },
              "libraries": [
                {
                  "name": "com.mojang:brigadier:1.0.18",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                      "url": "https://libraries.test/brigadier.jar",
                      "sha1": "$librarySha",
                      "size": ${libraryBytes.size}
                    }
                  }
                }
              ]
            }
        """.trimIndent()

    private fun assetUrl() = "https://resources.download.minecraft.net/${assetSha.take(2)}/$assetSha"

    private fun transport(): FakeMetadataTransport = FakeMetadataTransport().apply {
        val version = versionJson
        put(
            manifestUrl,
            """
            {
              "latest": { "release": "1.21.1", "snapshot": "1.21.1" },
              "versions": [
                {
                  "id": "1.21.1", "type": "release",
                  "url": "https://meta.test/1.21.1.json",
                  "sha1": "${Hashing.sha1(version.toByteArray())}"
                }
              ]
            }
            """.trimIndent(),
        )
        put("https://meta.test/1.21.1.json", version)
        put("https://meta.test/assets/17.json", assetIndexJson)
        putBinary("https://piston.test/client.jar", clientBytes)
        putBinary("https://libraries.test/brigadier.jar", libraryBytes)
        putBinary(assetUrl(), assetBytes)
    }

    private fun installer(fake: FakeMetadataTransport): GameInstaller {
        val catalog = VersionCatalog(MetadataClient(fake), paths, manifestUrl)
        return GameInstaller(catalog, Downloader(fake, initialBackoffMillis = 0), paths)
    }

    @Test
    fun `installs every file a version needs`() = runTest {
        val result = installer(transport()).install("1.21.1")

        val success = assertIs<InstallResult.Success>(result)
        assertEquals("1.21.1", success.plan.versionId)
        assertTrue(paths.versionJar("1.21.1").isFile, "client jar missing")
        assertTrue(paths.library("com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar").isFile, "library missing")
        assertTrue(paths.assetObject(assetSha).isFile, "asset missing")
        assertEquals(clientSha, Hashing.sha1(paths.versionJar("1.21.1")))
    }

    @Test
    fun `reports progress through to completion`() = runTest {
        val stages = mutableListOf<InstallStage>()
        val result = installer(transport()).install("1.21.1") { stages += it.stage }

        assertIs<InstallResult.Success>(result)
        assertTrue(stages.first() == InstallStage.RESOLVING_METADATA)
        assertTrue(stages.contains(InstallStage.DOWNLOADING))
        assertEquals(InstallStage.COMPLETE, stages.last())
    }

    @Test
    fun `a missing file yields an incomplete install that names it`() = runTest {
        val fake = transport().apply { remove("https://libraries.test/brigadier.jar") }

        val result = installer(fake).install("1.21.1")

        val incomplete = assertIs<InstallResult.Incomplete>(result)
        assertEquals(1, incomplete.failures.size)
        assertTrue(incomplete.failures.single().label.contains("brigadier"))
        // The rest of the install still landed, so a retry is cheap.
        assertTrue(paths.versionJar("1.21.1").isFile)
    }

    @Test
    fun `re-running an install re-downloads nothing`() = runTest {
        val fake = transport()
        installer(fake).install("1.21.1")
        val afterFirst = fake.requestedUrls.size

        val result = installer(fake).install("1.21.1")

        assertIs<InstallResult.Success>(result)
        val refetched = fake.requestedUrls.drop(afterFirst)
        assertTrue(
            refetched.none { it.startsWith("https://piston.test") || it.startsWith("https://libraries.test") },
            "verified files should be skipped, but re-fetched: $refetched",
        )
    }

    @Test
    fun `isInstalled reflects what is actually on disk`() = runTest {
        val fake = transport()
        assertTrue(!installer(fake).isInstalled("1.21.1"))
        installer(fake).install("1.21.1")
        assertTrue(installer(fake).isInstalled("1.21.1"))
    }

    @Test
    fun `an unknown version fails without leaving files behind`() = runTest {
        val result = installer(transport()).install("does-not-exist")
        assertIs<InstallResult.Failed>(result)
    }

    @Test
    fun `a legacy index gets its named asset tree built`() = runTest {
        val legacyIndex = """{"virtual":true,"objects":{"minecraft/sounds/step.ogg":{"hash":"$assetSha","size":${assetBytes.size}}}}"""
        val version = versionJson.replace(
            Regex("\"sha1\": \"[a-f0-9]+\",\\s*\"size\": \\d+\\s*\\},\\s*\"downloads\""),
            "\"sha1\": \"${Hashing.sha1(legacyIndex.toByteArray())}\", \"size\": ${legacyIndex.toByteArray().size} },\n  \"downloads\"",
        )
        val fake = transport().apply {
            put("https://meta.test/assets/17.json", legacyIndex)
            put("https://meta.test/1.21.1.json", version)
            put(
                manifestUrl,
                """
                {
                  "latest": { "release": "1.21.1", "snapshot": "1.21.1" },
                  "versions": [
                    { "id": "1.21.1", "type": "release", "url": "https://meta.test/1.21.1.json",
                      "sha1": "${Hashing.sha1(version.toByteArray())}" }
                  ]
                }
                """.trimIndent(),
            )
        }

        val gameDirectory = File(workspace, "game").also { it.mkdirs() }
        val result = installer(fake).install("1.21.1", gameDirectory)

        assertIs<InstallResult.Success>(result)
        assertTrue(
            File(File(File(paths.assets, "virtual"), "17"), "minecraft/sounds/step.ogg").isFile,
            "the legacy named tree was not built",
        )
    }
}
