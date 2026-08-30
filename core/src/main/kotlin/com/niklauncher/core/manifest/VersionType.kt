package com.niklauncher.core.manifest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VersionType {
    @SerialName("release")
    RELEASE,

    @SerialName("snapshot")
    SNAPSHOT,

    @SerialName("old_beta")
    OLD_BETA,

    @SerialName("old_alpha")
    OLD_ALPHA,

    @SerialName("unknown")
    UNKNOWN,
}
