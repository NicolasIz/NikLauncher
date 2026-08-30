package com.niklauncher.core.assets

import kotlinx.serialization.Serializable

/**
 * An asset index (`assets/indexes/<id>.json`).
 *
 * [virtual] and [mapToResources] mark the pre-1.7 layouts, where the game reads
 * assets by their logical name instead of by hash, so the launcher has to
 * materialise a name-shaped tree alongside the content-addressed store.
 */
@Serializable
data class AssetIndex(
    val objects: Map<String, AssetObject> = emptyMap(),
    val virtual: Boolean = false,
    @kotlinx.serialization.SerialName("map_to_resources")
    val mapToResources: Boolean = false,
) {
    val totalSize: Long get() = objects.values.sumOf { it.size }

    /** Distinct blobs; several logical names can share one hash. */
    fun uniqueObjects(): Collection<AssetObject> = objects.values.distinctBy { it.hash }

    fun requiresNamedCopies(): Boolean = virtual || mapToResources
}

@Serializable
data class AssetObject(
    val hash: String,
    val size: Long = 0,
) {
    /** Content-addressed location: `<first two hash chars>/<hash>`. */
    val relativePath: String get() = "${hash.take(2)}/$hash"

    val downloadUrl: String get() = "$RESOURCES_BASE/$relativePath"

    companion object {
        const val RESOURCES_BASE = "https://resources.download.minecraft.net"
    }
}
