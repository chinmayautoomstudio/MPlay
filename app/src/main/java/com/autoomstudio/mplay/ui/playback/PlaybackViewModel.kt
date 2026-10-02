package com.autoomstudio.mplay.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.di.AppContainer
import com.autoomstudio.mplay.playback.NowPlayingState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class PlaybackViewModel(container: AppContainer) : ViewModel(), PlayerActions {

    private val controller = container.createPlaybackController(viewModelScope).also { it.connect() }

    val state: StateFlow<NowPlayingState?> = controller.state
    val position: Flow<Long> = controller.positionMs()

    /** Queues [songs] and starts playback from [song]. */
    fun onSongClick(songs: List<Song>, song: Song) {
        controller.playQueue(songs, songs.indexOfFirst { it.id == song.id })
    }

    /** Queues [songs] with shuffle on, starting from a random song. */
    fun onShuffle(songs: List<Song>) {
        controller.shuffleQueue(songs)
    }

    override fun playPause() = controller.playPause()
    override fun next() = controller.next()
    override fun previous() = controller.previous()
    override fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    override fun toggleShuffle() = controller.toggleShuffle()
    override fun cycleRepeat() = controller.cycleRepeat()

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
