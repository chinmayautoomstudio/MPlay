package com.autoomstudio.mp3studio.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.account.AuthError
import com.autoomstudio.mp3studio.data.account.AuthErrors
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.account.Nonce
import com.autoomstudio.mp3studio.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<AuthState> = container.authRepository.state

    private val _signingIn = MutableStateFlow(false)
    val signingIn: StateFlow<Boolean> = _signingIn.asStateFlow()

    private val _errors = Channel<AuthError>(Channel.BUFFERED)
    val errors: Flow<AuthError> = _errors.receiveAsFlow()

    /** [activityContext] hosts the Google account sheet; it is used only for the duration of the call. */
    fun signInWithGoogle(activityContext: Context) {
        if (_signingIn.value) return
        _signingIn.value = true
        viewModelScope.launch {
            try {
                val rawNonce = Nonce.generate()
                val idToken = container.googleSignIn.requestIdToken(activityContext, Nonce.sha256Hex(rawNonce))
                container.authRepository.signInWithGoogleIdToken(idToken, rawNonce)
                container.authRepository.verifyAccount()?.let { _errors.send(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Google sign-in failed", e)
                val error = AuthErrors.classify(e)
                if (error != AuthError.Cancelled) _errors.send(error)
            } finally {
                _signingIn.value = false
            }
        }
    }

    /** Run when the app comes to the foreground: drops a disabled or revoked account (AU11), refreshes the profile. */
    fun verifyAccount() {
        viewModelScope.launch {
            val signedIn = container.authRepository.state.value as? AuthState.SignedIn ?: return@launch
            container.authRepository.verifyAccount()?.let {
                _errors.send(it)
                return@launch
            }
            runCatching { container.profileRepository.refresh(signedIn.user.id) }
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"

        val Factory = viewModelFactory {
            initializer { AuthViewModel((this[APPLICATION_KEY] as MPlayApp).container) }
        }
    }
}
