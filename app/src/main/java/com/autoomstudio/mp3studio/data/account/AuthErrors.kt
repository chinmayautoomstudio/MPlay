package com.autoomstudio.mp3studio.data.account

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import java.io.IOException

/** Why signing in or checking the account failed, in terms the UI can explain (PRD AU8). */
enum class AuthError {
    Cancelled,
    NoGoogleAccount,

    /** Google couldn't confirm the chosen account on this phone (status 16, "Account reauth failed"). */
    AccountUnavailable,
    PlayServicesUnavailable,
    Network,
    AccountDisabled,

    /** The server no longer accepts this session (signed out elsewhere, user deleted). */
    SessionRevoked,

    /** The build has no Supabase or Google client settings. */
    NotConfigured,
    Unknown,
}

/** An error the auth server returned, by its GoTrue error code (for example `user_banned`). */
class AuthCodeException(val code: String, cause: Throwable? = null) : Exception("Auth error: $code", cause)

/** The profile is marked disabled by an Admin. */
class AccountDisabledException : Exception("Account disabled")

/** Supabase URL or key, or the Google Web client ID, is missing from the build. */
class AuthNotConfiguredException : Exception("Sign-in is not configured in this build")

object AuthErrors {

    private val revokedCodes = setOf(
        "session_not_found",
        "session_expired",
        "refresh_token_not_found",
        "refresh_token_already_used",
        "user_not_found",
    )

    fun classify(error: Throwable): AuthError = when (error) {
        // Play services reports a failed account check (for example a missing Android OAuth client or a work
        // account that still needs setup) as a cancellation; only a real dismissal should stay silent.
        is GetCredentialCancellationException -> if (isReauthFailure(error.message)) {
            AuthError.AccountUnavailable
        } else {
            AuthError.Cancelled
        }
        is GetCredentialInterruptedException -> AuthError.Cancelled
        is NoCredentialException -> AuthError.NoGoogleAccount
        is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException ->
            AuthError.PlayServicesUnavailable
        is AccountDisabledException -> AuthError.AccountDisabled
        is AuthNotConfiguredException -> AuthError.NotConfigured
        is AuthCodeException -> classifyCode(error.code)
        is IOException -> AuthError.Network
        else -> error.cause?.let(::classify) ?: AuthError.Unknown
    }

    private fun isReauthFailure(message: String?): Boolean =
        message != null && (message.startsWith("[16]") || message.contains("reauth", ignoreCase = true))

    fun classifyCode(code: String): AuthError = when (code) {
        "user_banned" -> AuthError.AccountDisabled
        in revokedCodes -> AuthError.SessionRevoked
        else -> AuthError.Unknown
    }

    /** Errors after which the saved session must be dropped, as opposed to retried later. */
    fun endsSession(error: AuthError): Boolean =
        error == AuthError.AccountDisabled || error == AuthError.SessionRevoked
}
