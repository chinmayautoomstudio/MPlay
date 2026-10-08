package com.autoomstudio.mp3studio.data.plan

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

interface EntitlementsCache {
    suspend fun read(): Entitlements?
    suspend fun write(entitlements: Entitlements?)
}

private val Context.entitlementsDataStore: DataStore<Preferences> by preferencesDataStore(name = "entitlements")

/** The last server answer, so Pro and Trial keep working offline within the grace window (PRD PL4). */
class DataStoreEntitlementsCache(context: Context) : EntitlementsCache {

    private val dataStore = context.applicationContext.entitlementsDataStore

    override suspend fun read(): Entitlements? {
        val stored = dataStore.data.first()[KEY_ENTITLEMENTS] ?: return null
        return runCatching { json.decodeFromString<Entitlements>(stored) }.getOrNull()
    }

    override suspend fun write(entitlements: Entitlements?) {
        dataStore.edit { prefs ->
            if (entitlements == null) prefs.remove(KEY_ENTITLEMENTS)
            else prefs[KEY_ENTITLEMENTS] = json.encodeToString(Entitlements.serializer(), entitlements)
        }
    }

    private companion object {
        val KEY_ENTITLEMENTS = stringPreferencesKey("entitlements_json")
        val json = Json { ignoreUnknownKeys = true }
    }
}
