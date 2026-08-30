package com.niklauncher.core.library

import com.niklauncher.core.manifest.Library
import com.niklauncher.core.rules.LaunchEnvironment
import com.niklauncher.core.rules.RuleEvaluator

/**
 * Turns a version manifest's `libraries` block into a concrete classpath.
 *
 * Three things make this different from a desktop launcher:
 *
 *  1. Desktop native bundles are dropped. They contain `.so`/`.dll`/`.dylib`
 *     binaries built for glibc x86_64, which are useless - and, if extracted,
 *     actively harmful - on Android.
 *  2. LWJGL and its input siblings are marked runtime-provided. Minecraft's
 *     window, context and input all go through LWJGL, and on Android those
 *     calls have to land in the runtime pack's GLFW bridge rather than in the
 *     stock desktop build.
 *  3. Duplicate modules collapse to their newest version, because mod loaders
 *     routinely contribute several versions of the same library.
 */
class LibraryResolver(
    private val environment: LaunchEnvironment = LaunchEnvironment.ANDROID_ARM64,
    private val runtimeProvidedGroups: Set<String> = DEFAULT_RUNTIME_PROVIDED_GROUPS,
    private val defaultRepository: String = MOJANG_LIBRARIES,
) {

    fun resolve(libraries: List<Library>): LibraryResolution {
        val skipped = mutableListOf<SkippedLibrary>()
        val runtimeProvided = mutableListOf<MavenCoordinate>()
        // Keyed by module so a later, newer duplicate can displace an earlier one.
        val selected = LinkedHashMap<String, ResolvedLibrary>()

        for (library in libraries) {
            if (!RuleEvaluator.isAllowed(library.rules, environment)) {
                skipped += SkippedLibrary(library.name, SkipReason.RULES)
                continue
            }

            val coordinate = MavenCoordinate.parseOrNull(library.name)
            if (coordinate == null) {
                skipped += SkippedLibrary(library.name, SkipReason.MALFORMED)
                continue
            }

            if (library.natives.isNotEmpty() || coordinate.classifier?.startsWith("natives") == true) {
                skipped += SkippedLibrary(
                    library.name,
                    SkipReason.DESKTOP_NATIVE,
                    "natives are supplied by the NikLauncher runtime pack",
                )
                continue
            }

            if (coordinate.group in runtimeProvidedGroups) {
                runtimeProvided += coordinate
                continue
            }

            val artifact = library.downloads?.artifact
            val resolved = ResolvedLibrary(
                coordinate = coordinate,
                path = artifact?.path?.takeIf { it.isNotBlank() } ?: coordinate.toPath(),
                url = artifact?.url?.takeIf { it.isNotBlank() } ?: deriveUrl(library, coordinate),
                sha1 = artifact?.sha1,
                size = artifact?.size ?: 0L,
                source = LibrarySource.DOWNLOAD,
            )

            val existing = selected[coordinate.moduleKey]
            if (existing == null) {
                selected[coordinate.moduleKey] = resolved
            } else {
                val comparison = VersionComparator.compare(coordinate.version, existing.coordinate.version)
                if (comparison > 0) {
                    selected[coordinate.moduleKey] = resolved
                    skipped += SkippedLibrary(
                        existing.coordinate.toString(),
                        SkipReason.OUTDATED_DUPLICATE,
                        "superseded by ${coordinate.version}",
                    )
                } else {
                    skipped += SkippedLibrary(
                        library.name,
                        SkipReason.OUTDATED_DUPLICATE,
                        "superseded by ${existing.coordinate.version}",
                    )
                }
            }
        }

        return LibraryResolution(
            classpath = selected.values.toList(),
            runtimeProvided = runtimeProvided,
            skipped = skipped,
        )
    }

    private fun deriveUrl(library: Library, coordinate: MavenCoordinate): String {
        val base = library.url?.takeIf { it.isNotBlank() } ?: defaultRepository
        return base.trimEnd('/') + "/" + coordinate.toPath()
    }

    companion object {
        const val MOJANG_LIBRARIES = "https://libraries.minecraft.net"

        /**
         * Groups the native runtime pack owns. LWJGL is the window/input/GL
         * bridge; jinput and jutils are its desktop controller stack, which is
         * replaced by our own Android input handling.
         */
        val DEFAULT_RUNTIME_PROVIDED_GROUPS: Set<String> = setOf(
            "org.lwjgl",
            "org.lwjgl.lwjgl",
            "net.java.jinput",
            "net.java.jutils",
        )
    }
}
