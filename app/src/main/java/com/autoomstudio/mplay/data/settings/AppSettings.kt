package com.autoomstudio.mplay.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Light, Dark }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    /** Wallpaper-based colors on Android 12+; off by default so the MPlay palette is the baseline. */
    val dynamicColor: Boolean = false,
)

/** Unknown or missing stored values fall back to the defaults instead of failing. */
fun themeSettingsOf(storedMode: String?, storedDynamicColor: Boolean?): ThemeSettings {
    val defaults = ThemeSettings()
    return ThemeSettings(
        mode = ThemeMode.entries.firstOrNull { it.name == storedMode } ?: defaults.mode,
        dynamicColor = storedDynamicColor ?: defaults.dynamicColor,
    )
}

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettings(context: Context) {

    private val dataStore = context.applicationContext.appSettingsDataStore

    val theme: Flow<ThemeSettings> = dataStore.data
        .map { prefs -> themeSettingsOf(prefs[KEY_THEME_MODE], prefs[KEY_DYNAMIC_COLOR]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    /** The notification permission is asked for once; after that the user manages it in system settings. */
    suspend fun notificationPromptShown(): Boolean = dataStore.data.first()[KEY_NOTIFICATION_PROMPT_SHOWN] ?: false

    suspend fun setNotificationPromptShown() {
        dataStore.edit { it[KEY_NOTIFICATION_PROMPT_SHOWN] = true }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
    }
}
