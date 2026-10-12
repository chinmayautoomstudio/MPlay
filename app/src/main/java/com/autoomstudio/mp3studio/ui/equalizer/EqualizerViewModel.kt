package com.autoomstudio.mp3studio.ui.equalizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.equalizer.EqualizerRepository
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.playback.equalizer.BuiltInPreset
import com.autoomstudio.mp3studio.playback.equalizer.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EqualizerViewModel(
    private val repository: EqualizerRepository,
    appSettings: AppSettings,
) : ViewModel() {

    val state: StateFlow<EqualizerState> = repository.state

    val lofiEnabled: StateFlow<Boolean> = appSettings.lofiEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(enabled) }
    }

    fun setBandGain(index: Int, gainDb: Float) {
        viewModelScope.launch { repository.setBandGain(index, gainDb) }
    }

    fun setPreamp(preampDb: Float) {
        viewModelScope.launch { repository.setPreamp(preampDb) }
    }

    fun setBassBoost(enabled: Boolean) {
        viewModelScope.launch { repository.setBassBoost(enabled) }
    }

    fun setVirtualizer(enabled: Boolean) {
        viewModelScope.launch { repository.setVirtualizer(enabled) }
    }

    fun setLoudness(enabled: Boolean) {
        viewModelScope.launch { repository.setLoudness(enabled) }
    }

    fun selectBuiltIn(preset: BuiltInPreset) {
        viewModelScope.launch { repository.selectBuiltIn(preset) }
    }

    fun selectCustom() {
        viewModelScope.launch { repository.selectCustom() }
    }

    fun selectSaved(name: String) {
        viewModelScope.launch { repository.selectSaved(name) }
    }

    fun reset() {
        viewModelScope.launch { repository.reset() }
    }

    fun saveCurrentAsPreset(name: String) {
        viewModelScope.launch {
            _message.value = repository.saveCurrentAsPreset(name)
                ?: "Saved \"$name\""
        }
    }

    fun renameSavedPreset(oldName: String, newName: String) {
        viewModelScope.launch {
            _message.value = repository.renameSavedPreset(oldName, newName)
        }
    }

    fun deleteSavedPreset(name: String) {
        viewModelScope.launch { repository.deleteSavedPreset(name) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                EqualizerViewModel(app.container.equalizerRepository, app.container.appSettings)
            }
        }
    }
}
