package com.niklauncher.core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsPropertiesTest {

    private val packDirectory = "/data/data/com.niklauncher/files/runtime/lib"

    @Test
    fun `every backend points LWJGL at the pack for its own natives`() {
        GraphicsBackend.entries.forEach { backend ->
            val properties = GraphicsProperties.forBackend(backend, packDirectory)
            assertTrue(
                properties.contains("-Dorg.lwjgl.librarypath=$packDirectory"),
                "$backend must set the library path, otherwise LWJGL looks for its natives elsewhere",
            )
        }
    }

    @Test
    fun `zink is reached through Mesa's EGL`() {
        val properties = GraphicsProperties.forBackend(GraphicsBackend.ZINK, packDirectory)

        assertTrue(properties.contains("-Dorg.lwjgl.opengl.contextAPI=EGL"))
        assertTrue(properties.contains("-Dorg.lwjgl.egl.libname=$packDirectory/libEGL.so"))
    }

    @Test
    fun `zink does not name an OpenGL library, because Mesa builds none for Android`() {
        val properties = GraphicsProperties.forBackend(GraphicsBackend.ZINK, packDirectory)

        assertFalse(
            properties.any { it.startsWith("-Dorg.lwjgl.opengl.libname=") },
            "naming a libGL that the pack does not contain would fail the load outright",
        )
    }

    @Test
    fun `the direct backends name a libGL and leave the context API alone`() {
        listOf(GraphicsBackend.GL4ES, GraphicsBackend.LTW).forEach { backend ->
            val properties = GraphicsProperties.forBackend(backend, packDirectory)

            assertTrue(properties.contains("-Dorg.lwjgl.opengl.libname=$packDirectory/libGL.so"))
            assertFalse(
                properties.any { it.startsWith("-Dorg.lwjgl.opengl.contextAPI=") },
                "$backend exports its entry points directly; asking for EGL would send " +
                    "LWJGL looking for an eglGetProcAddress it does not provide",
            )
        }
    }

    @Test
    fun `paths are absolute, since an app data directory is not on the loader search path`() {
        GraphicsBackend.entries.forEach { backend ->
            GraphicsProperties.forBackend(backend, packDirectory)
                .filter { it.contains(".so") }
                .forEach { property ->
                    val path = property.substringAfter('=')
                    assertTrue(path.startsWith("/"), "expected an absolute path, got $path")
                }
        }
    }

    @Test
    fun `a trailing separator does not produce a doubled one`() {
        val properties = GraphicsProperties.forBackend(GraphicsBackend.ZINK, "$packDirectory/")

        assertTrue(properties.contains("-Dorg.lwjgl.egl.libname=$packDirectory/libEGL.so"))
        assertFalse(properties.any { it.contains("//") })
    }

    @Test
    fun `each property is emitted once, so none can shadow another`() {
        GraphicsBackend.entries.forEach { backend ->
            val names = GraphicsProperties.forBackend(backend, packDirectory)
                .map { it.substringBefore('=') }

            assertEquals(names.distinct(), names, "$backend emitted a duplicate property")
        }
    }
}
