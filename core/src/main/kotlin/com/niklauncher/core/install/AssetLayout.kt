package com.niklauncher.core.install

import com.niklauncher.core.assets.AssetIndex
import com.niklauncher.core.io.GamePaths
import java.io.File

/**
 * Materialises the name-shaped asset trees that pre-1.7 versions expect.
 *
 * Modern Minecraft reads assets by hash straight out of `assets/objects`. Older
 * versions instead open them by their logical name, so those installs need a
 * second tree where the files appear under their real paths. This is why a 1.6
 * or 1.5 install is not simply "download the objects and go".
 */
object AssetLayout {

    data class Materialisation(
        val targetDirectory: File,
        val filesLinked: Int,
        val filesMissing: Int,
    )

    /**
     * Builds the named tree for [index] if it needs one.
     *
     * `virtual` indexes go to `assets/virtual/<id>`; `map_to_resources` indexes
     * (1.5 and older) go to the instance's own `resources` directory, which is
     * where that generation of the game looks.
     */
    fun materialise(
        index: AssetIndex,
        indexId: String,
        paths: GamePaths,
        gameDirectory: File,
    ): Materialisation? {
        if (!index.requiresNamedCopies()) return null

        val target = if (index.mapToResources) {
            File(gameDirectory, "resources")
        } else {
            File(File(paths.assets, "virtual"), indexId)
        }

        var linked = 0
        var missing = 0

        for ((name, asset) in index.objects) {
            val source = paths.assetObject(asset.hash)
            if (!source.isFile) {
                missing++
                continue
            }
            val destination = File(target, name)
            // Skip files already in place; a full re-copy of a legacy index is
            // thousands of small writes on a phone's flash.
            if (destination.isFile && destination.length() == source.length()) {
                linked++
                continue
            }
            destination.parentFile?.mkdirs()
            source.copyTo(destination, overwrite = true)
            linked++
        }

        return Materialisation(target, linked, missing)
    }
}
