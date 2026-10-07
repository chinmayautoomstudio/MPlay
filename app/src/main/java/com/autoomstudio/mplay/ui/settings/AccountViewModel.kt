package com.autoomstudio.mplay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.account.AuthState
import com.autoomstudio.mplay.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the Account card shows. The profile row wins; the Google account details cover it until it loads. */
data class AccountUiState(
    val name: String,
    val email: String,
    val avatarUrl: String?,
)

class AccountViewModel(private val container: AppContainer) : ViewModel() {

    private val auth = container.authRepository
    private val profiles = container.profileRepository

    val account: StateFlow<AccountUiState?> = combine(auth.state, profiles.profile) { state, profile ->
        val user = (state as? AuthState.SignedIn)?.user ?: return@combine null
        AccountUiState(
            name = profile?.displayName?.takeIf { it.isNotBlank() } ?: user.name ?: user.email.orEmpty(),
            email = profile?.email ?: user.email.orEmpty(),
            avatarUrl = profile?.avatarUrl ?: user.avatarUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _savingName = MutableStateFlow(false)
    val savingName: StateFlow<Boolean> = _savingName.asStateFlow()

    init {
        val user = (auth.state.value as? AuthState.SignedIn)?.user
        if (user != null && profiles.profile.value == null) {
            viewModelScope.launch { runCatching { profiles.refresh(user.id) } }
        }
    }

    /** Calls [onResult] with true once saved; false when offline or rejected. */
    fun saveName(name: String, onResult: (Boolean) -> Unit) {
        val user = (auth.state.value as? AuthState.SignedIn)?.user ?: return
        if (_savingName.value || name.isBlank()) return
        _savingName.value = true
        viewModelScope.launch {
            val saved = try {
                profiles.updateDisplayName(user.id, name)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
            _savingName.value = false
            onResult(saved)
        }
    }

    /** Runs on the application scope: signing out clears this ViewModel before the call returns. */
    fun signOut() {
        container.applicationScope.launch { auth.signOut() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AccountViewModel((this[APPLICATION_KEY] as MPlayApp).container) }
        }
    }
}
