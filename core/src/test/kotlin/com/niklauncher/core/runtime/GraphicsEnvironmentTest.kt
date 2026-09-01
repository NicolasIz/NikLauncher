package com.niklauncher.core.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mesa is told which driver to use and where it is, because it cannot work
 * either out for itself inside an app: its usual route is probing the kernel's
 * DRM devices, and Android does not let an app near them. Getting this wrong
 * does not produce a helpful message - it produces a failed eglInitialize - so
 * what is set, and for which backend, is pinned here.
 */
class GraphicsEnvironmentTest {

    private val zinkDirectory = "/data/user/0/com.niklauncher/files/runtimes/jre21/nikgraphics/zink"
    private val cache = "/data/user/0/com.niklauncher/files/cache/shaders"

    private fun environment(backend: GraphicsBackend) =
        GraphicsProperties.environmentFor(backend, zinkDirectory, cache)

    @Test
    fun `zink is told the driver by name`() {
        val environment = environment(GraphicsBackend.ZINK)

        assertEquals("zink", environment["MESA_LOADER_DRIVER_OVERRIDE"])
        assertEquals("zink", environment["GALLIUM_DRIVER"])
    }

    @Test
    fun `zink is told where the driver lives`() {
        assertEquals(zinkDirectory, environment(GraphicsBackend.ZINK)["LIBGL_DRIVERS_PATH"])
    }

    @Test
    fun `the shader cache is the directory the caller named`() {
        val value = environment(GraphicsBackend.ZINK)["MESA_SHADER_CACHE_DIR"]

        assertEquals(cache, value)
        assertTrue(
            value?.contains("..") == false,
            "a path built out of '..' is one rename away from pointing elsewhere",
        )
    }

    @Test
    fun `a trailing slash does not reach the environment`() {
        val environment = GraphicsProperties.environmentFor(
            GraphicsBackend.ZINK, "$zinkDirectory/", "$cache/",
        )

        assertEquals(zinkDirectory, environment["LIBGL_DRIVERS_PATH"])
        assertEquals(cache, environment["MESA_SHADER_CACHE_DIR"])
    }

    /**
     * gl4es and LTW translate to a plain libGL and never go near Mesa's
     * loader. Naming a driver for them would at best be ignored and at worst
     * send a future Mesa looking for a driver that is not there.
     */
    @Test
    fun `the backends that are not Mesa get nothing`() {
        assertTrue(environment(GraphicsBackend.GL4ES).isEmpty())
        assertTrue(environment(GraphicsBackend.LTW).isEmpty())
    }
}
