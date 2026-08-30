package com.niklauncher.core.install

import com.niklauncher.core.manifest.Argument
import com.niklauncher.core.manifest.Arguments
import com.niklauncher.core.manifest.Library
import com.niklauncher.core.manifest.VersionJson
import com.niklauncher.core.manifest.VersionResolver
import com.niklauncher.core.manifest.VersionType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionResolverTest {

    private val vanilla = VersionJson(
        id = "1.21.1",
        type = VersionType.RELEASE,
        mainClass = "net.minecraft.client.main.Main",
        assets = "17",
        libraries = listOf(Library(name = "com.mojang:brigadier:1.0.18")),
        arguments = Arguments(game = listOf(Argument.Literal("--username"))),
        javaVersion = com.niklauncher.core.manifest.JavaVersionReference("java-runtime-delta", 21),
    )

    private val fabric = VersionJson(
        id = "fabric-loader-0.16.5-1.21.1",
        inheritsFrom = "1.21.1",
        mainClass = "net.fabricmc.loader.impl.launch.knot.KnotClient",
        libraries = listOf(Library(name = "net.fabricmc:fabric-loader:0.16.5")),
        arguments = Arguments(jvm = listOf(Argument.Literal("-DFabricMcEmu=net.minecraft.client.main.Main"))),
    )

    private suspend fun resolveFabric(): VersionJson =
        VersionResolver.resolve(fabric.id) { id ->
            when (id) {
                fabric.id -> fabric
                vanilla.id -> vanilla
                else -> error("unexpected id $id")
            }
        }

    @Test
    fun `derived version keeps its own id and main class`() = runTest {
        val merged = resolveFabric()
        assertEquals("fabric-loader-0.16.5-1.21.1", merged.id)
        assertEquals("net.fabricmc.loader.impl.launch.knot.KnotClient", merged.mainClass)
    }

    @Test
    fun `loader libraries precede the vanilla ones on the classpath`() = runTest {
        val merged = resolveFabric()
        assertEquals(
            listOf("net.fabricmc:fabric-loader:0.16.5", "com.mojang:brigadier:1.0.18"),
            merged.libraries.map { it.name },
        )
    }

    @Test
    fun `parent arguments come first so derived ones override them`() = runTest {
        val merged = resolveFabric()
        assertEquals(listOf("--username"), merged.arguments?.game?.flatMap { it.values })
        assertEquals(
            listOf("-DFabricMcEmu=net.minecraft.client.main.Main"),
            merged.arguments?.jvm?.flatMap { it.values },
        )
    }

    @Test
    fun `inherited fields fall through from the parent`() = runTest {
        val merged = resolveFabric()
        assertEquals("17", merged.assets)
        assertEquals(21, merged.javaVersion?.majorVersion)
        assertEquals(VersionType.RELEASE, merged.type)
    }

    @Test
    fun `the client jar is taken from the root parent`() = runTest {
        assertEquals("1.21.1", resolveFabric().jar)
    }

    @Test
    fun `merged version no longer claims to inherit`() = runTest {
        assertNull(resolveFabric().inheritsFrom)
    }

    @Test
    fun `a single version is returned unchanged`() = runTest {
        val merged = VersionResolver.resolve("1.21.1") { vanilla }
        assertEquals(vanilla, merged)
    }

    @Test
    fun `circular inheritance is rejected`() = runTest {
        val a = VersionJson(id = "a", inheritsFrom = "b")
        val b = VersionJson(id = "b", inheritsFrom = "a")
        assertFailsWith<VersionResolver.CircularInheritanceException> {
            VersionResolver.resolve("a") { if (it == "a") a else b }
        }
    }

    @Test
    fun `runaway inheritance depth is rejected`() = runTest {
        assertFailsWith<VersionResolver.InheritanceTooDeepException> {
            VersionResolver.resolve("v0") { id ->
                val index = id.removePrefix("v").toInt()
                VersionJson(id = id, inheritsFrom = "v${index + 1}")
            }
        }
    }

    @Test
    fun `three level chain merges in order`() = runTest {
        val base = VersionJson(id = "base", mainClass = "Base", libraries = listOf(Library("g:base:1")))
        val mid = VersionJson(id = "mid", inheritsFrom = "base", libraries = listOf(Library("g:mid:1")))
        val top = VersionJson(id = "top", inheritsFrom = "mid", libraries = listOf(Library("g:top:1")))

        val merged = VersionResolver.resolve("top") {
            when (it) {
                "top" -> top; "mid" -> mid; else -> base
            }
        }

        assertEquals(listOf("g:top:1", "g:mid:1", "g:base:1"), merged.libraries.map { it.name })
        assertEquals("Base", merged.mainClass)
        assertTrue(merged.jar == "base")
    }
}
