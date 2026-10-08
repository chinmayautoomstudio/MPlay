package com.autoomstudio.mp3studio.data.account

/** The signed-in Google account, as far as the phone knows it (may be from the cached session while offline). */
data class AccountUser(
    val id: String,
    val email: String?,
    val name: String?,
    val avatarUrl: String?,
)

sealed interface AuthState {
    /** The saved session is still being read; nothing may be shown or played yet. */
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val user: AccountUser) : AuthState
}
