package com.autoomstudio.mp3studio.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Gets a Google ID token through Credential Manager's "Sign in with Google" sheet. */
class GoogleSignIn(private val webClientId: String) {

    /** [activityContext] must be an Activity so the account sheet can be shown over it. */
    suspend fun requestIdToken(activityContext: Context, hashedNonce: String): String {
        if (webClientId.isBlank()) throw AuthNotConfiguredException()
        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type ${credential.type}")
    }
}
