package com.niklauncher.core.launch

import com.niklauncher.core.NikLauncher
import com.niklauncher.core.install.InstallPlan
import com.niklauncher.core.instance.Instance
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.runtime.GraphicsBackend
import com.niklauncher.core.runtime.GraphicsProperties
import com.niklauncher.core.runtime.InstalledRuntime
import com.niklauncher.core.runtime.RuntimePack
import java.io.File

/** How large the game's window is, in pixels. */
data class DisplaySize(val width: Int, val height: Int)

/**
 * A launch worked out but not yet started.
 *
 * Carries more than the command because two of the pieces are needed outside
 * the JVM: the GLFW bridge has to be told which `libEGL.so` to resolve
 * against before the game creates its window, and the loader has to be able to
 * find that library's own dependencies. Returning them here keeps the caller
 * from having to re-derive paths the planner already worked out, which is
 * exactly where the two would drift apart.
 */
data class PlannedLaunch(
    val command: LaunchCommand,
    val backend: GraphicsBackend,
    /** Absolute directory holding this backend's shared objects and LWJGL's. */
    val graphicsLibraryDirectory: File,
    /**
     * Where the game must actually be run from. Minecraft and the libraries
     * under it open a great many paths relative to the working directory and
     * never ask where it is, so this is not the same thing as --gameDir.
     */
    val gameDirectory: File,
    /**
     * The `libEGL.so` the GLFW bridge must bind to, or null to use the
     * device's own - which is what the backends that translate to a plain
     * `libGL.so` want.
     */
    val eglLibrary: File?,
)

/**
 * Turns what the launcher already knows - an instance, an install, an
 * installed runtime and a graphics backend - into the command that starts the
 * game.
 *
 * This is the seam the whole runtime stack was built towards, and it is pure
 * Kotlin on purpose: everything decided here can be checked without a device,
 * leaving only the JNI call itself to be proven on hardware.
 */
class LaunchPlanner(
    private val paths: GamePaths,
    private val argumentBuilder: LaunchArgumentBuilder = LaunchArgumentBuilder(),
    private val launcherVersion: String = NikLauncher.VERSION,
) {

    /**
     * [bundledLibraryDirectories] are directories outside the pack that also
     * hold shared objects the game will load - on Android, the launcher's own
     * native library directory, which is where libglfw.so lives. The pack
     * cannot carry that one: it implements the GLFW ABI against this
     * launcher's event core, so it ships in the application, not in a runtime
     * anyone could swap out.
     */
    fun plan(
        instance: Instance,
        install: InstallPlan,
        runtime: InstalledRuntime,
        pack: RuntimePack,
        backend: GraphicsBackend,
        account: LaunchAccount,
        display: DisplaySize,
        bundledLibraryDirectories: List<File> = emptyList(),
    ): PlannedLaunch {
        val graphicsDirectory = File(runtime.home, pack.libraryDirectoryFor(backend))

        // Anything the pack supplies comes first: the classpath is searched in
        // order, so a jar meant to replace one the manifest lists only wins if
        // it is ahead of it.
        val classpath = (runtime.providedClasspath + install.classpath).map { it.absolutePath }

        val gameDirectory = paths.instanceGameDirectory(instance.id)

        val context = LaunchContext(
            playerName = account.playerName,
            uuid = account.uuid,
            accessToken = account.accessToken,
            userType = account.userType,
            versionName = instance.resolvedVersionId,
            versionType = install.version.type.name.lowercase(),
            gameDirectory = gameDirectory.absolutePath,
            assetsRoot = paths.assets.absolutePath,
            assetsIndexName = install.assetIndexId ?: install.version.assets ?: LEGACY_ASSETS,
            librariesDirectory = paths.libraries.absolutePath,
            // Android has no per-version natives directory: the manifest's
            // native jars are desktop builds and are skipped at resolution, so
            // the only natives that exist are the pack's.
            nativesDirectory = graphicsDirectory.absolutePath,
            classpath = classpath,
            resolutionWidth = display.width,
            resolutionHeight = display.height,
            launcherVersion = launcherVersion,
            features = mapOf("has_custom_resolution" to true),
        )

        val command = argumentBuilder.build(
            version = install.version,
            context = context,
            extraJvmArguments = jvmArguments(
                instance, graphicsDirectory, backend, bundledLibraryDirectories,
            ),
        )

        return PlannedLaunch(
            command = command,
            backend = backend,
            graphicsLibraryDirectory = graphicsDirectory,
            gameDirectory = gameDirectory,
            eglLibrary = when (backend) {
                GraphicsBackend.ZINK -> File(graphicsDirectory, "libEGL.so")
                GraphicsBackend.GL4ES, GraphicsBackend.LTW -> null
            },
        )
    }

    /**
     * Appended after the manifest's own JVM arguments, so these win where both
     * set the same property, and the instance's own arguments come last of all
     * so a player can override anything the launcher chose.
     */
    private fun jvmArguments(
        instance: Instance,
        graphicsDirectory: File,
        backend: GraphicsBackend,
        bundledLibraryDirectories: List<File>,
    ): List<String> = buildList {
        add("-Xmx" + instance.memoryMegabytes + "M")
        // LWJGL loads its own natives through System.loadLibrary before any of
        // its properties are read, so the plain library path has to name the
        // pack directory too, not only org.lwjgl.librarypath.
        //
        // And the launcher's own directory after it, because libglfw.so is
        // there and nowhere else. org.lwjgl.librarypath names a single
        // directory, so it cannot cover both; java.library.path is a list and
        // is the only place the two can be named together. Without this LWJGL
        // reaches GLFW, finds nothing, and the launch dies just after the VM
        // has started - on every version and every backend.
        val searchPath = (listOf(graphicsDirectory) + bundledLibraryDirectories)
            .map { it.absolutePath }
            .distinct()
        add("-Djava.library.path=" + searchPath.joinToString(File.pathSeparator))
        addAll(GraphicsProperties.forBackend(backend, graphicsDirectory.absolutePath))
        addAll(instance.extraJvmArguments)
    }

    private companion object {
        /** What versions before the asset index existed are keyed under. */
        const val LEGACY_ASSETS = "legacy"
    }
}
