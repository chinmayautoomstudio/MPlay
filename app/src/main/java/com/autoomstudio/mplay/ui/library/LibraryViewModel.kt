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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object NoPermission : LibraryUiState
    data object Empty : LibraryUiState
    data class Content(val songs: List<Song>) : LibraryUiState
}

class LibraryViewModel(private val songRepository: SongRepository) : ViewModel() {

    private val permissionGranted = MutableStateFlow<Boolean?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = permissionGranted
        .flatMapLatest { granted ->
            when (granted) {
                null -> flowOf(LibraryUiState.Loading)
                false -> flowOf(LibraryUiState.NoPermission)
                true -> songRepository.songs()
                    .map { songs ->
                        if (songs.isEmpty()) LibraryUiState.Empty else LibraryUiState.Content(songs)
                    }
                    .onStart { emit(LibraryUiState.Loading) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState.Loading)

    fun onPermissionChanged(granted: Boolean) {
        permissionGranted.value = granted
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                LibraryViewModel(app.container.songRepository)
            }
        }
    }
}
