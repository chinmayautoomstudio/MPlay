package com.autoomstudio.mp3studio.data.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.libraryPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "library_prefs")

class LibraryPreferences(context: Context) {

    private val dataStore = context.applicationContext.libraryPrefsDataStore

    val sortOrder: Flow<SongSortOrder> = dataStore.data
        .map { prefs ->
            prefs[KEY_SORT_ORDER]
                ?.let { name -> SongSortOrder.entries.firstOrNull { it.name == name } }
                ?: SongSortOrder.Title
        }
        .distinctUntilChanged()

    suspend fun setSortOrder(order: SongSortOrder) {
        dataStore.edit { it[KEY_SORT_ORDER] = order.name }
    }

    /** Relative folder paths (trailing slash) hidden from the library. */
    val excludedFolders: Flow<Set<String>> = dataStore.data
        .map { prefs ->
            prefs[KEY_EXCLUDED_FOLDERS]
                ?.map { FolderPaths.normalize(it) }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
        }
        .distinctUntilChanged()

    suspend fun setFolderExcluded(path: String, excluded: Boolean) {
        val key = FolderPaths.normalize(path)
        if (key.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[KEY_EXCLUDED_FOLDERS]?.toMutableSet() ?: mutableSetOf()
            if (excluded) current += key else current -= key
            prefs[KEY_EXCLUDED_FOLDERS] = current
        }
    }

    private companion object {
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
        val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
    }
}
