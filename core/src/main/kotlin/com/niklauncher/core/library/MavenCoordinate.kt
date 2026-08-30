package com.niklauncher.core.library

/**
 * A Maven coordinate in the `group:artifact:version[:classifier][@extension]`
 * form used throughout Minecraft and mod-loader manifests.
 */
data class MavenCoordinate(
    val group: String,
    val artifact: String,
    val version: String,
    val classifier: String? = null,
    val extension: String = "jar",
) {
    /** Key identifying the artifact irrespective of version, for de-duplication. */
    val moduleKey: String get() = "$group:$artifact" + (classifier?.let { ":$it" } ?: "")

    /** Repository-relative path, e.g. `net/fabricmc/tiny-remapper/0.8.2/tiny-remapper-0.8.2.jar`. */
    fun toPath(): String {
        val groupPath = group.replace('.', '/')
        val suffix = classifier?.let { "-$it" } ?: ""
        return "$groupPath/$artifact/$version/$artifact-$version$suffix.$extension"
    }

    override fun toString(): String = buildString {
        append(group).append(':').append(artifact).append(':').append(version)
        classifier?.let { append(':').append(it) }
        if (extension != "jar") append('@').append(extension)
    }

    companion object {
        /**
         * Parses a coordinate, returning null when the string is not a valid
         * one. Callers decide whether a malformed entry is fatal; the resolver
         * skips it rather than aborting an otherwise usable install.
         */
        fun parseOrNull(raw: String): MavenCoordinate? {
            if (raw.isBlank()) return null
            val atIndex = raw.indexOf('@')
            val extension = if (atIndex >= 0) raw.substring(atIndex + 1) else "jar"
            val body = if (atIndex >= 0) raw.substring(0, atIndex) else raw
            val parts = body.split(':')
            if (parts.size < 3) return null
            if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) return null
            if (extension.isBlank()) return null
            return MavenCoordinate(
                group = parts[0],
                artifact = parts[1],
                version = parts[2],
                classifier = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                extension = extension,
            )
        }

        fun parse(raw: String): MavenCoordinate =
            parseOrNull(raw) ?: throw IllegalArgumentException("Malformed Maven coordinate: '$raw'")
    }
}
