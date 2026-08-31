package com.niklauncher.core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The pack layout is a contract between two things that are built separately:
 * the workflow that assembles a pack, and the launcher that reads one. Neither
 * can see the other, so the shape lives here and both sides are held to it.
 */
class RuntimePackLayoutTest {

    private fun pack(graphicsDirectory: String = RuntimePack.DEFAULT_GRAPHICS_DIRECTORY) = RuntimePack(
        id = "openjdk21",
        runtimeId = "java21",
        version = "21.0.12",
        url = "https://example.invalid/pack.tar.gz",
        graphicsDirectory = graphicsDirectory,
    )

    @Test
    fun `each backend gets its own directory`() {
        val pack = pack()
        assertEquals("nikgraphics/zink", pack.libraryDirectoryFor(GraphicsBackend.ZINK))
        assertEquals("nikgraphics/gl4es", pack.libraryDirectoryFor(GraphicsBackend.GL4ES))
        assertEquals("nikgraphics/ltw", pack.libraryDirectoryFor(GraphicsBackend.LTW))
    }

    @Test
    fun `no two backends share a directory`() {
        val pack = pack()
        val directories = GraphicsBackend.entries.map { pack.libraryDirectoryFor(it) }
        assertEquals(
            directories.size,
            directories.toSet().size,
            "backends ship libGL.so and libEGL.so under the same names, so sharing a " +
                "directory would have one shadow the other",
        )
    }

    @Test
    fun `a pack may relocate the directory`() {
        assertEquals("opt/gl/zink", pack("opt/gl").libraryDirectoryFor(GraphicsBackend.ZINK))
    }

    @Test
    fun `a trailing slash does not produce a doubled separator`() {
        assertEquals("opt/gl/zink", pack("opt/gl/").libraryDirectoryFor(GraphicsBackend.ZINK))
    }

    @Test
    fun `the directory is relative, so it resolves under an installed pack`() {
        val directory = pack().libraryDirectoryFor(GraphicsBackend.ZINK)
        assertTrue(!directory.startsWith("/"), "an absolute path would escape the install root")
    }

    @Test
    fun `the default layout round trips through the manifest`() {
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val encoded = json.encodeToString(RuntimePack.serializer(), pack())
        val decoded = json.decodeFromString(RuntimePack.serializer(), encoded)
        assertEquals(
            pack().libraryDirectoryFor(GraphicsBackend.ZINK),
            decoded.libraryDirectoryFor(GraphicsBackend.ZINK),
        )
    }
}
