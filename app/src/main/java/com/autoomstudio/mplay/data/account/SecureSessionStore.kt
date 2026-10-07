package com.autoomstudio.mplay.data.account

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val Context.authSessionDataStore by preferencesDataStore(name = SecureSessionStore.DATASTORE_NAME)

/**
 * Keeps the Supabase session in its own DataStore file, encrypted with Tink AES-256-GCM. The Tink keyset is itself
 * encrypted by a key in the Android Keystore, so the session can only be read on this phone. Both files are excluded
 * from backup (res/xml/backup_rules.xml and data_extraction_rules.xml).
 */
class SecureSessionStore(context: Context) : SessionManager {
    private val appContext = context.applicationContext
    private val dataStore = appContext.authSessionDataStore
    private val aeadLock = Mutex()
    private var aead: Aead? = null

    override suspend fun saveSession(session: UserSession) {
        val plain = json.encodeToString(UserSession.serializer(), session).toByteArray(Charsets.UTF_8)
        val cipher = aead().encrypt(plain, ASSOCIATED_DATA)
        val encoded = Base64.encodeToString(cipher, Base64.NO_WRAP)
        dataStore.edit { it[SESSION_KEY] = encoded }
    }

    override suspend fun loadSession(): UserSession {
        val encoded = dataStore.data.first()[SESSION_KEY] ?: throw NoSavedSessionException()
        return try {
            val plain = aead().decrypt(Base64.decode(encoded, Base64.NO_WRAP), ASSOCIATED_DATA)
            json.decodeFromString(UserSession.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            // A keyset from another device or a corrupted file: treat as signed out rather than crash.
            Log.w(TAG, "Saved session is unreadable; signing out", e)
            deleteSession()
            throw NoSavedSessionException()
        }
    }

    override suspend fun deleteSession() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    private suspend fun aead(): Aead = aeadLock.withLock {
        aead ?: withContext(Dispatchers.IO) { buildAead() }.also { aead = it }
    }

    private fun buildAead(): Aead {
        AeadConfig.register()
        val handle = try {
            keysetManager().keysetHandle
        } catch (e: Exception) {
            // The Keystore key was lost (restore, OS reset of the Keystore): start over with a new keyset.
            Log.w(TAG, "Session keyset unreadable; creating a new one", e)
            appContext.getSharedPreferences(KEYSET_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            keysetManager().keysetHandle
        }
        return handle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun keysetManager(): AndroidKeysetManager = AndroidKeysetManager.Builder()
        .withSharedPref(appContext, KEYSET_NAME, KEYSET_PREFS)
        .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
        .withMasterKeyUri(MASTER_KEY_URI)
        .build()

    class NoSavedSessionException : Exception("No saved session")

    companion object {
        private const val TAG = "SecureSessionStore"
        const val DATASTORE_NAME = "auth_session"
        const val KEYSET_PREFS = "auth_keyset_prefs"
        private const val KEYSET_NAME = "auth_keyset"
        private const val MASTER_KEY_URI = "android-keystore://mp3studio_auth_master_key"
        private val SESSION_KEY = stringPreferencesKey("session")
        private val ASSOCIATED_DATA = "mp3studio.auth.session".toByteArray(Charsets.UTF_8)
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
