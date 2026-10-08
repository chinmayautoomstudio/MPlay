package com.autoomstudio.mp3studio.ui.singalong

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.singalong.MixSettings
import com.autoomstudio.mp3studio.singalong.SingAlongOptions
import com.autoomstudio.mp3studio.singalong.SingAlongSession
import com.autoomstudio.mp3studio.singalong.SingAlongState
import com.autoomstudio.mp3studio.singalong.TakePreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SingAlongViewModel(
    private val session: SingAlongSession,
    private val appSettings: AppSettings,
) : ViewModel() {

    val state: StateFlow<SingAlongState> = session.state
    val mix: StateFlow<MixSettings> = session.mix

    /** Null until loaded, so the note doesn't flash for people who have already seen it. */
    val noteSeen: StateFlow<Boolean?> = appSettings.singAlongNoteSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preview: TakePreview? get() = session.preview

    fun level(): Float = session.level()

    fun hasSpaceFor(durationMs: Long): Boolean = session.hasSpaceFor(durationMs)

    fun start(song: Song, options: SingAlongOptions) {
        viewModelScope.launch { appSettings.setSingAlongNoteSeen() }
        session.start(song, options)
    }

    fun stop() = session.stop()

    fun retake() = session.retake()

    fun cancel() = session.cancel()

    fun updateMix(transform: (MixSettings) -> MixSettings) = session.updateMix(transform)

    fun save(name: String) = session.save(name)

    fun close() = session.close()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                SingAlongViewModel(app.container.singAlongSession, app.container.appSettings)
            }
        }
    }
}
