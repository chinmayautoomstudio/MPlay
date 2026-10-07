package com.autoomstudio.mplay.data.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class SupabaseAuthBackend(
    private val client: SupabaseClient,
    private val sessionStore: SessionManager,
) : AuthBackend {

    override val status: Flow<BackendStatus> = client.auth.sessionStatus.map { status ->
        when (status) {
            SessionStatus.Initializing -> BackendStatus.Initializing
            is SessionStatus.Authenticated -> status.session.user?.toAccountUser()
                ?.let(BackendStatus::Authenticated)
                ?: BackendStatus.RefreshFailed(storedUser())
            is SessionStatus.NotAuthenticated -> BackendStatus.NotAuthenticated
            is SessionStatus.RefreshFailure -> BackendStatus.RefreshFailed(storedUser())
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) = mapErrors {
        client.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (_: Exception) {
            // Offline or already revoked: the session must still be gone from the phone.
            client.auth.clearSession()
        }
    }

    override suspend fun refresh() = mapErrors { client.auth.refreshCurrentSession() }

    override suspend fun isDisabled(userId: String): Boolean = mapErrors {
        client.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()
            ?.disabled ?: false
    }

    private suspend fun storedUser(): AccountUser? = sessionStore.loadSessionOrNull()?.user?.toAccountUser()

    private inline fun <T> mapErrors(block: () -> T): T = try {
        block()
    } catch (e: AuthRestException) {
        throw AuthCodeException(e.error, e)
    }
}

internal fun UserInfo.toAccountUser(): AccountUser = AccountUser(
    id = id,
    email = email,
    name = userMetadata.string("full_name") ?: userMetadata.string("name"),
    avatarUrl = userMetadata.string("avatar_url") ?: userMetadata.string("picture"),
)

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
