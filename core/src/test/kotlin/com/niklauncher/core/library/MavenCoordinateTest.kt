package com.niklauncher.core.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MavenCoordinateTest {

    @Test
    fun `parses group artifact version`() {
        val coordinate = MavenCoordinate.parse("com.mojang:brigadier:1.0.18")
        assertEquals("com.mojang", coordinate.group)
        assertEquals("brigadier", coordinate.artifact)
        assertEquals("1.0.18", coordinate.version)
        assertNull(coordinate.classifier)
        assertEquals("jar", coordinate.extension)
    }

    @Test
    fun `builds repository path`() {
        assertEquals(
            "com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
            MavenCoordinate.parse("com.mojang:brigadier:1.0.18").toPath(),
        )
    }

    @Test
    fun `parses classifier and extension`() {
        val coordinate = MavenCoordinate.parse("net.minecraftforge:forge:1.20.1-47.2.0:universal@zip")
        assertEquals("universal", coordinate.classifier)
        assertEquals("zip", coordinate.extension)
        assertEquals(
            "net/minecraftforge/forge/1.20.1-47.2.0/forge-1.20.1-47.2.0-universal.zip",
            coordinate.toPath(),
        )
    }

    @Test
    fun `module key ignores version but keeps classifier`() {
        assertEquals("org.ow2.asm:asm", MavenCoordinate.parse("org.ow2.asm:asm:9.5").moduleKey)
        assertEquals(
            "org.ow2.asm:asm:sources",
            MavenCoordinate.parse("org.ow2.asm:asm:9.5:sources").moduleKey,
        )
    }

    @Test
    fun `rejects malformed coordinates`() {
        assertNull(MavenCoordinate.parseOrNull("not-a-coordinate"))
        assertNull(MavenCoordinate.parseOrNull("group:artifact"))
        assertNull(MavenCoordinate.parseOrNull(""))
        assertNull(MavenCoordinate.parseOrNull("group::1.0"))
    }

    @Test
    fun `round trips through toString`() {
        for (raw in listOf("a.b:c:1.0", "a.b:c:1.0:natives", "a.b:c:1.0@zip")) {
            assertEquals(raw, MavenCoordinate.parse(raw).toString())
        }
    }
}
