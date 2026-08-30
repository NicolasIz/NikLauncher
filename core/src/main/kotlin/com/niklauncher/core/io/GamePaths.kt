package com.niklauncher.core.io

import java.io.File

/**
 * The on-disk layout NikLauncher manages.
 *
 * Shared, content-addressed data (versions, libraries, assets, runtimes) lives
 * once under [root] and is reused by every instance; only per-instance state
 * lives under `instances/<id>/`. That split keeps a second install of the same
 * Minecraft version nearly free in storage, which matters on a phone.
 */
class GamePaths(val root: File) {

    val versions: File get() = File(root, "versions")
    val libraries: File get() = File(root, "libraries")
    val assets: File get() = File(root, "assets")
    val assetIndexes: File get() = File(assets, "indexes")
    val assetObjects: File get() = File(assets, "objects")
    val runtimes: File get() = File(root, "runtimes")
    val instances: File get() = File(root, "instances")
    val logs: File get() = File(root, "logs")
    val cache: File get() = File(root, "cache")

    fun versionDirectory(versionId: String): File = File(versions, versionId)

    fun versionJson(versionId: String): File = File(versionDirectory(versionId), "$versionId.json")

    fun versionJar(versionId: String): File = File(versionDirectory(versionId), "$versionId.jar")

    fun library(relativePath: String): File = File(libraries, relativePath)

    fun assetIndex(indexId: String): File = File(assetIndexes, "$indexId.json")

    /** Assets are content-addressed: `objects/<first two hash chars>/<hash>`. */
    fun assetObject(hash: String): File = File(File(assetObjects, hash.take(2)), hash)

    fun runtime(component: String): File = File(runtimes, component)

    fun instance(instanceId: String): File = File(instances, instanceId)

    /** The `.minecraft` working directory a given instance runs in. */
    fun instanceGameDirectory(instanceId: String): File = File(instance(instanceId), "minecraft")

    fun createDirectories() {
        listOf(versions, libraries, assetIndexes, assetObjects, runtimes, instances, logs, cache)
            .forEach { it.mkdirs() }
    }
}
