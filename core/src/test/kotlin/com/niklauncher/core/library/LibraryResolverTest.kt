package com.niklauncher.core.library

import com.niklauncher.core.manifest.DownloadEntry
import com.niklauncher.core.manifest.Library
import com.niklauncher.core.manifest.LibraryDownloads
import com.niklauncher.core.rules.OsConstraint
import com.niklauncher.core.rules.Rule
import com.niklauncher.core.rules.RuleAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LibraryResolverTest {

    private val resolver = LibraryResolver()

    @Test
    fun `keeps a plain library and uses its declared path and url`() {
        val library = Library(
            name = "com.mojang:brigadier:1.0.18",
            downloads = LibraryDownloads(
                artifact = DownloadEntry(
                    url = "https://libraries.minecraft.net/com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                    sha1 = "abc123",
                    size = 1234,
                    path = "com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                ),
            ),
        )

        val resolution = resolver.resolve(listOf(library))

        assertEquals(1, resolution.classpath.size)
        val entry = resolution.classpath.single()
        assertEquals("com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar", entry.path)
        assertEquals("abc123", entry.sha1)
        assertEquals(1234, entry.size)
        assertEquals(LibrarySource.DOWNLOAD, entry.source)
    }

    @Test
    fun `drops libraries excluded by rules`() {
        val macOnly = Library(
            name = "ca.weblite:java-objc-bridge:1.1",
            rules = listOf(Rule(action = RuleAction.ALLOW, os = OsConstraint(name = "osx"))),
        )

        val resolution = resolver.resolve(listOf(macOnly))

        assertTrue(resolution.classpath.isEmpty())
        assertEquals(SkipReason.RULES, resolution.skipped.single().reason)
    }

    @Test
    fun `drops desktop native bundles`() {
        val natives = Library(
            name = "org.lwjgl.lwjgl:lwjgl-platform:2.9.4",
            natives = mapOf("linux" to "natives-linux", "windows" to "natives-windows"),
        )

        val resolution = resolver.resolve(listOf(natives))

        assertTrue(resolution.classpath.isEmpty())
        assertEquals(SkipReason.DESKTOP_NATIVE, resolution.skipped.single().reason)
    }

    @Test
    fun `drops native classifier even without a natives block`() {
        val resolution = resolver.resolve(listOf(Library(name = "org.foo:bar:1.0:natives-linux")))
        assertEquals(SkipReason.DESKTOP_NATIVE, resolution.skipped.single().reason)
    }

    @Test
    fun `routes lwjgl to the runtime pack instead of downloading it`() {
        val resolution = resolver.resolve(
            listOf(
                Library(name = "org.lwjgl:lwjgl:3.3.3"),
                Library(name = "net.java.jinput:jinput:2.0.5"),
            ),
        )

        assertTrue(resolution.classpath.isEmpty())
        assertEquals(2, resolution.runtimeProvided.size)
        assertTrue(resolution.runtimeProvided.any { it.artifact == "lwjgl" })
    }

    @Test
    fun `collapses duplicate modules to the newest version`() {
        val resolution = resolver.resolve(
            listOf(
                Library(name = "org.ow2.asm:asm:9.3"),
                Library(name = "org.ow2.asm:asm:9.10"),
                Library(name = "org.ow2.asm:asm:9.5"),
            ),
        )

        assertEquals(1, resolution.classpath.size)
        assertEquals("9.10", resolution.classpath.single().coordinate.version)
        assertEquals(2, resolution.skipped.count { it.reason == SkipReason.OUTDATED_DUPLICATE })
    }

    @Test
    fun `derives a url from a loader supplied maven root`() {
        val resolution = resolver.resolve(
            listOf(Library(name = "net.fabricmc:tiny-mappings-parser:0.3.0", url = "https://maven.fabricmc.net/")),
        )

        val entry = resolution.classpath.single()
        assertEquals(
            "https://maven.fabricmc.net/net/fabricmc/tiny-mappings-parser/0.3.0/tiny-mappings-parser-0.3.0.jar",
            entry.url,
        )
    }

    @Test
    fun `falls back to mojang repository when no url is given`() {
        val resolution = resolver.resolve(listOf(Library(name = "com.mojang:brigadier:1.0.18")))
        val entry = resolution.classpath.single()
        assertNotNull(entry.url)
        assertTrue(entry.url.startsWith(LibraryResolver.MOJANG_LIBRARIES))
    }

    @Test
    fun `records malformed coordinates without aborting resolution`() {
        val resolution = resolver.resolve(
            listOf(Library(name = "totally-broken"), Library(name = "com.mojang:brigadier:1.0.18")),
        )

        assertEquals(1, resolution.classpath.size)
        assertEquals(SkipReason.MALFORMED, resolution.skipped.single().reason)
    }
}
