package com.autoomstudio.mp3studio.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.library.AudioFolder
import com.autoomstudio.mp3studio.data.library.FolderPaths
import com.autoomstudio.mp3studio.data.library.LibraryPreferences
import com.autoomstudio.mp3studio.data.library.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExcludeFoldersUiState(
    val folders: List<AudioFolder> = emptyList(),
    val excluded: Set<String> = emptySet(),
    val loading: Boolean = true,
)

class ExcludeFoldersViewModel(
    private val songRepository: SongRepository,
    private val libraryPreferences: LibraryPreferences,
) : ViewModel() {

    private val mediaFolders = MutableStateFlow<List<AudioFolder>?>(null)

    val uiState: StateFlow<ExcludeFoldersUiState> = combine(
        mediaFolders,
        libraryPreferences.excludedFolders,
    ) { media, excluded ->
        if (media == null) {
            ExcludeFoldersUiState(loading = true)
        } else {
            val known = media.associateBy { it.relativePath }
            val orphans = excluded
                .filter { it !in known }
                .map { AudioFolder(relativePath = it, songCount = 0) }
            val folders = (media + orphans).sortedBy { it.relativePath.lowercase() }
            ExcludeFoldersUiState(folders = folders, excluded = excluded, loading = false)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExcludeFoldersUiState(),
    )

    init {
        viewModelScope.launch {
            mediaFolders.value = songRepository.loadAudioFolders()
        }
    }

    fun setFolderExcluded(path: String, excluded: Boolean) {
        viewModelScope.launch {
            libraryPreferences.setFolderExcluded(FolderPaths.normalize(path), excluded)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                ExcludeFoldersViewModel(
                    app.container.songRepository,
                    app.container.libraryPreferences,
                )
            }
        }
    }
}
