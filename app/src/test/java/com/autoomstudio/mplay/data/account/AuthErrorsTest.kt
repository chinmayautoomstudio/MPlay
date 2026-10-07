package com.autoomstudio.mplay.data.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class AuthErrorsTest {

    @Test
    fun bannedUserIsDisabled() {
        assertEquals(AuthError.AccountDisabled, AuthErrors.classifyCode("user_banned"))
    }

    @Test
    fun rejectedSessionsAreRevoked() {
        listOf("session_not_found", "session_expired", "refresh_token_not_found", "refresh_token_already_used", "user_not_found")
            .forEach { assertEquals(it, AuthError.SessionRevoked, AuthErrors.classifyCode(it)) }
    }

    @Test
    fun unknownCodeIsUnknown() {
        assertEquals(AuthError.Unknown, AuthErrors.classifyCode("over_request_rate_limit"))
    }

    @Test
    fun classifiesExceptions() {
        assertEquals(AuthError.Cancelled, AuthErrors.classify(GetCredentialCancellationException("closed")))
        assertEquals(AuthError.NoGoogleAccount, AuthErrors.classify(NoCredentialException("none")))
        assertEquals(AuthError.Network, AuthErrors.classify(UnknownHostException("offline")))
        assertEquals(AuthError.AccountDisabled, AuthErrors.classify(AccountDisabledException()))
        assertEquals(AuthError.NotConfigured, AuthErrors.classify(AuthNotConfiguredException()))
        assertEquals(AuthError.AccountDisabled, AuthErrors.classify(AuthCodeException("user_banned")))
    }

    @Test
    fun looksThroughWrappers() {
        val wrapped = IllegalStateException("outer", RuntimeException("middle", IOException("socket closed")))
        assertEquals(AuthError.Network, AuthErrors.classify(wrapped))
        assertEquals(AuthError.Unknown, AuthErrors.classify(IllegalStateException("no cause")))
    }

    @Test
    fun onlyServerRejectionsEndTheSession() {
        assertTrue(AuthErrors.endsSession(AuthError.AccountDisabled))
        assertTrue(AuthErrors.endsSession(AuthError.SessionRevoked))
        AuthError.entries.filterNot { it == AuthError.AccountDisabled || it == AuthError.SessionRevoked }
            .forEach { assertFalse(it.name, AuthErrors.endsSession(it)) }
    }
}
