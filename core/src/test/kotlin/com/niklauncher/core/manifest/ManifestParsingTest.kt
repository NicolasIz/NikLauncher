package com.niklauncher.core.manifest

import com.niklauncher.core.rules.RuleAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ManifestParsingTest {

    @Test
    fun `parses the version manifest index`() {
        val json = """
            {
              "latest": { "release": "1.21.1", "snapshot": "24w35a" },
              "versions": [
                { "id": "1.21.1", "type": "release", "url": "https://example/1.21.1.json", "sha1": "aaa" },
                { "id": "24w35a", "type": "snapshot", "url": "https://example/24w35a.json" },
                { "id": "b1.7.3", "type": "old_beta", "url": "https://example/b1.7.3.json" }
              ]
            }
        """.trimIndent()

        val manifest = ManifestCodec.decodeManifest(json)

        assertEquals("1.21.1", manifest.latest.release)
        assertEquals(3, manifest.versions.size)
        assertEquals(VersionType.OLD_BETA, manifest.versions[2].type)
        assertEquals(1, manifest.releases().size)
        assertEquals("aaa", manifest.find("1.21.1")?.sha1)
    }

    @Test
    fun `parses modern rule aware arguments`() {
        val json = """
            {
              "id": "1.21.1",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 },
              "arguments": {
                "game": [
                  "--username", "${'$'}{auth_player_name}",
                  { "rules": [{ "action": "allow", "features": { "is_demo_user": true } }], "value": "--demo" },
                  {
                    "rules": [{ "action": "allow", "features": { "has_custom_resolution": true } }],
                    "value": ["--width", "${'$'}{resolution_width}"]
                  }
                ],
                "jvm": [
                  { "rules": [{ "action": "allow", "os": { "name": "osx" } }], "value": "-XstartOnFirstThread" },
                  "-cp", "${'$'}{classpath}"
                ]
              }
            }
        """.trimIndent()

        val version = ManifestCodec.decodeVersion(json)

        assertEquals(21, version.javaVersion?.majorVersion)
        val game = assertNotNull(version.arguments).game
        assertEquals(4, game.size)
        assertIs<Argument.Literal>(game[0])
        val demo = assertIs<Argument.Conditional>(game[2])
        assertEquals(RuleAction.ALLOW, demo.rules.single().action)
        assertEquals(listOf("--demo"), demo.values)
        val resolution = assertIs<Argument.Conditional>(game[3])
        assertEquals(listOf("--width", "\${resolution_width}"), resolution.values)
    }

    @Test
    fun `parses legacy flat arguments`() {
        val json = """
            {
              "id": "1.8.9",
              "type": "release",
              "mainClass": "net.minecraft.client.main.Main",
              "minecraftArguments": "--username ${'$'}{auth_player_name} --version ${'$'}{version_name}",
              "assets": "1.8"
            }
        """.trimIndent()

        val version = ManifestCodec.decodeVersion(json)

        assertEquals("1.8", version.assets)
        assertTrue(version.arguments == null)
        assertTrue(version.minecraftArguments!!.contains("--username"))
    }

    @Test
    fun `tolerates unknown fields so new releases do not break parsing`() {
        val json = """
            { "id": "1.99", "type": "release", "someBrandNewMojangField": { "nested": true } }
        """.trimIndent()

        assertEquals("1.99", ManifestCodec.decodeVersion(json).id)
    }

    @Test
    fun `parses libraries with downloads and natives`() {
        val json = """
            {
              "id": "1.12.2",
              "type": "release",
              "libraries": [
                {
                  "name": "com.mojang:brigadier:1.0.18",
                  "downloads": {
                    "artifact": {
                      "path": "com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
                      "sha1": "deadbeef",
                      "size": 77,
                      "url": "https://libraries.minecraft.net/x.jar"
                    }
                  }
                },
                {
                  "name": "org.lwjgl.lwjgl:lwjgl-platform:2.9.4",
                  "natives": { "linux": "natives-linux" },
                  "extract": { "exclude": ["META-INF/"] }
                }
              ]
            }
        """.trimIndent()

        val version = ManifestCodec.decodeVersion(json)

        assertEquals(2, version.libraries.size)
        assertEquals("deadbeef", version.libraries[0].downloads?.artifact?.sha1)
        assertEquals("natives-linux", version.libraries[1].natives["linux"])
        assertEquals(listOf("META-INF/"), version.libraries[1].extract?.exclude)
    }

    @Test
    fun `re-encoding arguments preserves both shapes`() {
        val original = Arguments(
            game = listOf(
                Argument.Literal("--username"),
                Argument.Conditional(emptyList(), listOf("--demo")),
            ),
        )

        val text = ManifestCodec.json.encodeToString(Arguments.serializer(), original)
        val decoded = ManifestCodec.json.decodeFromString(Arguments.serializer(), text)

        assertIs<Argument.Literal>(decoded.game[0])
        assertIs<Argument.Conditional>(decoded.game[1])
        assertEquals(listOf("--demo"), decoded.game[1].values)
    }
}
