package com.niklauncher.core.launch

import com.niklauncher.core.manifest.Argument
import com.niklauncher.core.manifest.Arguments
import com.niklauncher.core.manifest.VersionJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The first launch on a real device died on one line: "Unrecognized option:
 * -cp". It is not a JVM option at all - the `java` binary consumes it and
 * turns it into -Djava.class.path= before any VM exists - and NikLauncher
 * never runs that binary, because it creates the VM through JNI itself.
 *
 * Mojang's manifests emit -cp for every modern version, so this is not an edge
 * case: without the translation nothing launches, ever.
 */
class InvocationApiArgumentsTest {

    private val builder = LaunchArgumentBuilder()

    private fun context() = LaunchContext(
        playerName = "Nik",
        uuid = "0".repeat(32),
        accessToken = "0",
        userType = "legacy",
        versionName = "1.21.8",
        versionType = "release",
        gameDirectory = "/games/1218",
        assetsRoot = "/assets",
        assetsIndexName = "18",
        librariesDirectory = "/libraries",
        nativesDirectory = "/natives",
        classpath = listOf("/libraries/a.jar", "/libraries/b.jar", "/versions/1.21.8.jar"),
        resolutionWidth = 1920,
        resolutionHeight = 1080,
        launcherVersion = "0.1.0",
    )

    private fun version(jvm: List<String>) = VersionJson(
        id = "1.21.8",
        mainClass = "net.minecraft.client.main.Main",
        arguments = Arguments(
            jvm = jvm.map { Argument.Literal(it) },
            game = emptyList(),
        ),
    )

    private fun jvmArgumentsFor(jvm: List<String>) =
        builder.build(version(jvm), context()).jvmArguments

    /** Exactly the shape Mojang emits: the flag and its value as two entries. */
    @Test
    fun `the classpath Mojang declares becomes a property the VM accepts`() {
        val arguments = jvmArgumentsFor(listOf("-cp", "\${classpath}"))

        assertFalse("-cp" in arguments, "the JNI Invocation API rejects -cp outright")
        assertEquals(
            listOf("-Djava.class.path=/libraries/a.jar:/libraries/b.jar:/versions/1.21.8.jar"),
            arguments,
        )
    }

    @Test
    fun `the long spellings are translated too`() {
        assertEquals(
            listOf("-Djava.class.path=/a.jar"),
            jvmArgumentsFor(listOf("-classpath", "/a.jar")),
        )
        assertEquals(
            listOf("-Djava.class.path=/a.jar"),
            jvmArgumentsFor(listOf("--class-path", "/a.jar")),
        )
        assertEquals(
            listOf("-Djava.class.path=/a.jar"),
            jvmArgumentsFor(listOf("--class-path=/a.jar")),
        )
    }

    /**
     * The value belongs to the flag, so both are consumed together. Treating
     * them separately would leave the classpath itself on the command line as
     * a bare option, and HotSpot would reject that instead.
     */
    @Test
    fun `the value does not survive on its own`() {
        val arguments = jvmArgumentsFor(listOf("-Xmx1024M", "-cp", "/a.jar", "-Dfoo=bar"))

        assertEquals(
            listOf("-Xmx1024M", "-Djava.class.path=/a.jar", "-Dfoo=bar"),
            arguments,
        )
    }

    @Test
    fun `a flag with nothing after it is dropped rather than passed on`() {
        val arguments = jvmArgumentsFor(listOf("-Xmx1024M", "-cp"))

        assertEquals(listOf("-Xmx1024M"), arguments)
    }

    @Test
    fun `everything else is left exactly as the manifest wrote it`() {
        val manifest = listOf(
            "-Djava.library.path=/natives",
            "-Dminecraft.launcher.brand=niklauncher",
            "-XX:+UseG1GC",
            "-Xss1M",
        )

        assertEquals(manifest, jvmArgumentsFor(manifest))
    }

    /**
     * Legacy versions declare no JVM arguments at all, so the builder
     * synthesises them - and used to synthesise a -cp, which would have failed
     * the same way the moment a Java 8 runtime existed to run them on.
     */
    @Test
    fun `the synthesised arguments for a legacy version are translated as well`() {
        val legacy = VersionJson(
            id = "1.12.2",
            mainClass = "net.minecraft.client.main.Main",
            minecraftArguments = "--username \${auth_player_name}",
        )

        val arguments = builder.build(legacy, context()).jvmArguments

        assertFalse("-cp" in arguments)
        assertTrue(arguments.any { it.startsWith("-Djava.class.path=") })
    }
}
