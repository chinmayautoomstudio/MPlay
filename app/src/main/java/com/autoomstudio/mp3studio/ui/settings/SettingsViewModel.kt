package com.autoomstudio.mp3studio.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.data.settings.ThemeMode
import com.autoomstudio.mp3studio.data.settings.ThemeSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {

    /** Null until loaded, so the app doesn't flash the default theme before the saved one. */
    val theme: StateFlow<ThemeSettings?> =
        settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settings.setDynamicColor(enabled) }
    }

    /** True the first time it's called, so the caller shows the notification permission prompt once. */
    suspend fun claimNotificationPrompt(): Boolean {
        if (settings.notificationPromptShown()) return false
        settings.setNotificationPromptShown()
        return true
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                SettingsViewModel(app.container.appSettings)
            }
        }
    }
}
