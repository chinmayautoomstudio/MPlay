package com.autoomstudio.mplay.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.data.duplicates.DuplicateGroup
import com.autoomstudio.mplay.data.duplicates.DuplicateIndex
import com.autoomstudio.mplay.data.duplicates.DuplicateRepository
import com.autoomstudio.mplay.data.library.LibraryPreferences
import com.autoomstudio.mplay.data.library.LibraryQueries
import com.autoomstudio.mplay.data.library.SongRepository
import com.autoomstudio.mplay.data.library.SongSortOrder
import com.autoomstudio.mplay.data.model.Album
import com.autoomstudio.mplay.data.model.Artist
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.settings.AppSettings
import com.autoomstudio.mplay.data.stems.StemRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object NoPermission : LibraryUiState
    data object Empty : LibraryUiState

    /**
     * [songs] are filtered and sorted; [albums] and [artists] are grouped from the filtered songs.
     * [allSongs] is the unfiltered library, so album and artist screens stay complete during a search.
     * While [hidingDuplicates], all of them leave out the extra copies listed in [duplicates].
     */
    data class Content(
        val songs: List<Song>,
        val albums: List<Album>,
        val artists: List<Artist>,
        val isFiltered: Boolean,
        val allSongs: List<Song>,
        val duplicates: DuplicateIndex = DuplicateIndex.EMPTY,
        val hidingDuplicates: Boolean = false,
    ) : LibraryUiState {
        /** The visible song standing in for [songId], so playlist entries for a hidden copy play the kept one. */
        fun canonicalId(songId: Long): Long = if (hidingDuplicates) duplicates.canonicalId(songId) else songId
    }
}

class LibraryViewModel(
    private val songRepository: SongRepository,
    private val libraryPreferences: LibraryPreferences,
    private val duplicateRepository: DuplicateRepository,
    private val appSettings: AppSettings,
    private val stemRepository: StemRepository,
) : ViewModel() {

    val hideDuplicates: StateFlow<Boolean> = appSettings.hideDuplicates
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val permissionGranted = MutableStateFlow<Boolean?>(null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val sortOrder: StateFlow<SongSortOrder> = libraryPreferences.sortOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, SongSortOrder.Title)

    @OptIn(FlowPreview::class)
    private val effectiveQuery = _searchQuery
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = permissionGranted
        .flatMapLatest { granted ->
            when (granted) {
                null -> flowOf(LibraryUiState.Loading)
                false -> flowOf(LibraryUiState.NoPermission)
                true -> combine(
                    songRepository.songs()
                        .onEach { songs ->
                            _isRefreshing.value = false
                            stemRepository.onLibraryChanged(songs)
                        }
                        .flatMapLatest { songs ->
                            duplicateRepository.index(songs).map { index -> songs to index }
                        },
                    effectiveQuery,
                    libraryPreferences.sortOrder,
                    appSettings.hideDuplicates,
                ) { (songs, index), query, order, hide -> buildState(songs, index, hide, query, order) }
                    .flowOn(Dispatchers.Default)
                    .onStart { emit(LibraryUiState.Loading) }
                    // Access can be revoked between the resume check and the query.
                    .catch { e ->
                        if (e !is SecurityException) throw e
                        _isRefreshing.value = false
                        emit(LibraryUiState.NoPermission)
                    }
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

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun onSortOrderChange(order: SongSortOrder) {
        viewModelScope.launch { libraryPreferences.setSortOrder(order) }
    }

    fun setHideDuplicates(enabled: Boolean) {
        viewModelScope.launch { appSettings.setHideDuplicates(enabled) }
    }

    fun keepDuplicate(song: Song, group: DuplicateGroup) {
        viewModelScope.launch { duplicateRepository.keep(song.id, group) }
    }

    fun restoreDuplicate(song: Song) {
        viewModelScope.launch { duplicateRepository.restore(song.id) }
    }

    fun hideDuplicateAgain(song: Song) {
        viewModelScope.launch { duplicateRepository.hideAgain(song.id) }
    }

    private fun buildState(
        songs: List<Song>,
        duplicates: DuplicateIndex,
        hideDuplicates: Boolean,
        query: String,
        order: SongSortOrder,
    ): LibraryUiState {
        if (songs.isEmpty()) return LibraryUiState.Empty
        val shown = if (hideDuplicates) duplicates.visible(songs) else songs
        val filtered = LibraryQueries.filterSongs(shown, query)
        return LibraryUiState.Content(
            songs = LibraryQueries.sortSongs(filtered, order),
            albums = LibraryQueries.groupAlbums(filtered),
            artists = LibraryQueries.groupArtists(filtered),
            isFiltered = query.isNotEmpty(),
            allSongs = shown,
            duplicates = duplicates,
            hidingDuplicates = hideDuplicates,
        )
    }

    companion object {
        private const val FOREGROUND_SCAN_INTERVAL_MS = 2_000L
        private const val SEARCH_DEBOUNCE_MS = 250L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MPlayApp
                LibraryViewModel(
                    app.container.songRepository,
                    app.container.libraryPreferences,
                    app.container.duplicateRepository,
                    app.container.appSettings,
                    app.container.stemRepository,
                )
            }
        }
    }
}
