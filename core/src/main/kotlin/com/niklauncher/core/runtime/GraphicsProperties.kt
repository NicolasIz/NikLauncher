package com.niklauncher.core.runtime

/**
 * The JVM system properties that point LWJGL at an installed runtime pack.
 *
 * Minecraft never loads a graphics library by itself: LWJGL does, and LWJGL
 * decides what to load from these properties. Getting them right is what makes
 * the difference between a chosen backend being used and being silently ignored
 * in favour of whatever the platform happens to expose.
 *
 * LWJGL resolves an OpenGL function in two tiers (org.lwjgl.opengl.GL): it first
 * looks inside the loaded library for an address-getter - glXGetProcAddress,
 * then glXGetProcAddressARB, then eglGetProcAddress, then OSMesaGetProcAddress -
 * and falls back to a plain dlsym of the function name. The two backends here
 * arrive at a working context through different tiers of that same mechanism:
 *
 *  - GL4ES exports every gl* entry point as a real symbol, so the dlsym
 *    fallback resolves them. It is built with NOX11, which leaves
 *    glXGetProcAddress out, and that is fine precisely because of the fallback.
 *
 *  - Zink is reached through Mesa's libEGL, which exports eglGetProcAddress.
 *    Mesa builds no desktop libGL for Android, so there is no library to dlsym
 *    against - the address-getter tier is the whole path, and it works because
 *    Mesa's shared glapi answers for desktop GL entry points too.
 *
 * That is why no hand-written shim is needed to expose desktop OpenGL over EGL.
 * The indirection already exists inside LWJGL.
 */
object GraphicsProperties {

    /** `org.lwjgl.opengl.GL` reads this to choose its context-management API. */
    private const val CONTEXT_API = "org.lwjgl.opengl.contextAPI"

    /** Overrides the EGL library name; consulted when [CONTEXT_API] is EGL. */
    private const val EGL_LIBRARY_NAME = "org.lwjgl.egl.libname"

    /** Overrides the OpenGL library name. */
    private const val OPENGL_LIBRARY_NAME = "org.lwjgl.opengl.libname"

    /** Where LWJGL looks for its own natives, and for anything it dlopens. */
    private const val LIBRARY_PATH = "org.lwjgl.librarypath"

    /**
     * Builds the `-D` arguments for [backend], given the directory an installed
     * pack keeps its shared objects in.
     *
     * Absolute paths are used rather than bare library names: an Android app
     * cannot rely on its data directory being on the loader search path, so
     * naming the file is the difference between loading our pack and loading
     * nothing.
     */
    fun forBackend(backend: GraphicsBackend, packLibraryDirectory: String): List<String> {
        val directory = packLibraryDirectory.trimEnd('/')
        val properties = mutableListOf("-D$LIBRARY_PATH=$directory")

        when (backend) {
            GraphicsBackend.ZINK -> {
                properties += "-D$CONTEXT_API=EGL"
                properties += "-D$EGL_LIBRARY_NAME=$directory/libEGL.so"
            }

            // Both translate to a libGL that exports its entry points directly,
            // so LWJGL's dlsym fallback is the path and no context API override
            // is wanted: asking for EGL here would send it looking for an
            // eglGetProcAddress that these libraries do not provide.
            GraphicsBackend.GL4ES, GraphicsBackend.LTW -> {
                properties += "-D$OPENGL_LIBRARY_NAME=$directory/libGL.so"
            }
        }

        return properties
    }

    /**
     * The environment variables a backend's own loader reads.
     *
     * Separate from the JVM properties above because nothing Java sees these:
     * Mesa reads them with getenv from inside its own libraries, long after
     * the VM has started. That also makes them safe to set from the launcher,
     * unlike LD_LIBRARY_PATH, which Bionic reads once when the process starts
     * and never again.
     *
     * Zink is the only backend that needs any. Mesa's loader normally picks a
     * driver by probing the kernel's DRM devices, which an app cannot reach -
     * so it has to be told the driver by name and where the driver lives, or
     * eglInitialize fails with nothing useful to say. These are Mesa's own
     * documented variables; that they are the right ones for this pack is a
     * conclusion from how the pack is built, not something confirmed on a
     * device yet.
     */
    fun environmentFor(
        backend: GraphicsBackend,
        packLibraryDirectory: String,
        shaderCacheDirectory: String,
    ): Map<String, String> {
        val directory = packLibraryDirectory.trimEnd('/')
        return when (backend) {
            GraphicsBackend.ZINK -> mapOf(
                // Where libgallium_dri.so is. Mesa searches this directory for
                // the driver rather than a system path it has no access to.
                "LIBGL_DRIVERS_PATH" to directory,
                // Which driver, by name. The pack builds zink and nothing else,
                // but "the only one present" is not how the loader chooses.
                "MESA_LOADER_DRIVER_OVERRIDE" to ZINK_DRIVER,
                "GALLIUM_DRIVER" to ZINK_DRIVER,
                // Somewhere writable for compiled shaders, named by the caller
                // rather than derived here - a path built out of "../.." would
                // be one rename away from pointing somewhere unintended.
                // Without a cache Mesa recompiles every shader on every launch,
                // which on a phone is heat as well as time.
                "MESA_SHADER_CACHE_DIR" to shaderCacheDirectory.trimEnd('/'),
            )

            GraphicsBackend.GL4ES, GraphicsBackend.LTW -> emptyMap()
        }
    }

    private const val ZINK_DRIVER = "zink"
}
