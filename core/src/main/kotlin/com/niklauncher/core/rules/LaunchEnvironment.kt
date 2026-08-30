package com.niklauncher.core.rules

/**
 * The environment a version manifest is resolved against.
 *
 * Mojang's rule engine has no concept of Android, so NikLauncher presents
 * itself as Linux on arm64 - which is what the underlying runtime actually is.
 * Anything that then needs to differ from desktop Linux (the LWJGL natives,
 * above all) is handled explicitly by the library resolver rather than by
 * pretending to be a platform we are not.
 */
data class LaunchEnvironment(
    val osName: String = "linux",
    val osVersion: String = "",
    val osArch: String = "arm64",
    val features: Map<String, Boolean> = emptyMap(),
) {
    fun withFeature(name: String, enabled: Boolean): LaunchEnvironment =
        copy(features = features + (name to enabled))

    companion object {
        val ANDROID_ARM64 = LaunchEnvironment()
    }
}
