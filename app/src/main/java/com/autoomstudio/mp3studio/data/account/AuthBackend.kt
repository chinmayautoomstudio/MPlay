package com.autoomstudio.mp3studio.data.account

import kotlinx.coroutines.flow.Flow

/** What [AuthRepository] needs from the auth server; [SupabaseAuthBackend] in the app, a fake in tests. */
interface AuthBackend {

    val status: Flow<BackendStatus>

    /** Exchanges a Google ID token (issued with the SHA-256 of [rawNonce]) for a session. */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String)

    /** Ends the session on the server when possible, and always on the phone. */
    suspend fun signOut()

    /** Forgets the session on the phone only, for an account the server has already deleted. */
    suspend fun signOutLocally()

    /** Refreshes the session; throws [AuthCodeException] if the server rejects it, an IOException when offline. */
    suspend fun refresh()

    /** Whether an Admin disabled this account, read from the user's own profile row. */
    suspend fun isDisabled(userId: String): Boolean
}

sealed interface BackendStatus {
    data object Initializing : BackendStatus

    data class Authenticated(val user: AccountUser) : BackendStatus

    data object NotAuthenticated : BackendStatus

    /** A refresh failed (usually offline). [user] is the saved session's user, if one is still stored. */
    data class RefreshFailed(val user: AccountUser?) : BackendStatus
}
