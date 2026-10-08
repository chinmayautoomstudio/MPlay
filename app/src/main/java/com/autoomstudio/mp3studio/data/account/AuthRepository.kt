package com.autoomstudio.mp3studio.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single source of truth for whether someone is signed in. Sign-in is mandatory (PRD AU1): while [state] is not
 * [AuthState.SignedIn], the UI shows only the sign-in screen and playback refuses to start.
 */
class AuthRepository(
    private val backend: AuthBackend,
    private val scope: CoroutineScope,
    private val deletion: AccountDeletionBackend,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = _state.value is AuthState.SignedIn

    private var started = false

    /** Starts following the backend; call once, in the main process. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            backend.status.collect { status -> _state.value = reduce(_state.value, status) }
        }
    }

    /** Waits until the saved session has been read, for callers that start before the UI (services, receivers). */
    suspend fun awaitReady(): AuthState = state.first { it !is AuthState.Loading }

    /** Throws on failure; classify with [AuthErrors.classify]. */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
        backend.signInWithGoogleIdToken(idToken, rawNonce)
    }

    suspend fun signOut() {
        backend.signOut()
        _state.value = AuthState.SignedOut
    }

    /**
     * Deletes the signed-in account on the server (PRD AU9), then signs out on the phone only, since the server no
     * longer knows the session. Returns the deleted user's ID, or null when nobody was signed in. Throws
     * [AccountDeletionException] and stays signed in when the server refused or couldn't be reached.
     */
    suspend fun deleteAccount(): String? {
        val user = (_state.value as? AuthState.SignedIn)?.user ?: return null
        deletion.deleteAccount()
        backend.signOutLocally()
        _state.value = AuthState.SignedOut
        return user.id
    }

    /**
     * Confirms the account is still allowed (PRD AU11): signs out if an Admin disabled it or the server rejected the
     * session. Network failures are ignored so the app keeps working offline. Returns the error that signed out, if any.
     */
    suspend fun verifyAccount(): AuthError? {
        val user = (_state.value as? AuthState.SignedIn)?.user ?: return null
        val error = try {
            backend.refresh()
            if (backend.isDisabled(user.id)) AuthError.AccountDisabled else null
        } catch (e: Exception) {
            AuthErrors.classify(e)
        }
        if (error != null && AuthErrors.endsSession(error)) {
            signOut()
            return error
        }
        return null
    }

    companion object {
        /** Offline refresh failures keep the last known user signed in; only the server can end a session. */
        fun reduce(current: AuthState, status: BackendStatus): AuthState = when (status) {
            BackendStatus.Initializing -> if (current is AuthState.SignedIn) current else AuthState.Loading
            is BackendStatus.Authenticated -> AuthState.SignedIn(status.user)
            BackendStatus.NotAuthenticated -> AuthState.SignedOut
            is BackendStatus.RefreshFailed -> when {
                status.user != null -> AuthState.SignedIn(status.user)
                current is AuthState.SignedIn -> current
                else -> AuthState.SignedOut
            }
        }
    }
}
