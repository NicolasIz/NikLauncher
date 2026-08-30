package com.niklauncher.core.launch

/**
 * Everything the placeholders in a version manifest can refer to.
 *
 * Mojang's argument templates are literal `${name}` substitutions; this type is
 * the substitution table, kept explicit so a missing value is a compile error
 * rather than a placeholder leaking into the command line.
 */
data class LaunchContext(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa",
    val xuid: String = "",
    val clientId: String = "",
    val versionName: String,
    val versionType: String,
    val gameDirectory: String,
    val assetsRoot: String,
    val assetsIndexName: String,
    val librariesDirectory: String,
    val nativesDirectory: String,
    val classpath: List<String>,
    val resolutionWidth: Int,
    val resolutionHeight: Int,
    val launcherName: String = "NikLauncher",
    val launcherVersion: String,
    val classpathSeparator: String = ":",
    /** Feature flags gating conditional arguments, e.g. `has_custom_resolution`. */
    val features: Map<String, Boolean> = emptyMap(),
) {
    fun substitutions(): Map<String, String> = mapOf(
        "auth_player_name" to playerName,
        "auth_uuid" to uuid,
        "auth_access_token" to accessToken,
        "auth_session" to "token:$accessToken:$uuid",
        "auth_xuid" to xuid,
        "clientid" to clientId,
        "user_type" to userType,
        "user_properties" to "{}",
        "version_name" to versionName,
        "version_type" to versionType,
        "game_directory" to gameDirectory,
        "assets_root" to assetsRoot,
        "game_assets" to assetsRoot,
        "assets_index_name" to assetsIndexName,
        "library_directory" to librariesDirectory,
        "natives_directory" to nativesDirectory,
        "classpath" to classpath.joinToString(classpathSeparator),
        "classpath_separator" to classpathSeparator,
        "launcher_name" to launcherName,
        "launcher_version" to launcherVersion,
        "resolution_width" to resolutionWidth.toString(),
        "resolution_height" to resolutionHeight.toString(),
    )
}
