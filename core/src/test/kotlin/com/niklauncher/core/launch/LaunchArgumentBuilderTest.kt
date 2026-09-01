package com.niklauncher.core.launch

import com.niklauncher.core.manifest.ManifestCodec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LaunchArgumentBuilderTest {

    private val builder = LaunchArgumentBuilder()

    private fun context(features: Map<String, Boolean> = emptyMap()) = LaunchContext(
        playerName = "Nik",
        uuid = "0123",
        accessToken = "token",
        versionName = "1.21.1",
        versionType = "release",
        gameDirectory = "/data/mc",
        assetsRoot = "/data/assets",
        assetsIndexName = "17",
        librariesDirectory = "/data/libraries",
        nativesDirectory = "/data/natives",
        classpath = listOf("/a.jar", "/b.jar"),
        resolutionWidth = 1920,
        resolutionHeight = 1080,
        launcherVersion = "0.1.0",
        features = features,
    )

    @Test
    fun `substitutes placeholders in modern arguments`() {
        val version = ManifestCodec.decodeVersion(
            """
            {
              "id": "1.21.1", "type": "release", "mainClass": "net.minecraft.client.main.Main",
              "arguments": {
                "game": ["--username", "${'$'}{auth_player_name}", "--version", "${'$'}{version_name}"],
                "jvm": ["-cp", "${'$'}{classpath}"]
              }
            }
            """.trimIndent(),
        )

        val command = builder.build(version, context())

        assertEquals(listOf("--username", "Nik", "--version", "1.21.1"), command.gameArguments)
        // -cp does not survive: the JNI Invocation API rejects it, so the
        // builder hands over the property the `java` binary would have made.
        assertEquals(listOf("-Djava.class.path=/a.jar:/b.jar"), command.jvmArguments)
        assertEquals("net.minecraft.client.main.Main", command.mainClass)
    }

    @Test
    fun `omits conditional arguments whose feature is off`() {
        val version = ManifestCodec.decodeVersion(
            """
            {
              "id": "1.21.1", "type": "release", "mainClass": "Main",
              "arguments": {
                "game": [
                  { "rules": [{ "action": "allow", "features": { "is_demo_user": true } }], "value": "--demo" }
                ],
                "jvm": []
              }
            }
            """.trimIndent(),
        )

        assertFalse(builder.build(version, context()).gameArguments.contains("--demo"))
        assertTrue(
            builder.build(version, context(mapOf("is_demo_user" to true)))
                .gameArguments.contains("--demo"),
        )
    }

    @Test
    fun `drops arguments gated on another operating system`() {
        val version = ManifestCodec.decodeVersion(
            """
            {
              "id": "1.21.1", "type": "release", "mainClass": "Main",
              "arguments": {
                "game": [],
                "jvm": [
                  { "rules": [{ "action": "allow", "os": { "name": "osx" } }], "value": "-XstartOnFirstThread" },
                  "-cp", "${'$'}{classpath}"
                ]
              }
            }
            """.trimIndent(),
        )

        val command = builder.build(version, context())

        assertFalse(command.jvmArguments.contains("-XstartOnFirstThread"))
        assertContains(command.jvmArguments, "-Djava.class.path=/a.jar:/b.jar")
    }

    @Test
    fun `synthesises jvm arguments for legacy versions`() {
        val version = ManifestCodec.decodeVersion(
            """
            {
              "id": "1.8.9", "type": "release", "mainClass": "Main",
              "minecraftArguments": "--username ${'$'}{auth_player_name} --gameDir ${'$'}{game_directory}"
            }
            """.trimIndent(),
        )

        val command = builder.build(version, context())

        assertEquals(listOf("--username", "Nik", "--gameDir", "/data/mc"), command.gameArguments)
        assertContains(command.jvmArguments, "-Djava.library.path=/data/natives")
        // Synthesised as a property, not as the -cp the vanilla launcher used:
        // these arguments go to JNI_CreateJavaVM, which takes neither.
        assertContains(command.jvmArguments, "-Djava.class.path=/a.jar:/b.jar")
    }

    @Test
    fun `appends extra jvm arguments last`() {
        val version = ManifestCodec.decodeVersion(
            """{ "id": "1.8.9", "type": "release", "mainClass": "Main", "minecraftArguments": "" }""",
        )

        val command = builder.build(version, context(), listOf("-Xmx2048M"))

        assertEquals("-Xmx2048M", command.jvmArguments.last())
    }

    @Test
    fun `leaves unknown placeholders visible rather than blanking them`() {
        val version = ManifestCodec.decodeVersion(
            """
            {
              "id": "x", "type": "release", "mainClass": "Main",
              "minecraftArguments": "--mystery ${'$'}{not_a_real_placeholder}"
            }
            """.trimIndent(),
        )

        assertContains(builder.build(version, context()).gameArguments, "\${not_a_real_placeholder}")
    }
}
