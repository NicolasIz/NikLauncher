package com.niklauncher.core.launch

import com.niklauncher.core.install.InstallPlan
import com.niklauncher.core.instance.Instance
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.manifest.VersionJson
import com.niklauncher.core.manifest.VersionType
import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.InstalledRuntime
import com.niklauncher.core.runtime.JavaRuntime
import com.niklauncher.core.runtime.RuntimePack
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LaunchPlannerTest {

    private val root = File("/data/nik")
    private val paths = GamePaths(root)
    private val planner = LaunchPlanner(paths)

    private val version = VersionJson(
        id = "1.20.1",
        type = VersionType.RELEASE,
        mainClass = "net.minecraft.client.main.Main",
        assets = "5",
        minecraftArguments = "--gameDir \${game_directory} --assetsDir \${assets_root} " +
            "--username \${auth_player_name} --uuid \${auth_uuid}",
    )

    private val runtimeHome = File(root, "runtimes/jre21")

    private val runtime = InstalledRuntime(
        runtime = JavaRuntime.JRE_21,
        home = runtimeHome,
        libjvm = File(runtimeHome, "lib/server/libjvm.so"),
        version = "21.0.12",
        architecture = "arm64-v8a",
        providedClasspath = emptyList(),
    )

    private val pack = RuntimePack(
        id = "nikruntime-jre21",
        runtimeId = "jre21",
        version = "21.0.12",
        url = "https://example.invalid/pack.tar.gz",
        graphicsBackendIds = listOf("zink", "gl4es"),
    )

    private fun install(classpath: List<File> = listOf(File("/data/nik/libraries/a.jar"))) = InstallPlan(
        versionId = version.id,
        version = version,
        javaRuntime = JavaRuntime.JRE_21,
        client = null,
        libraries = emptyList(),
        assets = emptyList(),
        classpath = classpath,
        skippedLibraries = emptyList(),
        runtimeProvidedLibraries = emptyList(),
        assetIndexId = "5",
        requiresNamedAssetCopies = false,
    )

    private fun instance(
        memoryMegabytes: Int = 2048,
        extraJvmArguments: List<String> = emptyList(),
    ) = Instance(
        id = "inst-1",
        name = "Test",
        minecraftVersion = "1.20.1",
        resolvedVersionId = "1.20.1",
        memoryMegabytes = memoryMegabytes,
        extraJvmArguments = extraJvmArguments,
    )

    private fun plan(
        backend: GraphicsBackend = GraphicsBackend.ZINK,
        instance: Instance = instance(),
        install: InstallPlan = install(),
        runtime: InstalledRuntime = this.runtime,
        bundled: List<File> = emptyList(),
    ) = planner.plan(
        instance = instance,
        install = install,
        runtime = runtime,
        pack = pack,
        backend = backend,
        account = LaunchAccount.offline("Nik"),
        display = DisplaySize(2340, 1080),
        bundledLibraryDirectories = bundled,
    )

    /**
     * The effective library path, which is the last one on the command line.
     *
     * Minecraft's own manifest sets java.library.path to ${natives_directory},
     * and the launcher appends its own afterwards - HotSpot takes the last
     * value for a repeated -D, which is exactly why the launcher's arguments
     * go last. Reading the last one here is reading what the VM will use.
     */
    private fun libraryPathOf(planned: PlannedLaunch): List<String> =
        planned.command.jvmArguments
            .last { it.startsWith("-Djava.library.path=") }
            .removePrefix("-Djava.library.path=")
            .split(File.pathSeparator)

    @Test
    fun `the graphics directory is the one the pack declares for the backend`() {
        val zink = plan(GraphicsBackend.ZINK)
        assertEquals(
            File(runtimeHome, "nikgraphics/zink").absolutePath,
            zink.graphicsLibraryDirectory.absolutePath,
        )
        assertEquals(
            File(runtimeHome, "nikgraphics/gl4es").absolutePath,
            plan(GraphicsBackend.GL4ES).graphicsLibraryDirectory.absolutePath,
        )
    }

    @Test
    fun `zink names an egl library for the bridge, the others do not`() {
        val egl = assertNotNull(plan(GraphicsBackend.ZINK).eglLibrary)
        assertEquals("libEGL.so", egl.name)
        assertTrue(egl.absolutePath.endsWith("nikgraphics/zink/libEGL.so"))

        // These translate to a plain libGL and want the device's own EGL, so
        // naming one would send the bridge somewhere it should not go.
        assertNull(plan(GraphicsBackend.GL4ES).eglLibrary)
        assertNull(plan(GraphicsBackend.LTW).eglLibrary)
    }

    @Test
    fun `the heap size comes from the instance`() {
        assertTrue(plan(instance = instance(memoryMegabytes = 3072)).command.jvmArguments.contains("-Xmx3072M"))
    }

    @Test
    fun `the pack directory is on both library paths`() {
        val arguments = plan().command.jvmArguments
        val directory = File(runtimeHome, "nikgraphics/zink").absolutePath
        // LWJGL loads its own natives with System.loadLibrary, which reads
        // java.library.path and not org.lwjgl.librarypath.
        assertTrue(arguments.contains("-Djava.library.path=$directory"))
        assertTrue(arguments.contains("-Dorg.lwjgl.librarypath=$directory"))
    }

    @Test
    fun `the instance's own arguments come last so they can override ours`() {
        val arguments = plan(instance = instance(extraJvmArguments = listOf("-Xmx9001M"))).command.jvmArguments
        assertEquals("-Xmx9001M", arguments.last())
        assertTrue(arguments.indexOf("-Xmx2048M") < arguments.indexOf("-Xmx9001M"))
    }

    @Test
    fun `pack supplied jars precede the ones the manifest listed`() {
        val provided = File(runtimeHome, "lwjgl-android.jar")
        val fromManifest = File("/data/nik/libraries/lwjgl-desktop.jar")
        val planned = plan(
            install = install(classpath = listOf(fromManifest)),
            runtime = runtime.copy(providedClasspath = listOf(provided)),
        )
        val arguments = planned.command.jvmArguments
        val classpath = arguments[arguments.indexOf("-cp") + 1]
        assertTrue(
            classpath.indexOf(provided.absolutePath) < classpath.indexOf(fromManifest.absolutePath),
            "a replacement jar only wins if it is searched first",
        )
    }

    @Test
    fun `an offline uuid matches the derivation vanilla uses`() {
        // Fixed rather than recomputed: this is the value a vanilla server
        // derives for the same name, and a world's player data is keyed on it.
        assertEquals("b50ad385-829d-3141-a216-7e7d7539ba7f", LaunchAccount.offlineUuid("Notch"))
        assertEquals("a762f560-4fce-3236-812a-b80efff0b62b", LaunchAccount.offlineUuid("jeb_"))
    }

    @Test
    fun `an offline account carries no real token`() {
        val account = LaunchAccount.offline("Nik")
        assertEquals("0", account.accessToken)
        assertEquals("legacy", account.userType)
    }

    @Test
    fun `the game is pointed at the instance's own directory, not the shared root`() {
        val planned = plan()
        val expected = paths.instanceGameDirectory("inst-1").absolutePath
        assertTrue(
            planned.command.gameArguments.contains(expected),
            "worlds and options belong to the instance; a shared game directory would " +
                "have every instance overwrite the others",
        )
        assertTrue(planned.command.gameArguments.contains(paths.assets.absolutePath))
    }

    @Test
    fun `the offline account reaches the game arguments`() {
        val planned = plan().command.gameArguments
        assertTrue(planned.contains("Nik"))
        assertTrue(planned.contains(LaunchAccount.offlineUuid("Nik")))
    }

    /**
     * libglfw.so ships in the launcher, not in the pack - it implements the
     * GLFW ABI against the launcher's own event core, so it cannot live in a
     * runtime anyone could swap out. LWJGL finds a native library through
     * org.lwjgl.librarypath, which names one directory, and then through
     * java.library.path, which is a list. So the list is the only place the
     * pack's directory and the launcher's can both be named, and if the
     * launcher's is missing the game dies reaching for GLFW just after the VM
     * has started - on every version and every backend alike.
     */
    @Test
    fun `the library path names the launcher's own directory as well as the pack's`() {
        val launcherNatives = File("/data/app/com.niklauncher/lib/arm64")

        val path = libraryPathOf(plan(bundled = listOf(launcherNatives)))

        assertEquals(
            listOf(
                File(runtimeHome, "nikgraphics/zink").absolutePath,
                launcherNatives.absolutePath,
            ),
            path,
        )
    }

    @Test
    fun `the pack's own directory comes first`() {
        val path = libraryPathOf(plan(bundled = listOf(File("/data/app/lib/arm64"))))

        assertEquals(File(runtimeHome, "nikgraphics/zink").absolutePath, path.first())
    }

    @Test
    fun `a directory named twice appears once`() {
        val graphics = File(runtimeHome, "nikgraphics/gl4es")

        val path = libraryPathOf(
            plan(backend = GraphicsBackend.GL4ES, bundled = listOf(graphics)),
        )

        assertEquals(listOf(graphics.absolutePath), path)
    }

    @Test
    fun `with nothing bundled the path is the pack's directory alone`() {
        val path = libraryPathOf(plan(backend = GraphicsBackend.GL4ES))

        assertEquals(listOf(File(runtimeHome, "nikgraphics/gl4es").absolutePath), path)
    }

    /**
     * The manifest sets java.library.path too, so both end up on the command
     * line. HotSpot takes the last, which is why the launcher's arguments are
     * appended rather than prepended - and if that order ever inverted, the
     * game would search only the pack and never find libglfw.so.
     */
    @Test
    fun `the launcher's library path comes after the manifest's own`() {
        val arguments = plan(bundled = listOf(File("/data/app/lib/arm64")))
            .command.jvmArguments
        val positions = arguments.withIndex()
            .filter { it.value.startsWith("-Djava.library.path=") }
            .map { it.index }

        assertTrue(positions.size >= 1, "the launcher must set a library path")
        assertEquals(
            positions.max(),
            arguments.indexOfLast { it.contains("/data/app/lib/arm64") },
            "the launcher's path must be the last one, or the manifest's wins",
        )
    }
}
