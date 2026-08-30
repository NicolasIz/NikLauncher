package com.niklauncher.core.runtime

/**
 * What the device can actually do, as far as the renderer is concerned.
 */
data class GraphicsCapabilities(
    val vulkanVersion: Int,
    val glesMajor: Int,
    val glesMinor: Int,
    val gpuName: String = "",
) {
    val supportsVulkan: Boolean get() = vulkanVersion > 0

    /** Zink needs a solid Vulkan 1.1+ driver; below that it is not worth trying. */
    val supportsZink: Boolean get() = vulkanVersion >= VULKAN_1_1

    /** LTW translates modern GL onto GLES 3.2. */
    val supportsLtw: Boolean get() = glesMajor > 3 || (glesMajor == 3 && glesMinor >= 2)

    companion object {
        /** `VK_MAKE_VERSION(1, 1, 0)` as Android's feature version reports it. */
        const val VULKAN_1_1 = 0x00401000
    }
}

data class BackendChoice(
    val backend: GraphicsBackend,
    val reason: String,
    /** False when nothing suitable exists and this is a last resort. */
    val confident: Boolean = true,
)

/**
 * Picks the graphics backend for a given Minecraft version and device.
 *
 * The rule that matters: up to 1.16 Minecraft only needs OpenGL 2.1, and GL4ES
 * serves that far more cheaply than translating through Vulkan. Reaching for
 * Zink there would burn battery and heat for no visual gain, which is the wrong
 * trade for this project. From 1.17 the game needs a core 3.2+ profile, and
 * then Zink is the correct answer wherever Vulkan is good enough.
 */
object GraphicsBackendSelector {

    fun select(
        minecraftVersion: String,
        capabilities: GraphicsCapabilities,
        userOverride: GraphicsBackend? = null,
        installedBackends: Set<GraphicsBackend> = GraphicsBackend.entries.toSet(),
    ): BackendChoice {
        userOverride?.let { override ->
            return BackendChoice(
                backend = override,
                reason = "Elegido manualmente",
                confident = isUsable(override, capabilities) && override in installedBackends,
            )
        }

        val needsModernGl = GraphicsBackend.requiresModernOpenGl(minecraftVersion)
        val candidates = if (needsModernGl) {
            listOf(GraphicsBackend.ZINK, GraphicsBackend.LTW)
        } else {
            listOf(GraphicsBackend.GL4ES, GraphicsBackend.ZINK, GraphicsBackend.LTW)
        }

        val choice = candidates.firstOrNull { it in installedBackends && isUsable(it, capabilities) }
        if (choice != null) {
            return BackendChoice(choice, explain(choice, minecraftVersion, needsModernGl))
        }

        // Nothing installed fits. Name something usable so the caller can offer
        // to install it, rather than silently returning nothing.
        val fallback = candidates.firstOrNull { isUsable(it, capabilities) } ?: GraphicsBackend.GL4ES
        return BackendChoice(
            backend = fallback,
            reason = "Ningún backend compatible está instalado todavía",
            confident = false,
        )
    }

    fun isUsable(backend: GraphicsBackend, capabilities: GraphicsCapabilities): Boolean = when (backend) {
        GraphicsBackend.GL4ES -> capabilities.glesMajor >= 2
        GraphicsBackend.LTW -> capabilities.supportsLtw
        GraphicsBackend.ZINK -> capabilities.supportsZink
    }

    private fun explain(
        backend: GraphicsBackend,
        minecraftVersion: String,
        needsModernGl: Boolean,
    ): String = when (backend) {
        GraphicsBackend.GL4ES ->
            "$minecraftVersion solo necesita OpenGL 2.1; GL4ES es la ruta más eficiente y la que menos calienta"
        GraphicsBackend.ZINK ->
            if (needsModernGl) {
                "$minecraftVersion requiere OpenGL 3.2+; Zink sobre Vulkan es la opción más correcta"
            } else {
                "Zink sobre Vulkan, al no haber una ruta GLES disponible"
            }
        GraphicsBackend.LTW ->
            "$minecraftVersion requiere OpenGL 3.2+ y este dispositivo no tiene Vulkan suficiente para Zink"
    }
}
