package com.niklauncher.core.launch

import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Who a session runs as.
 *
 * Kept separate from the Microsoft sign-in that will produce it, so the launch
 * path can be built and tested before that exists - and so an offline session
 * stays possible afterwards, which is the only way to start the game on a
 * device with no account attached.
 */
data class LaunchAccount(
    val playerName: String,
    val uuid: String,
    val accessToken: String,
    val userType: String,
) {
    companion object {

        /**
         * A session with no Mojang account behind it.
         *
         * Good enough for singleplayer, and refused by any server running with
         * online-mode on - which is the intended behaviour, not a limitation to
         * work around.
         */
        fun offline(playerName: String): LaunchAccount = LaunchAccount(
            playerName = playerName,
            uuid = offlineUuid(playerName),
            accessToken = "0",
            userType = "legacy",
        )

        /**
         * The same derivation the vanilla server uses for an offline player, so
         * a world created here keeps its player data if the same name is used
         * again elsewhere.
         */
        internal fun offlineUuid(playerName: String): String =
            UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).toByteArray(StandardCharsets.UTF_8))
                .toString()
    }
}
