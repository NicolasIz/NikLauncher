package com.niklauncher.core.manifest

import kotlinx.serialization.json.Json

/**
 * Shared JSON reader for Mojang and mod-loader metadata.
 *
 * `ignoreUnknownKeys` is essential rather than lax here: the manifests carry
 * fields we deliberately do not model, and new ones appear with new releases.
 * Failing to parse a version because Mojang added a key would break the
 * launcher for every user at once.
 */
object ManifestCodec {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    fun decodeManifest(text: String): VersionManifest = json.decodeFromString(text)

    fun decodeVersion(text: String): VersionJson = json.decodeFromString(text)
}
