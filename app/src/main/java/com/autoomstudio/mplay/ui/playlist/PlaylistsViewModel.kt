package com.autoomstudio.mplay.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.model.Playlist
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.playlist.PlaylistQueries
import com.autoomstudio.mplay.data.playlist.PlaylistRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface PlaylistActions {
    /** Creates a playlist and, when [firstSong] is given, adds it straight away. */
    fun createPlaylist(name: String, firstSong: Song? = null)
    fun renamePlaylist(playlist: Playlist, name: String)
    fun deletePlaylist(playlist: Playlist)
    fun addToPlaylist(playlist: Playlist, song: Song)
    fun removeFromPlaylist(playlist: Playlist, song: Song)
    fun reorderPlaylist(playlist: Playlist, songs: List<Song>)
}

/** One-shot results shown in a snackbar. */
sealed interface PlaylistMessage {
    data class Created(val name: String) : PlaylistMessage
    data class Added(val name: String) : PlaylistMessage
    data class AlreadyIn(val name: String) : PlaylistMessage
    data class Deleted(val name: String) : PlaylistMessage
}

class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel(), PlaylistActions {

    /** Null until the library has loaded, so songs are not briefly reported as unavailable. */
    private val librarySongs = MutableStateFlow<List<Song>?>(null)

    /** Null while loading; otherwise playlists resolved against the current library. */
    val playlists: StateFlow<List<Playlist>?> =
        combine(repository.playlists, librarySongs.filterNotNull()) { stored, songs ->
            val songsById = songs.associateBy { it.id }
            stored.map { PlaylistQueries.resolvePlaylist(it, songsById) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<PlaylistMessage>(Channel.BUFFERED)
    val messages: Flow<PlaylistMessage> = _messages.receiveAsFlow()

    fun onLibraryChanged(songs: List<Song>) {
        librarySongs.value = songs
    }

    override fun createPlaylist(name: String, firstSong: Song?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.create(trimmed)
            if (firstSong != null) {
                repository.addSongs(id, listOf(firstSong))
                _messages.send(PlaylistMessage.Added(trimmed))
            } else {
                _messages.send(PlaylistMessage.Created(trimmed))
            }
        }
    }

    override fun renamePlaylist(playlist: Playlist, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.rename(playlist.id, name) }
    }

    override fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.delete(playlist.id)
            _messages.send(PlaylistMessage.Deleted(playlist.name))
        }
    }

    override fun addToPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch {
            val added = repository.addSongs(playlist.id, listOf(song))
            _messages.send(
                if (added > 0) PlaylistMessage.Added(playlist.name) else PlaylistMessage.AlreadyIn(playlist.name),
            )
        }
    }

    override fun removeFromPlaylist(playlist: Playlist, song: Song) {
        viewModelScope.launch { repository.remove(playlist.id, song) }
    }

    override fun reorderPlaylist(playlist: Playlist, songs: List<Song>) {
        viewModelScope.launch { repository.reorder(playlist.id, songs) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                PlaylistsViewModel(app.container.playlistRepository)
            }
        }
    }
}
