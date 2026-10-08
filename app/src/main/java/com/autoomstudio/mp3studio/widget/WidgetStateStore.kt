package com.autoomstudio.mp3studio.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_state")

/**
 * Persists what the widget shows so it survives process death; Glance may render after the
 * playback service is gone. Artwork is kept as a small PNG next to the preferences.
 */
class WidgetStateStore(context: Context) {

    private val dataStore = context.applicationContext.widgetDataStore
    private val artworkFile = File(context.applicationContext.filesDir, "widget/artwork.png")

    val state: Flow<WidgetState> = dataStore.data
        .map { prefs ->
            WidgetState(
                songId = prefs[KEY_SONG_ID],
                title = prefs[KEY_TITLE].orEmpty(),
                artist = prefs[KEY_ARTIST].orEmpty(),
                isPlaying = prefs[KEY_PLAYING] ?: false,
            )
        }
        .distinctUntilChanged()

    /** Emits the stored artwork whenever the artwork version changes. */
    val artwork: Flow<Bitmap?> = dataStore.data
        .map { it[KEY_ARTWORK_VERSION] ?: 0L }
        .distinctUntilChanged()
        .map { if (artworkFile.exists()) BitmapFactory.decodeFile(artworkFile.path) else null }
        .flowOn(Dispatchers.IO)

    suspend fun current(): WidgetState = state.first()

    suspend fun save(state: WidgetState) {
        dataStore.edit { prefs ->
            val id = state.songId
            if (id == null) prefs.remove(KEY_SONG_ID) else prefs[KEY_SONG_ID] = id
            prefs[KEY_TITLE] = state.title
            prefs[KEY_ARTIST] = state.artist
            prefs[KEY_PLAYING] = state.isPlaying
        }
    }

    /** Replaces the artwork; null clears it so the widget shows its placeholder. */
    suspend fun saveArtwork(bitmap: Bitmap?) {
        withContext(Dispatchers.IO) {
            if (bitmap == null) {
                artworkFile.delete()
            } else {
                artworkFile.parentFile?.mkdirs()
                val tmp = File(artworkFile.parentFile, "artwork.tmp")
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (!tmp.renameTo(artworkFile)) {
                    tmp.copyTo(artworkFile, overwrite = true)
                    tmp.delete()
                }
            }
        }
        dataStore.edit { prefs -> prefs[KEY_ARTWORK_VERSION] = System.nanoTime() }
    }

    private companion object {
        val KEY_SONG_ID = longPreferencesKey("song_id")
        val KEY_TITLE = stringPreferencesKey("title")
        val KEY_ARTIST = stringPreferencesKey("artist")
        val KEY_PLAYING = booleanPreferencesKey("is_playing")
        val KEY_ARTWORK_VERSION = longPreferencesKey("artwork_version")
    }
}
