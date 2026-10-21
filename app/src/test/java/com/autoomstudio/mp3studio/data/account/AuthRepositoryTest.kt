package com.autoomstudio.mp3studio.data.account

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AuthRepositoryTest {

    private val user = AccountUser(id = "u1", email = "a@example.com", name = "A", avatarUrl = null)
    private val phoneA = LoggedInDevice("a".repeat(64), "Pixel 8", 1L, 2L, isCurrent = false)
    private val phoneB = LoggedInDevice("b".repeat(64), "Pixel 9", 1L, 3L, isCurrent = false)
    private val thisPhone = LoggedInDevice("c".repeat(64), "Pixel 10", 4L, 4L, isCurrent = true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun reduceMapsBackendStatus() {
        assertEquals(AuthState.Loading, AuthRepository.reduce(AuthState.Loading, BackendStatus.Initializing))
        assertEquals(AuthState.SignedOut, AuthRepository.reduce(AuthState.SignedOut, BackendStatus.Initializing))
        assertEquals(AuthState.SignedIn(user), AuthRepository.reduce(AuthState.Loading, BackendStatus.Authenticated(user)))
        assertEquals(AuthState.SignedOut, AuthRepository.reduce(AuthState.SignedIn(user), BackendStatus.NotAuthenticated))
    }

    @Test
    fun offlineRefreshFailureKeepsTheUser() {
        val signedIn = AuthState.SignedIn(user)
        assertEquals(signedIn, AuthRepository.reduce(AuthState.Loading, BackendStatus.RefreshFailed(user)))
        assertEquals(signedIn, AuthRepository.reduce(signedIn, BackendStatus.RefreshFailed(null)))
        assertEquals(signedIn, AuthRepository.reduce(signedIn, BackendStatus.Initializing))
        assertEquals(AuthState.SignedOut, AuthRepository.reduce(AuthState.Loading, BackendStatus.RefreshFailed(null)))
    }

    @Test
    fun disabledAccountIsSignedOut() = runBlocking {
        val backend = FakeBackend(disabled = true)
        val repository = signedInRepository(backend)
        assertEquals(AuthError.AccountDisabled, repository.verifyAccount())
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertEquals(1, backend.signOuts)
    }

    @Test
    fun revokedSessionIsSignedOut() = runBlocking {
        val backend = FakeBackend(refreshError = AuthCodeException("refresh_token_not_found"))
        val repository = signedInRepository(backend)
        assertEquals(AuthError.SessionRevoked, repository.verifyAccount())
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun offlineCheckKeepsTheSession() = runBlocking {
        val backend = FakeBackend(refreshError = IOException("offline"))
        val repository = signedInRepository(backend)
        assertNull(repository.verifyAccount())
        assertEquals(AuthState.SignedIn(user), repository.state.value)
        assertEquals(0, backend.signOuts)
    }

    @Test
    fun activeAccountStaysSignedIn() = runBlocking {
        val repository = signedInRepository(FakeBackend())
        assertNull(repository.verifyAccount())
        assertEquals(AuthState.SignedIn(user), repository.state.value)
    }

    @Test
    fun deletingSignsOutOnThePhoneOnly() = runBlocking {
        val backend = FakeBackend()
        var deleted = 0
        val repository = signedInRepository(backend) { deleted++ }
        assertEquals("u1", repository.deleteAccount())
        assertEquals(1, deleted)
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertEquals(1, backend.localSignOuts)
        assertEquals(0, backend.signOuts)
    }

    @Test
    fun aRefusedDeletionKeepsTheUserSignedIn() = runBlocking {
        val backend = FakeBackend()
        val repository = signedInRepository(backend) { throw AccountDeletionException(DeletionError.LastAdmin) }
        val error = runCatching { repository.deleteAccount() }.exceptionOrNull()
        assertEquals(DeletionError.LastAdmin, (error as AccountDeletionException).error)
        assertEquals(AuthState.SignedIn(user), repository.state.value)
        assertEquals(0, backend.localSignOuts)
    }

    @Test
    fun aThirdDeviceFromLoadingShowsThePickerNotABlankScreen() = runBlocking {
        val backend = FakeBackend(
            registration = DeviceRegistrationResult.LimitReached(listOf(phoneA, phoneB)),
            current = user,
        )
        val repository = AuthRepository(backend, scope, AccountDeletionBackend {})
        repository.start()
        repository.signInWithGoogleIdToken("token", "nonce")
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertEquals(listOf(phoneA, phoneB), repository.pendingDeviceLimit.value)
    }

    @Test
    fun aStuckSessionLoadLeavesLoadingSoTheSplashCanDismiss() = runBlocking {
        val repository = AuthRepository(FakeBackend(), scope, AccountDeletionBackend {}, loadingTimeoutMs = 40)
        repository.start()
        withTimeout(1_000) { repository.state.first { it is AuthState.SignedOut } }
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun aThirdDeviceHoldsSignInUntilOneIsReplaced() = runBlocking {
        val backend = FakeBackend(
            registration = DeviceRegistrationResult.LimitReached(listOf(phoneA, phoneB)),
            current = user,
        )
        val repository = signedOutRepository(backend)
        repository.signInWithGoogleIdToken("token", "nonce")
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertEquals(listOf(phoneA, phoneB), repository.pendingDeviceLimit.value)
        assertEquals(0, backend.signOuts)

        backend.registration = DeviceRegistrationResult.Registered(listOf(phoneA, thisPhone))
        repository.completeSignInByReplacingDevice(phoneB.deviceId)
        assertEquals(AuthState.SignedIn(user), repository.state.value)
        assertNull(repository.pendingDeviceLimit.value)
        assertEquals(listOf(phoneA, thisPhone), repository.devices.value)
        assertEquals(phoneB.deviceId, backend.lastReplaceId)
    }

    @Test
    fun cancellingTheDevicePromptSignsOutTheTemporarySession() = runBlocking {
        val backend = FakeBackend(
            registration = DeviceRegistrationResult.LimitReached(listOf(phoneA, phoneB)),
            current = user,
        )
        val repository = signedOutRepository(backend)
        repository.signInWithGoogleIdToken("token", "nonce")
        repository.cancelPendingSignIn()
        assertEquals(AuthState.SignedOut, repository.state.value)
        assertNull(repository.pendingDeviceLimit.value)
        assertEquals(1, backend.signOuts)
    }

    @Test
    fun aRemoteDeviceRevocationSignsOut() = runBlocking {
        val backend = FakeBackend(verification = DeviceVerificationResult.Revoked)
        val repository = signedInRepository(backend)
        assertEquals(AuthError.SessionRevoked, repository.verifyAccount())
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun signingOutAnotherDeviceUpdatesTheList() = runBlocking {
        val backend = FakeBackend(listed = listOf(phoneA, thisPhone))
        val repository = signedInRepository(backend)
        repository.refreshDevices()
        assertEquals(listOf(phoneA, thisPhone), repository.devices.value)
        backend.listed = listOf(thisPhone)
        repository.signOutDevice(phoneA.deviceId)
        assertEquals(listOf(thisPhone), repository.devices.value)
        assertEquals(listOf(phoneA.deviceId), backend.removed)
    }

    private suspend fun signedOutRepository(backend: FakeBackend): AuthRepository {
        backend.status.value = BackendStatus.NotAuthenticated
        return AuthRepository(backend, scope, AccountDeletionBackend {}).also {
            it.start()
            withTimeout(5_000) { it.state.first { state -> state is AuthState.SignedOut } }
        }
    }

    private suspend fun signedInRepository(
        backend: FakeBackend,
        deletion: AccountDeletionBackend = AccountDeletionBackend {},
    ): AuthRepository {
        backend.status.value = BackendStatus.Authenticated(user)
        return AuthRepository(backend, scope, deletion).also {
            it.start()
            withTimeout(5_000) { it.state.first { state -> state is AuthState.SignedIn } }
        }
    }

    private inner class FakeBackend(
        private val disabled: Boolean = false,
        private val refreshError: Exception? = null,
        var registration: DeviceRegistrationResult = DeviceRegistrationResult.Registered(emptyList()),
        var verification: DeviceVerificationResult = DeviceVerificationResult.Ok(emptyList()),
        var listed: List<LoggedInDevice> = emptyList(),
        private val current: AccountUser? = null,
    ) : AuthBackend {
        override val status = MutableStateFlow<BackendStatus>(BackendStatus.Initializing)
        var signOuts = 0
        var localSignOuts = 0
        var lastReplaceId: String? = null
        val removed = mutableListOf<String>()

        override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) {
            status.value = BackendStatus.Authenticated(current ?: user)
        }

        override suspend fun signOut() {
            signOuts++
            status.value = BackendStatus.NotAuthenticated
        }

        override suspend fun signOutLocally() {
            localSignOuts++
        }

        override suspend fun refresh() {
            refreshError?.let { throw it }
        }

        override suspend fun isDisabled(userId: String) = disabled

        override suspend fun registerDevice(replaceDeviceId: String?): DeviceRegistrationResult {
            lastReplaceId = replaceDeviceId
            return registration
        }

        override suspend fun verifyDevice() = verification

        override suspend fun listDevices() = listed

        override suspend fun removeDevice(deviceId: String): List<LoggedInDevice> {
            removed += deviceId
            return listed
        }

        override suspend fun currentUser() = current
    }
}
