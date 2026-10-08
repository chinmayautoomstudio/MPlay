package com.autoomstudio.mp3studio.playback

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** The queue as it was last saved: song IDs in play order, the current index and position. */
data class SavedSession(
    val songIds: List<Long>,
    val index: Int,
    val positionMs: Long,
)

/** Shuffle flag and Media3 `Player.REPEAT_MODE_*` value. */
data class PlaybackModes(
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

private val Context.playbackSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_session")

class PlaybackSessionStore(context: Context) {

    private val dataStore = context.applicationContext.playbackSessionDataStore

    suspend fun save(session: SavedSession) {
        dataStore.edit { prefs ->
            prefs[KEY_SONG_IDS] = session.songIds.joinToString(SEPARATOR)
            prefs[KEY_INDEX] = session.index
            prefs[KEY_POSITION] = session.positionMs
        }
    }

    suspend fun load(): SavedSession? {
        val prefs = dataStore.data.first()
        val ids = prefs[KEY_SONG_IDS]
            ?.split(SEPARATOR)
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        if (ids.isEmpty()) return null
        return SavedSession(
            songIds = ids,
            index = prefs[KEY_INDEX] ?: 0,
            positionMs = prefs[KEY_POSITION] ?: 0L,
        )
    }

    /** Shuffle and repeat are kept apart from the queue so they survive even when no queue is saved. */
    suspend fun saveModes(modes: PlaybackModes) {
        dataStore.edit { prefs ->
            prefs[KEY_SHUFFLE] = modes.shuffleEnabled
            prefs[KEY_REPEAT] = modes.repeatMode
        }
    }

    suspend fun loadModes(): PlaybackModes? {
        val prefs = dataStore.data.first()
        val shuffle = prefs[KEY_SHUFFLE] ?: return null
        return PlaybackModes(shuffleEnabled = shuffle, repeatMode = prefs[KEY_REPEAT] ?: 0)
    }

    private companion object {
        const val SEPARATOR = ","
        val KEY_SONG_IDS = stringPreferencesKey("song_ids")
        val KEY_INDEX = intPreferencesKey("index")
        val KEY_POSITION = longPreferencesKey("position_ms")
        val KEY_SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val KEY_REPEAT = intPreferencesKey("repeat_mode")
    }
}
