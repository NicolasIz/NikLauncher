package com.niklauncher.core.runtime

/**
 * The Java runtimes NikLauncher can install.
 *
 * Minecraft states the major release it needs in its manifest, and the mapping
 * is not optional: 1.16 and older will not start on 17+, and 1.18+ will not
 * start on 8.
 */
enum class JavaRuntime(
    val majorVersion: Int,
    val id: String,
    val displayName: String,
) {
    JRE_8(8, "jre8", "Java 8"),
    JRE_17(17, "jre17", "Java 17"),
    JRE_21(21, "jre21", "Java 21");

    companion object {
        /**
         * Picks a runtime for a manifest's declared major version: an exact
         * match when we have one, otherwise the oldest runtime new enough to
         * satisfy it, so a future Minecraft asking for a release we do not know
         * about still gets our newest rather than failing outright.
         */
        fun forMajorVersion(majorVersion: Int): JavaRuntime =
            entries.firstOrNull { it.majorVersion == majorVersion }
                ?: entries.filter { it.majorVersion >= majorVersion }.minByOrNull { it.majorVersion }
                ?: entries.maxBy { it.majorVersion }

        fun fromId(id: String): JavaRuntime? = entries.firstOrNull { it.id == id }
    }
}
