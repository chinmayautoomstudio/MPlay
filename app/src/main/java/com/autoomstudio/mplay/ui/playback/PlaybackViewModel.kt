package com.autoomstudio.mplay.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.di.AppContainer
import kotlinx.coroutines.flow.StateFlow

class PlaybackViewModel(container: AppContainer) : ViewModel() {

    private val controller = container.createPlaybackController(viewModelScope).also { it.connect() }

    val currentSongId: StateFlow<Long?> = controller.currentSongId
    val isPlaying: StateFlow<Boolean> = controller.isPlaying

    /** Queues [songs] and starts playback from [song]. */
    fun onSongClick(songs: List<Song>, song: Song) {
        controller.playQueue(songs, songs.indexOfFirst { it.id == song.id })
    }

    override fun onCleared() {
        controller.release()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                PlaybackViewModel(app.container)
            }
        }
    }
}
