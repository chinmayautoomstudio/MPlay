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
import com.autoomstudio.mplay.playback.SleepTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaybackViewModel(container: AppContainer) : ViewModel(), PlayerActions {

    private val controller = container.createPlaybackController(viewModelScope).also { it.connect() }
    private val appSettings = container.appSettings

    val state: StateFlow<NowPlayingState?> = controller.state
    val position: Flow<Long> = controller.positionMs()

    /** Preselected duration in the sleep timer sheet: the last one used, or the default. */
    val suggestedSleepMinutes: StateFlow<Int> = appSettings.lastSleepMinutes
        .map { it ?: SleepTimer.DEFAULT_MINUTES }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SleepTimer.DEFAULT_MINUTES)

    /** Queues [songs] and starts playback from [song]. */
    fun onSongClick(songs: List<Song>, song: Song) {
        controller.playQueue(songs, songs.indexOfFirst { it.id == song.id })
    }

    /** Queues [songs] with shuffle on, starting from a random song. */
    fun onShuffle(songs: List<Song>) {
        controller.shuffleQueue(songs)
    }

    fun onPlayNext(songs: List<Song>) = controller.playNext(songs)

    fun onAddToQueue(songs: List<Song>) = controller.addToQueue(songs)

    fun removeFromQueue(songIds: Set<Long>) = controller.removeSongs(songIds)

    override fun playPause() = controller.playPause()
    override fun next() = controller.next()
    override fun previous() = controller.previous()
    override fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    override fun toggleShuffle() = controller.toggleShuffle()
    override fun cycleRepeat() = controller.cycleRepeat()

    override fun setSleepTimer(minutes: Int) {
        controller.setSleepTimer(minutes)
        viewModelScope.launch { appSettings.setLastSleepMinutes(minutes) }
    }

    override fun setSleepTimerEndOfSong() = controller.setSleepTimerEndOfSong()
    override fun extendSleepTimer(minutes: Int) = controller.extendSleepTimer(minutes)
    override fun cancelSleepTimer() = controller.cancelSleepTimer()
    override fun setLofi(enabled: Boolean) = controller.setLofi(enabled)

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
