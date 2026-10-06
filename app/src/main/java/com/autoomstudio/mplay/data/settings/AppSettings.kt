package com.autoomstudio.mplay.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.metronome.MetronomeSettings
import com.autoomstudio.mplay.separation.SeparationEstimate
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

data class SeparationSettings(
    val chargingOnly: Boolean = false,
    val pauseOnLowBattery: Boolean = true,
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

    /** The last duration picked in the sleep timer sheet, suggested next time; null until one is picked. */
    val lastSleepMinutes: Flow<Int?> = dataStore.data.map { it[KEY_LAST_SLEEP_MINUTES] }.distinctUntilChanged()

    suspend fun setLastSleepMinutes(minutes: Int) {
        dataStore.edit { it[KEY_LAST_SLEEP_MINUTES] = minutes }
    }

    val lofiEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LOFI_ENABLED] ?: false }.distinctUntilChanged()

    suspend fun setLofiEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LOFI_ENABLED] = enabled }
    }

    val hideDuplicates: Flow<Boolean> = dataStore.data.map { it[KEY_HIDE_DUPLICATES] ?: true }.distinctUntilChanged()

    suspend fun setHideDuplicates(enabled: Boolean) {
        dataStore.edit { it[KEY_HIDE_DUPLICATES] = enabled }
    }

    val stemMode: Flow<StemMode> =
        dataStore.data.map { StemMode.fromName(it[KEY_STEM_MODE]) }.distinctUntilChanged()

    suspend fun setStemMode(mode: StemMode) {
        dataStore.edit { it[KEY_STEM_MODE] = mode.name }
    }

    val separationSettings: Flow<SeparationSettings> = dataStore.data
        .map { prefs ->
            val defaults = SeparationSettings()
            SeparationSettings(
                chargingOnly = prefs[KEY_SEPARATION_CHARGING_ONLY] ?: defaults.chargingOnly,
                pauseOnLowBattery = prefs[KEY_SEPARATION_PAUSE_LOW_BATTERY] ?: defaults.pauseOnLowBattery,
            )
        }
        .distinctUntilChanged()

    suspend fun setSeparationChargingOnly(enabled: Boolean) {
        dataStore.edit { it[KEY_SEPARATION_CHARGING_ONLY] = enabled }
    }

    suspend fun setSeparationPauseOnLowBattery(enabled: Boolean) {
        dataStore.edit { it[KEY_SEPARATION_PAUSE_LOW_BATTERY] = enabled }
    }

    /** The time notice is shown before every separation until the user picks "Don't show again". */
    suspend fun separationNoticeHidden(): Boolean = dataStore.data.first()[KEY_SEPARATION_NOTICE_HIDDEN] ?: false

    suspend fun setSeparationNoticeHidden() {
        dataStore.edit { it[KEY_SEPARATION_NOTICE_HIDDEN] = true }
    }

    /** Rolling average of processing seconds per second of audio on this phone; null before the first song. */
    suspend fun separationSpeedFactor(): Double? =
        dataStore.data.first()[KEY_SEPARATION_SPEED_FACTOR]?.toDouble()

    suspend fun recordSeparationSpeed(measured: Double) {
        dataStore.edit { prefs ->
            val updated = SeparationEstimate.updatedFactor(prefs[KEY_SEPARATION_SPEED_FACTOR]?.toDouble(), measured)
            if (updated != null) prefs[KEY_SEPARATION_SPEED_FACTOR] = updated.toFloat()
        }
    }

    /** The last metronome setup, restored on the next launch (MT17). */
    val metronomeSettings: Flow<MetronomeSettings> = dataStore.data
        .map { prefs ->
            MetronomeSettings.of(
                bpm = prefs[KEY_METRONOME_BPM],
                beatsPerBar = prefs[KEY_METRONOME_BEATS],
                beatUnit = prefs[KEY_METRONOME_UNIT],
                accent = prefs[KEY_METRONOME_ACCENT],
                sound = prefs[KEY_METRONOME_SOUND],
                volume = prefs[KEY_METRONOME_VOLUME],
            )
        }
        .distinctUntilChanged()

    suspend fun setMetronomeSettings(settings: MetronomeSettings) {
        dataStore.edit {
            it[KEY_METRONOME_BPM] = settings.bpm
            it[KEY_METRONOME_BEATS] = settings.beatsPerBar
            it[KEY_METRONOME_UNIT] = settings.beatUnit
            it[KEY_METRONOME_ACCENT] = settings.accent
            it[KEY_METRONOME_SOUND] = settings.sound.name
            it[KEY_METRONOME_VOLUME] = settings.volume
        }
    }

    /** The personal-use note shows in the sing-along sheet until the first recording starts (SA19). */
    val singAlongNoteSeen: Flow<Boolean> = dataStore.data
        .map { it[KEY_SINGALONG_NOTE_SEEN] ?: false }
        .distinctUntilChanged()

    suspend fun setSingAlongNoteSeen() {
        dataStore.edit { it[KEY_SINGALONG_NOTE_SEEN] = true }
    }

    private companion object {
        val KEY_SINGALONG_NOTE_SEEN = booleanPreferencesKey("singalong_note_seen")
        val KEY_METRONOME_BPM = intPreferencesKey("metronome_bpm")
        val KEY_METRONOME_BEATS = intPreferencesKey("metronome_beats")
        val KEY_METRONOME_UNIT = intPreferencesKey("metronome_unit")
        val KEY_METRONOME_ACCENT = booleanPreferencesKey("metronome_accent")
        val KEY_METRONOME_SOUND = stringPreferencesKey("metronome_sound")
        val KEY_METRONOME_VOLUME = floatPreferencesKey("metronome_volume")
        val KEY_STEM_MODE = stringPreferencesKey("stem_mode")
        val KEY_SEPARATION_CHARGING_ONLY = booleanPreferencesKey("separation_charging_only")
        val KEY_SEPARATION_PAUSE_LOW_BATTERY = booleanPreferencesKey("separation_pause_low_battery")
        val KEY_SEPARATION_NOTICE_HIDDEN = booleanPreferencesKey("separation_notice_hidden")
        val KEY_SEPARATION_SPEED_FACTOR = floatPreferencesKey("separation_speed_factor")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_NOTIFICATION_PROMPT_SHOWN = booleanPreferencesKey("notification_prompt_shown")
        val KEY_LAST_SLEEP_MINUTES = intPreferencesKey("last_sleep_minutes")
        val KEY_LOFI_ENABLED = booleanPreferencesKey("lofi_enabled")
        val KEY_HIDE_DUPLICATES = booleanPreferencesKey("hide_duplicates")
    }
}
