package com.niklauncher.core

/** Identity constants shared by every module. */
object NikLauncher {
    const val NAME = "NikLauncher"
    const val VERSION = "0.1.0"

    /** Sent as the User-Agent on every request we make. */
    const val USER_AGENT = "$NAME/$VERSION"

    const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
}
