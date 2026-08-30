package com.niklauncher.core.install

import com.niklauncher.core.assets.AssetIndex
import com.niklauncher.core.assets.AssetObject
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.manifest.DownloadEntry
import com.niklauncher.core.manifest.JavaVersionReference
import com.niklauncher.core.manifest.Library
import com.niklauncher.core.manifest.LibraryDownloads
import com.niklauncher.core.manifest.VersionDownloads
import com.niklauncher.core.manifest.VersionJson
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstallPlannerTest {

    private val workspace: File = Files.createTempDirectory("niklauncher-plan").toFile()
    private val paths = GamePaths(workspace)
    private val planner = InstallPlanner(paths)

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private fun version(
        id: String = "1.21.1",
        jar: String? = null,
        majorVersion: Int = 21,
        libraries: List<Library> = listOf(
            Library(
                name = "com.mojang:brigadier:1.0.18",
                downloads = LibraryDownloads(
                    artifact = DownloadEntry(
                        url = "https://libraries.minecraft.net/com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                        sha1 = "libsha",
                        size = 55,
                        path = "com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                    ),
                ),
            ),
        ),
    ) = VersionJson(
        id = id,
        jar = jar,
        mainClass = "net.minecraft.client.main.Main",
        libraries = libraries,
        javaVersion = JavaVersionReference("java-runtime-delta", majorVersion),
        downloads = VersionDownloads(
            client = DownloadEntry(url = "https://piston.test/client.jar", sha1 = "clientsha", size = 1000),
        ),
    )

    private val assetIndex = AssetIndex(
        objects = mapOf(
            "icons/icon_16x16.png" to AssetObject("aaaa1111", 16),
            "icons/icon_32x32.png" to AssetObject("bbbb2222", 32),
            "duplicate.png" to AssetObject("aaaa1111", 16),
        ),
    )

    @Test
    fun `plans the client jar with its checksum`() {
        val plan = planner.plan(version(), null)
        val client = assertNotNull(plan.client)
        assertEquals("clientsha", client.sha1)
        assertEquals(paths.versionJar("1.21.1"), client.destination)
    }

    @Test
    fun `plans libraries into the shared libraries tree`() {
        val plan = planner.plan(version(), null)
        val library = plan.libraries.single()
        assertEquals(paths.library("com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar"), library.destination)
        assertEquals("libsha", library.sha1)
    }

    @Test
    fun `deduplicates assets that share a hash`() {
        val plan = planner.plan(version(), assetIndex)
        assertEquals(2, plan.assets.size, "the same blob must only be fetched once")
    }

    @Test
    fun `asset requests are content addressed`() {
        val plan = planner.plan(version(), assetIndex)
        val request = plan.assets.first { it.sha1 == "aaaa1111" }
        assertEquals(paths.assetObject("aaaa1111"), request.destination)
        assertTrue(request.url.endsWith("aa/aaaa1111"))
    }

    @Test
    fun `classpath puts libraries before the client jar`() {
        val plan = planner.plan(version(), null)
        assertEquals(paths.library("com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar"), plan.classpath.first())
        assertEquals(paths.versionJar("1.21.1"), plan.classpath.last())
    }

    @Test
    fun `an inherited version stores its jar under the parent id`() {
        val plan = planner.plan(version(id = "fabric-loader-1.21.1", jar = "1.21.1"), null)
        assertEquals(paths.versionJar("1.21.1"), plan.client?.destination)
        assertEquals(paths.versionJar("1.21.1"), plan.classpath.last())
    }

    @Test
    fun `selects the java runtime the version asks for`() {
        assertEquals(
            com.niklauncher.core.runtime.JavaRuntime.JRE_8,
            planner.plan(version(majorVersion = 8), null).javaRuntime,
        )
        assertEquals(
            com.niklauncher.core.runtime.JavaRuntime.JRE_17,
            planner.plan(version(majorVersion = 17), null).javaRuntime,
        )
        assertEquals(
            com.niklauncher.core.runtime.JavaRuntime.JRE_21,
            planner.plan(version(majorVersion = 21), null).javaRuntime,
        )
    }

    @Test
    fun `keeps lwjgl off the download list and records it as runtime provided`() {
        val plan = planner.plan(version(libraries = listOf(Library(name = "org.lwjgl:lwjgl:3.3.3"))), null)
        assertTrue(plan.libraries.isEmpty())
        assertEquals(1, plan.runtimeProvidedLibraries.size)
    }

    @Test
    fun `totals cover every planned file`() {
        val plan = planner.plan(version(), assetIndex)
        assertEquals(1 + 1 + 2, plan.fileCount)
        assertEquals(1000L + 55L + 16L + 32L, plan.totalBytes)
    }

    @Test
    fun `flags an index that needs a named asset tree`() {
        val legacy = assetIndex.copy(virtual = true)
        assertTrue(planner.plan(version(), legacy).requiresNamedAssetCopies)
        assertTrue(!planner.plan(version(), assetIndex).requiresNamedAssetCopies)
    }
}
