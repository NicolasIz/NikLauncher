package com.niklauncher.core.runtime

import com.niklauncher.core.download.DownloadProgress
import java.io.File

/**
 * Supplies the parts of the stack that cannot be written in Kotlin: the ARM64
 * JVM, the OpenGL translation layer and the GLFW bridge.
 *
 * This interface is the seam that keeps NikLauncher's own code independent of
 * whichever runtime pack is installed. Packs are downloaded on first run rather
 * than bundled in the APK, so the launcher ships no third-party binaries and a
 * pack can be replaced - eventually by our own builds - without touching
 * anything above this line.
 *
 * Note for implementers: Android forbids executing binaries from an app's data
 * directory, so a pack is never launched as a `java` subprocess. The JVM is
 * loaded in-process with `dlopen()` and started through the JNI Invocation API.
 */
interface NativeRuntimeProvider {

    /** Runtime packs already installed and verified on this device. */
    suspend fun installedRuntimes(): List<InstalledRuntime>

    /** Graphics backends this device and the installed packs can actually run. */
    suspend fun availableBackends(): List<GraphicsBackend>

    /**
     * Ensures [runtime] is installed, downloading it if needed.
     * Implementations must verify the pack before reporting success.
     */
    suspend fun ensureInstalled(
        runtime: JavaRuntime,
        onProgress: ((DownloadProgress) -> Unit)? = null,
    ): InstalledRuntime

    /**
     * The pack an installed runtime came from, or null when none is installed.
     *
     * The launcher needs this and not just [InstalledRuntime] because the pack
     * is what declares its own layout - where each graphics backend's shared
     * objects live. Re-deriving that at the call site is how the layout the
     * pack was built with and the paths the launcher passes drift apart.
     */
    suspend fun installedPack(runtime: JavaRuntime): RuntimePack?

    suspend fun remove(runtime: JavaRuntime)
}

/** A verified, ready-to-load runtime pack. */
data class InstalledRuntime(
    val runtime: JavaRuntime,
    /** Root of the pack; the JAVA_HOME equivalent. */
    val home: File,
    /** The `libjvm.so` this pack exposes for the JNI Invocation API. */
    val libjvm: File,
    val version: String,
    val architecture: String,
    /** LWJGL/GLFW jars the pack supplies, replacing the desktop ones. */
    val providedClasspath: List<File> = emptyList(),
)

class RuntimeNotInstalledException(val runtime: JavaRuntime) :
    Exception("${runtime.displayName} runtime is not installed")

/**
 * Stand-in used until Phase 2 lands the real pack installer.
 *
 * It deliberately reports an empty state and fails loudly rather than
 * pretending, so the UI shows what is genuinely available instead of offering a
 * Play button that could not work.
 */
class UnavailableRuntimeProvider : NativeRuntimeProvider {
    override suspend fun installedRuntimes(): List<InstalledRuntime> = emptyList()

    override suspend fun availableBackends(): List<GraphicsBackend> = emptyList()

    override suspend fun ensureInstalled(
        runtime: JavaRuntime,
        onProgress: ((DownloadProgress) -> Unit)?,
    ): InstalledRuntime = throw RuntimeNotInstalledException(runtime)

    override suspend fun installedPack(runtime: JavaRuntime): RuntimePack? = null

    override suspend fun remove(runtime: JavaRuntime) = Unit
}
