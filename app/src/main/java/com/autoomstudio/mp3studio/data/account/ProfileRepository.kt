package com.autoomstudio.mp3studio.data.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in user's own `profiles` row. Kept in memory; the Google account details cover it while offline. */
class ProfileRepository(private val client: SupabaseClient) {

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    /** Loads the row; failures (offline) keep the last value. */
    suspend fun refresh(userId: String): Profile? = try {
        client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()
            .also { _profile.value = it }
    } catch (e: AuthRestException) {
        throw AuthCodeException(e.error, e)
    } catch (_: Exception) {
        _profile.value
    }

    /** Throws when offline or rejected, so the caller can show an error. */
    suspend fun updateDisplayName(userId: String, name: String) {
        val clean = name.trim().take(MAX_NAME_LENGTH)
        client.from("profiles").update({ set("display_name", clean) }) { filter { eq("id", userId) } }
        _profile.value = _profile.value?.copy(displayName = clean)
    }

    fun clear() {
        _profile.value = null
    }

    companion object {
        const val MAX_NAME_LENGTH = 80
    }
}
