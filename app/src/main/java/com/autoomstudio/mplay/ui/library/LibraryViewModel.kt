package com.autoomstudio.mplay.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.data.model.Song
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object NoPermission : LibraryUiState
    data object Empty : LibraryUiState
    data class Content(val songs: List<Song>) : LibraryUiState
}

class LibraryViewModel(private val songRepository: SongRepository) : ViewModel() {

    private val permissionGranted = MutableStateFlow<Boolean?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = permissionGranted
        .flatMapLatest { granted ->
            when (granted) {
                null -> flowOf(LibraryUiState.Loading)
                false -> flowOf(LibraryUiState.NoPermission)
                true -> songRepository.songs()
                    .onEach { _isRefreshing.value = false }
                    .map { songs ->
                        if (songs.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(songs)
                    }
                    .onStart { emit(LibraryUiState.Loading) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    fun onPermissionChanged(granted: Boolean) {
        permissionGranted.value = granted
        if (granted) onAppForeground()
    }

    /** Indexes the audio folders, then reloads the list. The spinner clears on the next list emission. */
    fun refresh() {
        _isRefreshing.value = true
        viewModelScope.launch { songRepository.rescan() }
    }

    /** Picks up files added while the app was away, without showing the refresh spinner. */
    fun onAppForeground() {
        if (permissionGranted.value != true) return
        viewModelScope.launch { songRepository.rescanIfStale(FOREGROUND_SCAN_INTERVAL_MS) }
    }

    companion object {
        private const val FOREGROUND_SCAN_INTERVAL_MS = 2_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                LibraryViewModel(app.container.songRepository)
            }
        }
    }
}
