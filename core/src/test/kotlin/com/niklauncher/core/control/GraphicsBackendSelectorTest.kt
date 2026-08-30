package com.niklauncher.core.control

import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.GraphicsBackendSelector
import com.niklauncher.core.runtime.GraphicsCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GraphicsBackendSelectorTest {

    /** Roughly what a Galaxy S24 Ultra reports: Vulkan 1.3, GLES 3.2. */
    private val modernDevice = GraphicsCapabilities(
        vulkanVersion = 0x00403000,
        glesMajor = 3,
        glesMinor = 2,
        gpuName = "Adreno 750",
    )

    private val glesOnlyDevice = GraphicsCapabilities(vulkanVersion = 0, glesMajor = 3, glesMinor = 2)

    private val oldDevice = GraphicsCapabilities(vulkanVersion = 0, glesMajor = 2, glesMinor = 0)

    @Test
    fun `old versions get GL4ES rather than paying for Vulkan translation`() {
        val choice = GraphicsBackendSelector.select("1.16.5", modernDevice)
        assertEquals(GraphicsBackend.GL4ES, choice.backend)
        assertTrue(choice.confident)
    }

    @Test
    fun `1_17 and newer get Zink where Vulkan is good enough`() {
        assertEquals(GraphicsBackend.ZINK, GraphicsBackendSelector.select("1.17", modernDevice).backend)
        assertEquals(GraphicsBackend.ZINK, GraphicsBackendSelector.select("1.21.1", modernDevice).backend)
    }

    @Test
    fun `without Vulkan a modern version falls back to LTW`() {
        assertEquals(GraphicsBackend.LTW, GraphicsBackendSelector.select("1.21.1", glesOnlyDevice).backend)
    }

    @Test
    fun `a device too old for either modern path is flagged as not confident`() {
        val choice = GraphicsBackendSelector.select("1.21.1", oldDevice)
        assertFalse(choice.confident, "an unusable device must not be reported as a confident choice")
    }

    @Test
    fun `a user override wins`() {
        val choice = GraphicsBackendSelector.select("1.16.5", modernDevice, userOverride = GraphicsBackend.ZINK)
        assertEquals(GraphicsBackend.ZINK, choice.backend)
    }

    @Test
    fun `an override the device cannot run is reported as not confident`() {
        val choice = GraphicsBackendSelector.select("1.21.1", oldDevice, userOverride = GraphicsBackend.ZINK)
        assertEquals(GraphicsBackend.ZINK, choice.backend)
        assertFalse(choice.confident)
    }

    @Test
    fun `only installed backends are chosen automatically`() {
        val choice = GraphicsBackendSelector.select(
            "1.21.1",
            modernDevice,
            installedBackends = setOf(GraphicsBackend.LTW),
        )
        assertEquals(GraphicsBackend.LTW, choice.backend)
        assertTrue(choice.confident)
    }

    @Test
    fun `with nothing installed it still names what would work`() {
        val choice = GraphicsBackendSelector.select("1.21.1", modernDevice, installedBackends = emptySet())
        assertEquals(GraphicsBackend.ZINK, choice.backend)
        assertFalse(choice.confident)
        assertTrue(choice.reason.isNotBlank())
    }

    @Test
    fun `the version boundary sits between 1_16 and 1_17`() {
        assertEquals(GraphicsBackend.GL4ES, GraphicsBackendSelector.select("1.16.5", modernDevice).backend)
        assertEquals(GraphicsBackend.ZINK, GraphicsBackendSelector.select("1.17.1", modernDevice).backend)
    }

    @Test
    fun `an unparseable version is treated as modern rather than assumed old`() {
        // Guessing "old" would hand a 3.2-core version a GL 2.1 backend and
        // fail at startup; guessing "modern" merely costs some efficiency.
        assertEquals(GraphicsBackend.ZINK, GraphicsBackendSelector.select("24w35a", modernDevice).backend)
    }

    @Test
    fun `every choice explains itself`() {
        for (version in listOf("1.8.9", "1.16.5", "1.17.1", "1.21.1")) {
            assertTrue(
                GraphicsBackendSelector.select(version, modernDevice).reason.isNotBlank(),
                "no reason given for $version",
            )
        }
    }
}
