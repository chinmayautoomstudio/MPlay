package com.autoomstudio.mp3studio.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single source of truth for whether someone is signed in. Sign-in is mandatory (PRD AU1): while [state] is not
 * [AuthState.SignedIn], the UI shows only the sign-in screen and playback refuses to start. A third phone is held
 * at [AuthState.SignedOut] until the user picks one of the two logged-in devices to replace.
 */
class AuthRepository(
    private val backend: AuthBackend,
    private val scope: CoroutineScope,
    private val deletion: AccountDeletionBackend,
    private val loadingTimeoutMs: Long = LOADING_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    val isSignedIn: Boolean get() = _state.value is AuthState.SignedIn

    private val _devices = MutableStateFlow<List<LoggedInDevice>>(emptyList())
    val devices: StateFlow<List<LoggedInDevice>> = _devices.asStateFlow()

    private val _pendingDeviceLimit = MutableStateFlow<List<LoggedInDevice>?>(null)
    val pendingDeviceLimit: StateFlow<List<LoggedInDevice>?> = _pendingDeviceLimit.asStateFlow()

    private var started = false
    private var awaitingDeviceCheck = false
    private var pendingUser: AccountUser? = null

    /** Starts following the backend; call once, in the main process. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            backend.status.collect { status ->
                if (awaitingDeviceCheck && status is BackendStatus.Authenticated) {
                    pendingUser = status.user
                    return@collect
                }
                if (status is BackendStatus.NotAuthenticated) {
                    awaitingDeviceCheck = false
                    pendingUser = null
                    _pendingDeviceLimit.value = null
                    _devices.value = emptyList()
                }
                _state.value = reduce(_state.value, status)
            }
        }
        // Splash stays up while Loading. If the session store never finishes, leave it so the
        // sign-in screen can show instead of a black window.
        scope.launch {
            delay(loadingTimeoutMs)
            if (_state.value is AuthState.Loading) _state.value = AuthState.SignedOut
        }
    }

    /** Waits until the saved session has been read, for callers that start before the UI (services, receivers). */
    suspend fun awaitReady(): AuthState = state.first { it !is AuthState.Loading }

    /** Throws on failure; classify with [AuthErrors.classify]. */
    suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
        awaitingDeviceCheck = true
        pendingUser = null
        _pendingDeviceLimit.value = null
        try {
            backend.signInWithGoogleIdToken(idToken, rawNonce)
            when (val result = backend.registerDevice()) {
                is DeviceRegistrationResult.Registered -> finishSignIn(result.devices)
                is DeviceRegistrationResult.LimitReached -> holdForDeviceChoice(result.devices)
            }
        } catch (e: Exception) {
            awaitingDeviceCheck = false
            pendingUser = null
            _pendingDeviceLimit.value = null
            backend.signOut()
            _state.value = AuthState.SignedOut
            throw e
        }
    }

    /** Logs the chosen phone out on the server and finishes the held sign-in on this one. */
    suspend fun completeSignInByReplacingDevice(removeDeviceId: String) {
        when (val result = backend.registerDevice(replaceDeviceId = removeDeviceId)) {
            is DeviceRegistrationResult.Registered -> finishSignIn(result.devices)
            is DeviceRegistrationResult.LimitReached -> holdForDeviceChoice(result.devices)
        }
    }

    /** Drops the temporary session from a cancelled third-device prompt. */
    suspend fun cancelPendingSignIn() {
        awaitingDeviceCheck = false
        pendingUser = null
        _pendingDeviceLimit.value = null
        backend.signOut()
        _state.value = AuthState.SignedOut
    }

    suspend fun signOut() {
        awaitingDeviceCheck = false
        pendingUser = null
        _pendingDeviceLimit.value = null
        _devices.value = emptyList()
        _state.value = AuthState.SignedOut
        backend.signOut()
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
        _devices.value = emptyList()
        _state.value = AuthState.SignedOut
        return user.id
    }

    /**
     * Confirms the account is still allowed (PRD AU11): signs out if an Admin disabled it, the server rejected the
     * session, or this phone was logged out from another device. Network failures are ignored so the app keeps
     * working offline. Returns the error that signed out, if any.
     */
    suspend fun verifyAccount(): AuthError? {
        val user = (_state.value as? AuthState.SignedIn)?.user ?: return null
        val error = try {
            backend.refresh()
            when (val check = backend.verifyDevice()) {
                DeviceVerificationResult.Disabled -> AuthError.AccountDisabled
                DeviceVerificationResult.Revoked -> AuthError.SessionRevoked
                is DeviceVerificationResult.Ok -> {
                    _devices.value = check.devices
                    if (backend.isDisabled(user.id)) AuthError.AccountDisabled else null
                }
            }
        } catch (e: Exception) {
            AuthErrors.classify(e)
        }
        if (error != null && AuthErrors.endsSession(error)) {
            signOut()
            return error
        }
        return null
    }

    suspend fun refreshDevices() {
        if (_state.value !is AuthState.SignedIn) return
        runCatching { _devices.value = backend.listDevices() }
    }

    suspend fun signOutDevice(deviceId: String) {
        if (_state.value !is AuthState.SignedIn) return
        _devices.value = backend.removeDevice(deviceId)
    }

    private fun holdForDeviceChoice(devices: List<LoggedInDevice>) {
        _pendingDeviceLimit.value = devices
        _state.value = AuthState.SignedOut
    }

    private suspend fun finishSignIn(devices: List<LoggedInDevice>) {
        val user = pendingUser ?: backend.currentUser()
        awaitingDeviceCheck = false
        pendingUser = null
        _pendingDeviceLimit.value = null
        _devices.value = devices
        _state.value = if (user != null) AuthState.SignedIn(user) else AuthState.SignedOut
    }

    companion object {
        const val LOADING_TIMEOUT_MS = 8_000L

        /** Offline refresh failures keep the last known user signed in; only the server can end a session. */
        fun reduce(current: AuthState, status: BackendStatus): AuthState = when (status) {
            BackendStatus.Initializing -> current
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
