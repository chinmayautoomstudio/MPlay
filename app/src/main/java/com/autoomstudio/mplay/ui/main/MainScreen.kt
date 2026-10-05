package com.autoomstudio.mplay.ui.main

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.data.library.SongSortOrder
import com.autoomstudio.mplay.data.model.Playlist
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.duplicates.DuplicateIndex
import com.autoomstudio.mplay.data.settings.ThemeMode
import com.autoomstudio.mplay.data.settings.ThemeSettings
import com.autoomstudio.mplay.playback.NowPlayingState
import com.autoomstudio.mplay.data.library.SongDeleter
import com.autoomstudio.mplay.ui.components.MPlayTopBar
import com.autoomstudio.mplay.ui.components.SelectionTopBar
import com.autoomstudio.mplay.ui.duplicates.DuplicateActions
import com.autoomstudio.mplay.ui.duplicates.ReviewDuplicatesScreen
import com.autoomstudio.mplay.ui.library.DeleteResult
import com.autoomstudio.mplay.ui.library.DeleteSongsHost
import com.autoomstudio.mplay.ui.library.LibraryScreen
import com.autoomstudio.mplay.ui.library.LibraryUiState
import com.autoomstudio.mplay.ui.library.SongActions
import com.autoomstudio.mplay.ui.library.SongInfoHost
import com.autoomstudio.mplay.ui.library.rememberSongSelection
import com.autoomstudio.mplay.ui.playback.ExpandablePlayer
import com.autoomstudio.mplay.ui.playback.MiniPlayerSlotHeight
import com.autoomstudio.mplay.ui.playback.PlayerActions
import com.autoomstudio.mplay.ui.playback.PlayerSheetValue
import com.autoomstudio.mplay.ui.playback.animateSheetTo
import com.autoomstudio.mplay.ui.playback.expandProgress
import com.autoomstudio.mplay.ui.playback.rememberPlayerSheetFlingBehavior
import com.autoomstudio.mplay.ui.playback.rememberPlayerSheetState
import com.autoomstudio.mplay.ui.playback.updateSheetAnchors
import com.autoomstudio.mplay.ui.playlist.AddToPlaylistSheet
import com.autoomstudio.mplay.ui.playlist.PlaylistActions
import com.autoomstudio.mplay.ui.playlist.PlaylistMessage
import com.autoomstudio.mplay.ui.playlist.PlaylistNameDialog
import com.autoomstudio.mplay.ui.theme.fadeThrough
import com.autoomstudio.mplay.ui.playlist.PlaylistsScreen
import com.autoomstudio.mplay.ui.separation.SeparationMessage
import com.autoomstudio.mplay.ui.separation.SeparationNoticeDialog
import com.autoomstudio.mplay.ui.separation.SeparationScreen
import com.autoomstudio.mplay.ui.separation.SeparationSettingsSection
import com.autoomstudio.mplay.ui.separation.SeparationUiState
import com.autoomstudio.mplay.ui.separation.SeparationViewModel
import com.autoomstudio.mplay.ui.separation.separationMessageText
import com.autoomstudio.mplay.ui.settings.SettingsScreen
import com.autoomstudio.mplay.ui.trim.TrimEditorActivity
import com.autoomstudio.mplay.ui.trim.TrimMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private enum class Destination(
    @StringRes val label: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Library(R.string.nav_library, Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    Playlists(
        R.string.nav_playlists,
        Icons.AutoMirrored.Filled.QueueMusic,
        Icons.AutoMirrored.Outlined.QueueMusic,
    ),
    Settings(R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun MainScreen(
    libraryState: LibraryUiState,
    nowPlaying: NowPlayingState?,
    position: Flow<Long>,
    playerActions: PlayerActions,
    suggestedSleepMinutes: Int,
    showNowPlaying: Boolean,
    onShowNowPlayingChange: (Boolean) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    sortOrder: SongSortOrder,
    onSortOrderChange: (SongSortOrder) -> Unit,
    onPlay: (songs: List<Song>, start: Song) -> Unit,
    onShuffle: (songs: List<Song>) -> Unit,
    onPlayNext: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    playlists: List<Playlist>?,
    playlistActions: PlaylistActions,
    playlistMessages: Flow<PlaylistMessage>,
    songDeleter: SongDeleter,
    /** Called after files were deleted, to drop them from the queue and playlists. */
    onSongsDeleted: (Set<Long>) -> Unit,
    themeSettings: ThemeSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    duplicateActions: DuplicateActions,
    separationState: SeparationUiState,
    separationViewModel: SeparationViewModel,
    openSeparationRequest: Boolean,
    onOpenSeparationHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Library) }
    val allSongs = (libraryState as? LibraryUiState.Content)?.allSongs.orEmpty()
    val duplicates = (libraryState as? LibraryUiState.Content)?.duplicates ?: DuplicateIndex.EMPTY
    // Hidden duplicate copies can still be deleted from the review screen.
    val deletableSongs = remember(allSongs, duplicates) {
        (allSongs + duplicates.groups.flatMap { it.songs }).distinctBy { it.id }
    }
    var settingsPage by rememberSaveable { mutableStateOf(SettingsPage.Main) }
    val openSeparation = {
        destination = Destination.Settings
        settingsPage = SettingsPage.Separation
    }
    LaunchedEffect(openSeparationRequest) {
        if (openSeparationRequest) {
            openSeparation()
            onShowNowPlayingChange(false)
            onOpenSeparationHandled()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val showMessage: (String) -> Unit = { text ->
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(text)
        }
    }
    LaunchedEffect(playlistMessages) {
        playlistMessages.collect { message ->
            showMessage(
                when (message) {
                    is PlaylistMessage.Created -> resources.getString(R.string.message_playlist_created, message.name)
                    is PlaylistMessage.Added -> if (message.count == 1) {
                        resources.getString(R.string.message_added_to_playlist, message.name)
                    } else {
                        resources.getQuantityString(
                            R.plurals.message_songs_added_to_playlist,
                            message.count,
                            message.count,
                            message.name,
                        )
                    }
                    is PlaylistMessage.AlreadyIn ->
                        resources.getString(R.string.message_already_in_playlist, message.name)
                    is PlaylistMessage.Deleted -> resources.getString(R.string.message_playlist_deleted, message.name)
                },
            )
        }
    }
    val context = LocalContext.current
    LaunchedEffect(separationViewModel) {
        separationViewModel.messages.collect { message ->
            val text = separationMessageText(context, message)
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = text,
                    actionLabel = if (message is SeparationMessage.Queued) {
                        resources.getString(R.string.action_view_queue)
                    } else {
                        null
                    },
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) openSeparation()
            }
        }
    }

    var infoSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addToPlaylistSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var newPlaylistSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var deleteSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    val canSeparate = separationState.available
    val songActions = remember(onPlayNext, onAddToQueue, resources, context, canSeparate) {
        SongActions(
            onAddToPlaylist = { addToPlaylistSongIds = longArrayOf(it.id) },
            onPlayNext = {
                onPlayNext(listOf(it))
                showMessage(resources.getString(R.string.message_playing_next))
            },
            onAddToQueue = {
                onAddToQueue(listOf(it))
                showMessage(resources.getString(R.string.message_added_to_queue))
            },
            onCutAndSave = { context.startActivity(TrimEditorActivity.intent(context, it, TrimMode.Cut)) },
            onSetAsRingtone = { context.startActivity(TrimEditorActivity.intent(context, it, TrimMode.Ringtone)) },
            onShowInfo = { infoSongId = it.id },
            onDelete = { deleteSongIds = longArrayOf(it.id) },
            onSeparate = if (canSeparate) {
                { song -> separationViewModel.requestSeparation(listOf(song)) }
            } else {
                null
            },
        )
    }

    val selection = rememberSongSelection()
    val songsById = remember(allSongs) { allSongs.associateBy { it.id } }
    if (libraryState is LibraryUiState.Content || libraryState is LibraryUiState.Empty) {
        LaunchedEffect(songsById) { selection.retainOnly(songsById.keys) }
    }
    val selectedSongs: () -> List<Song> = { selection.selectedIds.mapNotNull { songsById[it] } }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    val closeSearch = {
        searchActive = false
        onClearSearch()
    }

    // Keeps the last song on screen while the player surfaces animate out after the queue is cleared.
    val lastNowPlaying = remember { mutableStateOf(nowPlaying) }
        .apply { if (nowPlaying != null) value = nowPlaying }
        .value

    val sheetExpanded = showNowPlaying && nowPlaying != null
    val sheetState = rememberPlayerSheetState(initiallyExpanded = sheetExpanded)
    val sheetFling = rememberPlayerSheetFlingBehavior(sheetState)
    LaunchedEffect(sheetExpanded) {
        val target = if (sheetExpanded) PlayerSheetValue.Expanded else PlayerSheetValue.Collapsed
        if (sheetState.settledValue != target || sheetState.targetValue != target) {
            sheetState.animateSheetTo(target)
        }
    }
    val hasNowPlaying by rememberUpdatedState(nowPlaying != null)
    val currentOnShowNowPlayingChange by rememberUpdatedState(onShowNowPlayingChange)
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.settledValue }
            .drop(1)
            .collect { value ->
                if (hasNowPlaying) currentOnShowNowPlayingChange(value == PlayerSheetValue.Expanded)
            }
    }

    // The player's back handler is registered first, so screen handlers would otherwise win while it is open.
    val contentBackEnabled = sheetState.targetValue != PlayerSheetValue.Expanded

    var collapsedTop by remember { mutableFloatStateOf(Float.NaN) }
    var miniSlotAttached by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (selection.isActive) {
                    val openPlaylist = selection.playlistId?.let { id -> playlists?.firstOrNull { it.id == id } }
                    SelectionTopBar(
                        count = selection.selectedIds.size,
                        onClose = selection::clear,
                        onAddToPlaylist = { addToPlaylistSongIds = selection.selectedIds.toLongArray() },
                        onPlayNext = {
                            val songs = selectedSongs()
                            onPlayNext(songs)
                            selection.clear()
                            showMessage(
                                resources.getQuantityString(R.plurals.message_songs_playing_next, songs.size, songs.size),
                            )
                        },
                        onAddToQueue = {
                            val songs = selectedSongs()
                            onAddToQueue(songs)
                            selection.clear()
                            showMessage(
                                resources.getQuantityString(R.plurals.message_songs_added_to_queue, songs.size, songs.size),
                            )
                        },
                        onDelete = { deleteSongIds = selection.selectedIds.toLongArray() },
                        onSeparate = if (canSeparate) {
                            {
                                separationViewModel.requestSeparation(selectedSongs())
                                selection.clear()
                            }
                        } else {
                            null
                        },
                        onRemoveFromPlaylist = openPlaylist?.let { playlist ->
                            {
                                val songs = selectedSongs()
                                playlistActions.removeFromPlaylist(playlist, songs)
                                selection.clear()
                                showMessage(
                                    resources.getQuantityString(
                                        R.plurals.message_songs_removed_from_playlist,
                                        songs.size,
                                        songs.size,
                                        playlist.name,
                                    ),
                                )
                            }
                        },
                    )
                } else {
                    MPlayTopBar(
                        showActions = destination == Destination.Library,
                        searchActive = searchActive && destination == Destination.Library,
                        searchQuery = searchQuery,
                        onSearchQueryChange = onSearchQueryChange,
                        onOpenSearch = { searchActive = true },
                        onCloseSearch = closeSearch,
                        sortOrder = sortOrder,
                        onSortOrderChange = onSortOrderChange,
                        backEnabled = contentBackEnabled,
                    )
                }
            },
            bottomBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                    AnimatedVisibility(
                        visible = nowPlaying != null,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        DisposableEffect(Unit) {
                            miniSlotAttached = true
                            onDispose { miniSlotAttached = false }
                        }
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(MiniPlayerSlotHeight)
                                .onGloballyPositioned { coordinates ->
                                    collapsedTop = coordinates.positionInRoot().y
                                    sheetState.updateSheetAnchors(collapsedTop)
                                },
                        )
                    }
                    MPlayNavigationBar(
                        selected = destination,
                        onSelect = {
                            if (it != Destination.Library && searchActive) closeSearch()
                            if (it != destination) selection.clear()
                            destination = it
                        },
                        modifier = Modifier.graphicsLayer {
                            translationY = sheetState.expandProgress * size.height
                        },
                    )
                }
            },
        ) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize()
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeThrough() },
                label = "destination",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { shown ->
                // The outgoing screen stays composed during the transition and must not claim Back.
                // While selecting, Back clears the selection instead.
                val screenBackEnabled = contentBackEnabled && shown == destination && !selection.isActive
                when (shown) {
                    Destination.Library -> LibraryScreen(
                        state = libraryState,
                        currentSongId = nowPlaying?.songId,
                        isPlaying = nowPlaying?.isPlaying == true,
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        searchQuery = searchQuery,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        actions = songActions,
                        selection = selection,
                        modifier = contentModifier,
                        backEnabled = screenBackEnabled,
                    )

                    Destination.Playlists -> PlaylistsScreen(
                        playlists = playlists,
                        currentSongId = nowPlaying?.songId,
                        isPlaying = nowPlaying?.isPlaying == true,
                        playlistActions = playlistActions,
                        songActions = songActions,
                        onPlay = onPlay,
                        onShuffle = onShuffle,
                        selection = selection,
                        modifier = contentModifier,
                        backEnabled = screenBackEnabled,
                    )

                    Destination.Settings -> AnimatedContent(
                        targetState = settingsPage,
                        transitionSpec = { fadeThrough() },
                        label = "settingsPage",
                    ) { page ->
                        val pageBackEnabled = screenBackEnabled && settingsPage == page
                        when (page) {
                            SettingsPage.Duplicates -> ReviewDuplicatesScreen(
                                duplicates = duplicates,
                                actions = duplicateActions,
                                onDeleteHidden = { songs -> deleteSongIds = songs.map { it.id }.toLongArray() },
                                onBack = { settingsPage = SettingsPage.Main },
                                modifier = contentModifier,
                                backEnabled = pageBackEnabled,
                            )
                            SettingsPage.Separation -> SeparationScreen(
                                state = separationState,
                                viewModel = separationViewModel,
                                onBack = { settingsPage = SettingsPage.Main },
                                modifier = contentModifier,
                                backEnabled = pageBackEnabled,
                            )
                            SettingsPage.Main -> SettingsScreen(
                                theme = themeSettings,
                                onThemeModeChange = onThemeModeChange,
                                onDynamicColorChange = onDynamicColorChange,
                                hideDuplicates = duplicateActions.hideDuplicates,
                                onHideDuplicatesChange = duplicateActions.onHideDuplicatesChange,
                                duplicateGroupCount = duplicates.groups.size,
                                onReviewDuplicates = { settingsPage = SettingsPage.Duplicates },
                                modifier = contentModifier,
                                separationSection = { header ->
                                    SeparationSettingsSection(
                                        state = separationState,
                                        viewModel = separationViewModel,
                                        onOpenQueue = { settingsPage = SettingsPage.Separation },
                                        sectionHeader = header,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        val state = lastNowPlaying
        if (state != null && miniSlotAttached && !collapsedTop.isNaN()) {
            ExpandablePlayer(
                state = state,
                position = position,
                actions = playerActions,
                sheetState = sheetState,
                sheetFling = sheetFling,
                collapsedTop = { collapsedTop },
                onExpand = { onShowNowPlayingChange(true) },
                onCollapse = { onShowNowPlayingChange(false) },
                song = allSongs.firstOrNull { it.id == state.songId },
                songActions = songActions,
                suggestedSleepMinutes = suggestedSleepMinutes,
            )
        }
    }

    // Registered after the screens' handlers so it wins while songs are selected.
    BackHandler(enabled = contentBackEnabled && selection.isActive) { selection.clear() }

    addToPlaylistSongIds?.let { ids -> ids.toList().mapNotNull { songsById[it] } }?.takeIf { it.isNotEmpty() }?.let { songs ->
        AddToPlaylistSheet(
            playlists = playlists.orEmpty(),
            onPick = {
                playlistActions.addToPlaylist(it, songs)
                addToPlaylistSongIds = null
                selection.clear()
            },
            onNewPlaylist = {
                newPlaylistSongIds = addToPlaylistSongIds
                addToPlaylistSongIds = null
            },
            onDismiss = { addToPlaylistSongIds = null },
        )
    }
    newPlaylistSongIds?.let { ids -> ids.toList().mapNotNull { songsById[it] } }?.takeIf { it.isNotEmpty() }?.let { songs ->
        PlaylistNameDialog(
            title = stringResource(R.string.playlist_new),
            confirmLabel = stringResource(R.string.playlist_create),
            onConfirm = {
                playlistActions.createPlaylist(it, firstSongs = songs)
                newPlaylistSongIds = null
                selection.clear()
            },
            onDismiss = { newPlaylistSongIds = null },
        )
    }
    DeleteSongsHost(
        request = deleteSongIds,
        allSongs = deletableSongs,
        deleter = songDeleter,
        onResult = { result ->
            deleteSongIds = null
            when (result) {
                is DeleteResult.Deleted -> {
                    onSongsDeleted(result.ids)
                    selection.retainOnly(selection.selectedIds - result.ids)
                    showMessage(
                        if (result.ids.size < result.requested) {
                            resources.getString(R.string.message_songs_partly_deleted, result.ids.size, result.requested)
                        } else {
                            resources.getQuantityString(R.plurals.message_songs_deleted, result.ids.size, result.ids.size)
                        },
                    )
                }
                DeleteResult.Cancelled -> Unit
                DeleteResult.Failed -> showMessage(resources.getString(R.string.message_delete_failed))
                DeleteResult.PermissionDenied ->
                    showMessage(resources.getString(R.string.message_delete_needs_permission))
            }
        },
    )
    SongInfoHost(songs = allSongs, songId = infoSongId, onDismiss = { infoSongId = null })
    val noticeRequest by separationViewModel.noticeRequest.collectAsStateWithLifecycle()
    if (noticeRequest != null) {
        SeparationNoticeDialog(
            onConfirm = separationViewModel::confirmNotice,
            onDismiss = separationViewModel::dismissNotice,
        )
    }
}

private enum class SettingsPage { Main, Duplicates, Separation }

@Composable
private fun MPlayNavigationBar(
    selected: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Destination.entries.forEach { item ->
            val isSelected = item == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(item.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
