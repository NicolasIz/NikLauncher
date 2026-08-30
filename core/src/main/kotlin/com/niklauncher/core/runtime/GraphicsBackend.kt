package com.niklauncher.core.runtime

/**
 * How desktop OpenGL calls reach the device GPU.
 *
 * Minecraft speaks desktop OpenGL; Android exposes only OpenGL ES and Vulkan,
 * so every option here is a translation layer with a different trade-off.
 */
enum class GraphicsBackend(
    val id: String,
    val displayName: String,
    /** Lowest Minecraft OpenGL profile this backend can serve. */
    val minimumGlMajor: Int,
    val minimumGlMinor: Int,
    val requiresVulkan: Boolean,
) {
    /** GL 2.1 over GLES. Fastest and most battery-friendly; 1.16 and older. */
    GL4ES("gl4es", "GL4ES", 2, 1, false),

    /** GL 4.x over GLES 3.2. Lighter than Zink for 1.17+ on capable GPUs. */
    LTW("ltw", "LTW", 4, 0, false),

    /** Desktop GL over Vulkan via Mesa. Most correct, needed for shaders. */
    ZINK("zink", "Zink (Vulkan)", 4, 6, true);

    companion object {
        fun fromId(id: String): GraphicsBackend? = entries.firstOrNull { it.id == id }

        /**
         * Suggests a backend for a Minecraft version.
         *
         * Up to 1.16 the game only needs GL 2.1, where GL4ES is both faster and
         * cooler than translating through Vulkan. From 1.17 the game requires
         * core 3.2+, which needs Zink where Vulkan is available and LTW
         * otherwise.
         */
        fun recommendedFor(minecraftVersion: String, vulkanAvailable: Boolean): GraphicsBackend {
            val needsModernGl = requiresModernOpenGl(minecraftVersion)
            return when {
                !needsModernGl -> GL4ES
                vulkanAvailable -> ZINK
                else -> LTW
            }
        }

        /** True for 1.17 and newer, which moved to an OpenGL 3.2 core profile. */
        internal fun requiresModernOpenGl(minecraftVersion: String): Boolean {
            val parts = minecraftVersion.split('.')
            val major = parts.getOrNull(0)?.toIntOrNull() ?: return true
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: return true
            return major > 1 || minor >= 17
        }
    }
}
