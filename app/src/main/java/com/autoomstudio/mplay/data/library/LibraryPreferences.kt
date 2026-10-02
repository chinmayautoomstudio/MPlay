package com.autoomstudio.mplay.data.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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

    private companion object {
        val KEY_SORT_ORDER = stringPreferencesKey("sort_order")
    }
}
