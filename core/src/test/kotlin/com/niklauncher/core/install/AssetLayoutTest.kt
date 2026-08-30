package com.niklauncher.core.install

import com.niklauncher.core.assets.AssetIndex
import com.niklauncher.core.assets.AssetObject
import com.niklauncher.core.io.GamePaths
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetLayoutTest {

    private val workspace: File = Files.createTempDirectory("niklauncher-assets").toFile()
    private val paths = GamePaths(workspace).also { it.createDirectories() }
    private val gameDirectory = File(workspace, "instance").also { it.mkdirs() }

    @AfterTest
    fun cleanUp() {
        workspace.deleteRecursively()
    }

    private fun storeObject(hash: String, content: String) {
        val file = paths.assetObject(hash)
        file.parentFile.mkdirs()
        file.writeText(content)
    }

    @Test
    fun `modern indexes need no named tree`() {
        val index = AssetIndex(objects = mapOf("a.png" to AssetObject("aaaa1111", 3)))
        assertNull(AssetLayout.materialise(index, "17", paths, gameDirectory))
    }

    @Test
    fun `virtual indexes are materialised under assets virtual`() {
        storeObject("aaaa1111", "png")
        val index = AssetIndex(
            objects = mapOf("minecraft/sounds/step.ogg" to AssetObject("aaaa1111", 3)),
            virtual = true,
        )

        val result = assertNotNull(AssetLayout.materialise(index, "legacy", paths, gameDirectory))

        assertEquals(1, result.filesLinked)
        assertEquals(File(File(paths.assets, "virtual"), "legacy"), result.targetDirectory)
        assertTrue(File(result.targetDirectory, "minecraft/sounds/step.ogg").isFile)
    }

    @Test
    fun `map_to_resources indexes go into the instance resources directory`() {
        storeObject("bbbb2222", "ogg")
        val index = AssetIndex(
            objects = mapOf("sound/step.ogg" to AssetObject("bbbb2222", 3)),
            mapToResources = true,
        )

        val result = assertNotNull(AssetLayout.materialise(index, "pre-1.6", paths, gameDirectory))

        assertEquals(File(gameDirectory, "resources"), result.targetDirectory)
        assertTrue(File(gameDirectory, "resources/sound/step.ogg").isFile)
    }

    @Test
    fun `missing objects are reported rather than failing the install`() {
        val index = AssetIndex(
            objects = mapOf("present.png" to AssetObject("cccc3333", 3), "absent.png" to AssetObject("dddd4444", 3)),
            virtual = true,
        )
        storeObject("cccc3333", "png")

        val result = assertNotNull(AssetLayout.materialise(index, "legacy", paths, gameDirectory))

        assertEquals(1, result.filesLinked)
        assertEquals(1, result.filesMissing)
    }

    @Test
    fun `re-running does not rewrite files that are already in place`() {
        storeObject("eeee5555", "content")
        val index = AssetIndex(objects = mapOf("a.png" to AssetObject("eeee5555", 7)), virtual = true)

        val first = assertNotNull(AssetLayout.materialise(index, "legacy", paths, gameDirectory))
        val target = File(first.targetDirectory, "a.png")
        val stamp = 1_000_000L
        target.setLastModified(stamp)

        AssetLayout.materialise(index, "legacy", paths, gameDirectory)

        assertEquals(stamp, target.lastModified(), "an unchanged file should not be rewritten")
    }
}
