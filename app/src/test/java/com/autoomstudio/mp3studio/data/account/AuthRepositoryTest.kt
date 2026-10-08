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
import org.junit.Test
import java.io.IOException

class AuthRepositoryTest {

    private val user = AccountUser(id = "u1", email = "a@example.com", name = "A", avatarUrl = null)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun reduceMapsBackendStatus() {
        assertEquals(AuthState.Loading, AuthRepository.reduce(AuthState.Loading, BackendStatus.Initializing))
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

    private class FakeBackend(
        private val disabled: Boolean = false,
        private val refreshError: Exception? = null,
    ) : AuthBackend {
        override val status = MutableStateFlow<BackendStatus>(BackendStatus.Initializing)
        var signOuts = 0
        var localSignOuts = 0

        override suspend fun signInWithGoogleIdToken(idToken: String, rawNonce: String) = Unit

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
    }
}
