package com.niklauncher.core.manifest

/**
 * Flattens `inheritsFrom` chains.
 *
 * Mod loaders do not restate a Minecraft version; they publish a thin
 * descriptor that names a parent and contributes its own main class, libraries
 * and arguments. Fabric, Forge and NeoForge all work this way, so getting the
 * merge precedence right here is what makes all three work rather than each
 * needing special handling.
 */
object VersionResolver {

    /** Guards against a malicious or malformed descriptor chaining forever. */
    const val MAX_DEPTH = 16

    class CircularInheritanceException(val chain: List<String>) :
        Exception("Circular version inheritance: ${chain.joinToString(" -> ")}")

    class InheritanceTooDeepException(val chain: List<String>) :
        Exception("Version inheritance deeper than $MAX_DEPTH: ${chain.joinToString(" -> ")}")

    /**
     * Loads [id] and every ancestor it inherits from, then merges them.
     *
     * [load] resolves a version id to its descriptor; it is a parameter so this
     * stays free of any I/O and remains testable.
     */
    suspend fun resolve(id: String, load: suspend (String) -> VersionJson): VersionJson {
        val chain = mutableListOf<VersionJson>()
        val seen = mutableListOf<String>()
        var currentId: String? = id

        while (currentId != null) {
            if (currentId in seen) throw CircularInheritanceException(seen + currentId)
            if (seen.size >= MAX_DEPTH) throw InheritanceTooDeepException(seen + currentId)
            seen += currentId
            val version = load(currentId)
            chain += version
            currentId = version.inheritsFrom?.takeIf { it.isNotBlank() }
        }

        return merge(chain)
    }

    /**
     * Merges a chain ordered most-derived first.
     *
     * Scalar fields take the most-derived non-null value. Libraries keep the
     * derived entries ahead of the parent's, because the classpath is searched
     * in order and a loader's rewritten classes have to win over the vanilla
     * ones. Arguments go the other way - parent first, derived appended - since
     * a later command-line flag overrides an earlier one.
     */
    fun merge(chain: List<VersionJson>): VersionJson {
        require(chain.isNotEmpty()) { "Cannot merge an empty version chain" }
        if (chain.size == 1) return chain.single()

        val derived = chain.first()
        val ancestorsFirst = chain.asReversed()

        val libraries = chain.flatMap { it.libraries }
        val gameArguments = ancestorsFirst.flatMap { it.arguments?.game.orEmpty() }
        val jvmArguments = ancestorsFirst.flatMap { it.arguments?.jvm.orEmpty() }
        val hasArguments = chain.any { it.arguments != null }

        return derived.copy(
            id = derived.id,
            // The derived descriptor states the loader's entry point; without
            // this fallback a loader that omits it would silently boot vanilla.
            mainClass = chain.firstNotNullOfOrNull { it.mainClass },
            inheritsFrom = null,
            jar = chain.firstNotNullOfOrNull { it.jar } ?: chain.last().id,
            assets = chain.firstNotNullOfOrNull { it.assets },
            minecraftArguments = chain.firstNotNullOfOrNull { it.minecraftArguments },
            arguments = if (hasArguments) Arguments(gameArguments, jvmArguments) else null,
            libraries = libraries,
            assetIndex = chain.firstNotNullOfOrNull { it.assetIndex },
            downloads = chain.firstNotNullOfOrNull { it.downloads },
            javaVersion = chain.firstNotNullOfOrNull { it.javaVersion },
            type = chain.firstOrNull { it.type != VersionType.UNKNOWN }?.type ?: derived.type,
            complianceLevel = chain.maxOf { it.complianceLevel },
            releaseTime = chain.firstNotNullOfOrNull { it.releaseTime },
            time = chain.firstNotNullOfOrNull { it.time },
        )
    }
}
