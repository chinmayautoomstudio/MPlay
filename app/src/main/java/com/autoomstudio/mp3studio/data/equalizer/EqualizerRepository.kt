package com.autoomstudio.mp3studio.data.equalizer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.autoomstudio.mp3studio.playback.equalizer.BuiltInPreset
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerBands
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerPresetIds
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerState
import com.autoomstudio.mp3studio.playback.equalizer.SavedEqualizerPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.equalizerDataStore: DataStore<Preferences> by preferencesDataStore(name = "equalizer_settings")

/**
 * Single source of truth for equalizer state. Persists to DataStore; UI and [PlaybackService]
 * both observe [state].
 */
class EqualizerRepository(
    context: Context,
    scope: CoroutineScope,
) {
    private val dataStore = context.applicationContext.equalizerDataStore

    val state: StateFlow<EqualizerState> = dataStore.data
        .map { prefs -> decode(prefs) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, EqualizerState.Default)

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setBandGain(index: Int, gainDb: Float) {
        if (index !in 0 until EqualizerBands.COUNT) return
        val clamped = EqualizerBands.clampGain(gainDb)
        dataStore.edit { prefs ->
            val current = decode(prefs)
            val gains = current.gainsDb.copyOf().also { it[index] = clamped }
            prefs[KEY_GAINS] = encodeGains(gains)
            prefs[KEY_CUSTOM_GAINS] = encodeGains(gains)
            prefs[KEY_PRESET_ID] = EqualizerPresetIds.CUSTOM
        }
    }

    suspend fun setGains(gainsDb: FloatArray, asCustom: Boolean) {
        val gains = EqualizerBands.copyGains(gainsDb)
        dataStore.edit { prefs ->
            prefs[KEY_GAINS] = encodeGains(gains)
            if (asCustom) {
                prefs[KEY_CUSTOM_GAINS] = encodeGains(gains)
                prefs[KEY_PRESET_ID] = EqualizerPresetIds.CUSTOM
            }
        }
    }

    suspend fun setPreamp(preampDb: Float) {
        val clamped = EqualizerBands.clampGain(preampDb)
        dataStore.edit { prefs ->
            prefs[KEY_PREAMP] = clamped
            val current = decode(prefs)
            // Manual preamp edit keeps the preset tile but stores custom curve for bands only;
            // PRD: moving any slider switches to Custom — treat preamp as a slider.
            prefs[KEY_CUSTOM_GAINS] = encodeGains(current.gainsDb)
            prefs[KEY_PRESET_ID] = EqualizerPresetIds.CUSTOM
        }
    }

    suspend fun setBassBoost(enabled: Boolean) {
        dataStore.edit { it[KEY_BASS_BOOST] = enabled }
    }

    suspend fun setVirtualizer(enabled: Boolean) {
        dataStore.edit { it[KEY_VIRTUALIZER] = enabled }
    }

    suspend fun setLoudness(enabled: Boolean) {
        dataStore.edit { it[KEY_LOUDNESS] = enabled }
    }

    suspend fun selectBuiltIn(preset: BuiltInPreset) {
        dataStore.edit { prefs ->
            prefs[KEY_PRESET_ID] = preset.id
            prefs[KEY_GAINS] = encodeGains(preset.gainsDb)
        }
    }

    suspend fun selectCustom() {
        dataStore.edit { prefs ->
            val custom = decodeGains(prefs[KEY_CUSTOM_GAINS])
            prefs[KEY_PRESET_ID] = EqualizerPresetIds.CUSTOM
            prefs[KEY_GAINS] = encodeGains(custom)
        }
    }

    suspend fun selectSaved(name: String) {
        dataStore.edit { prefs ->
            val saved = decodeSaved(prefs[KEY_SAVED_PRESETS]).firstOrNull { it.name == name } ?: return@edit
            prefs[KEY_PRESET_ID] = saved.id
            prefs[KEY_GAINS] = encodeGains(saved.gainsDb)
            prefs[KEY_PREAMP] = EqualizerBands.clampGain(saved.preampDb)
        }
    }

    suspend fun reset() {
        dataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
            prefs[KEY_PRESET_ID] = BuiltInPreset.Normal.id
            prefs[KEY_GAINS] = encodeGains(EqualizerBands.flatGains())
            prefs[KEY_CUSTOM_GAINS] = encodeGains(EqualizerBands.flatGains())
            prefs[KEY_PREAMP] = 0f
            prefs[KEY_BASS_BOOST] = false
            prefs[KEY_VIRTUALIZER] = false
            prefs[KEY_LOUDNESS] = false
        }
    }

    /**
     * Saves the current bands + preamp under [name]. Returns an error message, or null on success.
     */
    suspend fun saveCurrentAsPreset(name: String): String? {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> return "Name cannot be blank"
            trimmed.length > MAX_NAME_LENGTH -> return "Name must be at most $MAX_NAME_LENGTH characters"
            BuiltInPreset.entries.any { it.displayName.equals(trimmed, ignoreCase = true) } ->
                return "Name is reserved"
            trimmed.equals("Custom", ignoreCase = true) -> return "Name is reserved"
        }
        var error: String? = null
        dataStore.edit { prefs ->
            val current = decode(prefs)
            val existing = current.savedPresets.toMutableList()
            if (existing.any { it.name.equals(trimmed, ignoreCase = true) }) {
                error = "Name already used"
                return@edit
            }
            if (existing.size >= MAX_SAVED) {
                error = "At most $MAX_SAVED saved presets"
                return@edit
            }
            existing.add(
                SavedEqualizerPreset(
                    name = trimmed,
                    gainsDb = EqualizerBands.copyGains(current.gainsDb),
                    preampDb = EqualizerBands.clampGain(current.preampDb),
                ),
            )
            prefs[KEY_SAVED_PRESETS] = encodeSaved(existing)
            prefs[KEY_PRESET_ID] = EqualizerPresetIds.userId(trimmed)
        }
        return error
    }

    suspend fun renameSavedPreset(oldName: String, newName: String): String? {
        val trimmed = newName.trim()
        when {
            trimmed.isEmpty() -> return "Name cannot be blank"
            trimmed.length > MAX_NAME_LENGTH -> return "Name must be at most $MAX_NAME_LENGTH characters"
            BuiltInPreset.entries.any { it.displayName.equals(trimmed, ignoreCase = true) } ->
                return "Name is reserved"
            trimmed.equals("Custom", ignoreCase = true) -> return "Name is reserved"
        }
        var error: String? = null
        dataStore.edit { prefs ->
            val list = decodeSaved(prefs[KEY_SAVED_PRESETS]).toMutableList()
            val index = list.indexOfFirst { it.name == oldName }
            if (index < 0) {
                error = "Preset not found"
                return@edit
            }
            if (list.any { it.name.equals(trimmed, ignoreCase = true) && it.name != oldName }) {
                error = "Name already used"
                return@edit
            }
            val old = list[index]
            list[index] = old.copy(name = trimmed)
            prefs[KEY_SAVED_PRESETS] = encodeSaved(list)
            if (prefs[KEY_PRESET_ID] == EqualizerPresetIds.userId(oldName)) {
                prefs[KEY_PRESET_ID] = EqualizerPresetIds.userId(trimmed)
            }
        }
        return error
    }

    suspend fun deleteSavedPreset(name: String) {
        dataStore.edit { prefs ->
            val list = decodeSaved(prefs[KEY_SAVED_PRESETS]).filterNot { it.name == name }
            prefs[KEY_SAVED_PRESETS] = encodeSaved(list)
            if (prefs[KEY_PRESET_ID] == EqualizerPresetIds.userId(name)) {
                prefs[KEY_PRESET_ID] = EqualizerPresetIds.CUSTOM
                prefs[KEY_GAINS] = prefs[KEY_CUSTOM_GAINS] ?: encodeGains(EqualizerBands.flatGains())
            }
        }
    }

    private fun decode(prefs: Preferences): EqualizerState {
        return try {
            val gains = decodeGains(prefs[KEY_GAINS])
            val custom = decodeGains(prefs[KEY_CUSTOM_GAINS] ?: prefs[KEY_GAINS])
            val presetId = prefs[KEY_PRESET_ID] ?: BuiltInPreset.Normal.id
            val normalizedPreset = normalizePresetId(presetId, prefs)
            EqualizerState(
                enabled = prefs[KEY_ENABLED] ?: false,
                gainsDb = gains,
                presetId = normalizedPreset,
                customGainsDb = custom,
                preampDb = EqualizerBands.clampGain(prefs[KEY_PREAMP] ?: 0f),
                bassBoost = prefs[KEY_BASS_BOOST] ?: false,
                virtualizer = prefs[KEY_VIRTUALIZER] ?: false,
                loudness = prefs[KEY_LOUDNESS] ?: false,
                savedPresets = decodeSaved(prefs[KEY_SAVED_PRESETS]),
            )
        } catch (_: Exception) {
            EqualizerState.Default
        }
    }

    private fun normalizePresetId(presetId: String, prefs: Preferences): String {
        if (presetId == EqualizerPresetIds.CUSTOM) return presetId
        if (BuiltInPreset.fromId(presetId) != null) return presetId
        if (EqualizerPresetIds.isUser(presetId)) {
            val name = EqualizerPresetIds.userName(presetId)
            val saved = decodeSaved(prefs[KEY_SAVED_PRESETS])
            if (saved.any { it.name == name }) return presetId
        }
        return BuiltInPreset.Normal.id
    }

    companion object {
        const val MAX_SAVED = 10
        const val MAX_NAME_LENGTH = 24

        val KEY_ENABLED = booleanPreferencesKey("eq_enabled")
        val KEY_GAINS = stringPreferencesKey("eq_gains")
        val KEY_CUSTOM_GAINS = stringPreferencesKey("eq_custom_gains")
        val KEY_PRESET_ID = stringPreferencesKey("eq_preset_id")
        val KEY_PREAMP = floatPreferencesKey("eq_preamp")
        val KEY_BASS_BOOST = booleanPreferencesKey("eq_bass_boost")
        val KEY_VIRTUALIZER = booleanPreferencesKey("eq_virtualizer")
        val KEY_LOUDNESS = booleanPreferencesKey("eq_loudness")
        val KEY_SAVED_PRESETS = stringPreferencesKey("eq_saved_presets")

        fun encodeGains(gains: FloatArray): String =
            EqualizerBands.copyGains(gains).joinToString(",")

        fun decodeGains(encoded: String?): FloatArray {
            if (encoded.isNullOrBlank()) return EqualizerBands.flatGains()
            val parts = encoded.split(',')
            if (parts.size != EqualizerBands.COUNT) return EqualizerBands.flatGains()
            return FloatArray(EqualizerBands.COUNT) { i ->
                EqualizerBands.clampGain(parts[i].toFloatOrNull() ?: 0f)
            }
        }

        /** Compact line format: name|gains|preamp, records separated by newlines. */
        fun encodeSaved(presets: List<SavedEqualizerPreset>): String =
            presets.take(MAX_SAVED).joinToString("\n") { preset ->
                val safeName = preset.name.replace('|', '/').replace('\n', ' ').trim()
                "$safeName|${encodeGains(preset.gainsDb)}|${preset.preampDb}"
            }

        fun decodeSaved(encoded: String?): List<SavedEqualizerPreset> {
            if (encoded.isNullOrBlank()) return emptyList()
            return try {
                encoded.lineSequence()
                    .mapNotNull { line ->
                        val parts = line.split('|')
                        if (parts.size < 3) return@mapNotNull null
                        val name = parts[0].trim()
                        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) return@mapNotNull null
                        SavedEqualizerPreset(
                            name = name,
                            gainsDb = decodeGains(parts[1]),
                            preampDb = EqualizerBands.clampGain(parts[2].toFloatOrNull() ?: 0f),
                        )
                    }
                    .take(MAX_SAVED)
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
