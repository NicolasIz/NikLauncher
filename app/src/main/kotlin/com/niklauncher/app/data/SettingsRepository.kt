package com.niklauncher.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.niklauncher.core.settings.LauncherSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "nik_settings")

/** Persists [LauncherSettings] in DataStore, exposed as a reactive flow. */
class SettingsRepository(private val context: Context) {

    val settings: Flow<LauncherSettings> = context.settingsDataStore.data.map { it.toSettings() }

    suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
        context.settingsDataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())
            preferences[Keys.PLAYER_NAME] = updated.playerName
            preferences[Keys.MEMORY] = updated.defaultMemoryMegabytes
            preferences[Keys.PROFILE] = updated.defaultPerformanceProfileId
            preferences[Keys.KEEP_SCREEN_ON] = updated.keepScreenOn
            preferences[Keys.SHOW_NON_RELEASE] = updated.showNonReleaseVersions
            preferences[Keys.VERBOSE_LOGGING] = updated.verboseLogging
            preferences[Keys.DOWNLOAD_CONCURRENCY] = updated.downloadConcurrency
            preferences[Keys.CONTROL_LAYOUT] = updated.activeControlLayoutId
            preferences[Keys.PACK_INDEX_URL] = updated.runtimePackIndexUrl
            updated.lastPlayedInstanceId
                ?.let { preferences[Keys.LAST_INSTANCE] = it }
                ?: preferences.remove(Keys.LAST_INSTANCE)
        }
    }

    private fun Preferences.toSettings(): LauncherSettings {
        val defaults = LauncherSettings.DEFAULT
        return LauncherSettings(
            playerName = this[Keys.PLAYER_NAME]?.takeIf { it.isNotBlank() } ?: defaults.playerName,
            defaultMemoryMegabytes = this[Keys.MEMORY] ?: defaults.defaultMemoryMegabytes,
            defaultPerformanceProfileId = this[Keys.PROFILE] ?: defaults.defaultPerformanceProfileId,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            showNonReleaseVersions = this[Keys.SHOW_NON_RELEASE] ?: defaults.showNonReleaseVersions,
            verboseLogging = this[Keys.VERBOSE_LOGGING] ?: defaults.verboseLogging,
            downloadConcurrency = this[Keys.DOWNLOAD_CONCURRENCY] ?: defaults.downloadConcurrency,
            lastPlayedInstanceId = this[Keys.LAST_INSTANCE],
            activeControlLayoutId = this[Keys.CONTROL_LAYOUT] ?: defaults.activeControlLayoutId,
            runtimePackIndexUrl = this[Keys.PACK_INDEX_URL] ?: defaults.runtimePackIndexUrl,
        )
    }

    private object Keys {
        val PLAYER_NAME = stringPreferencesKey("player_name")
        val MEMORY = intPreferencesKey("default_memory_mb")
        val PROFILE = stringPreferencesKey("default_performance_profile")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_NON_RELEASE = booleanPreferencesKey("show_non_release_versions")
        val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
        val DOWNLOAD_CONCURRENCY = intPreferencesKey("download_concurrency")
        val LAST_INSTANCE = stringPreferencesKey("last_played_instance")
        val CONTROL_LAYOUT = stringPreferencesKey("active_control_layout")
        val PACK_INDEX_URL = stringPreferencesKey("runtime_pack_index_url")
    }
}
