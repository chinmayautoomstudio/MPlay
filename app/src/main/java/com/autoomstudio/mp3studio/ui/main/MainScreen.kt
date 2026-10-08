package com.autoomstudio.mp3studio.ui.main

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.R
import com.autoomstudio.mp3studio.data.library.SongSortOrder
import com.autoomstudio.mp3studio.data.model.Playlist
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.duplicates.DuplicateIndex
import com.autoomstudio.mp3studio.data.settings.ThemeMode
import com.autoomstudio.mp3studio.data.stems.StemMode
import com.autoomstudio.mp3studio.data.settings.ThemeSettings
import com.autoomstudio.mp3studio.playback.NowPlayingState
import com.autoomstudio.mp3studio.data.library.SongDeleter
import com.autoomstudio.mp3studio.ui.admin.AdminScreen
import com.autoomstudio.mp3studio.ui.common.rememberWithLegacyStorage
import com.autoomstudio.mp3studio.ui.components.MPlayTopBar
import com.autoomstudio.mp3studio.ui.components.MetronomeIcons
import com.autoomstudio.mp3studio.ui.metronome.MetronomeScreen
import com.autoomstudio.mp3studio.ui.components.SelectionTopBar
import com.autoomstudio.mp3studio.ui.duplicates.DuplicateActions
import com.autoomstudio.mp3studio.ui.duplicates.ReviewDuplicatesScreen
import com.autoomstudio.mp3studio.ui.library.DeleteResult
import com.autoomstudio.mp3studio.ui.library.DeleteSongsHost
import com.autoomstudio.mp3studio.ui.library.LibraryScreen
import com.autoomstudio.mp3studio.ui.library.LibraryUiState
import com.autoomstudio.mp3studio.ui.library.SongActions
import com.autoomstudio.mp3studio.ui.library.SongInfoHost
import com.autoomstudio.mp3studio.ui.library.rememberSongSelection
import com.autoomstudio.mp3studio.data.plan.Feature
import com.autoomstudio.mp3studio.ui.plans.PlansScreen
import com.autoomstudio.mp3studio.ui.plans.PlansViewModel
import com.autoomstudio.mp3studio.ui.plans.UpgradeSheet
import com.autoomstudio.mp3studio.ui.playback.ExpandablePlayer
import com.autoomstudio.mp3studio.ui.playback.MiniPlayerSlotHeight
import com.autoomstudio.mp3studio.ui.playback.PlayerActions
import com.autoomstudio.mp3studio.ui.playback.PlayerSheetValue
import com.autoomstudio.mp3studio.ui.playback.animateSheetTo
import com.autoomstudio.mp3studio.ui.playback.expandProgress
import com.autoomstudio.mp3studio.ui.playback.rememberPlayerSheetFlingBehavior
import com.autoomstudio.mp3studio.ui.playback.rememberPlayerSheetState
import com.autoomstudio.mp3studio.ui.playback.updateSheetAnchors
import com.autoomstudio.mp3studio.ui.playlist.AddToPlaylistSheet
import com.autoomstudio.mp3studio.ui.playlist.PlaylistActions
import com.autoomstudio.mp3studio.ui.playlist.PlaylistMessage
import com.autoomstudio.mp3studio.ui.playlist.PlaylistNameDialog
import com.autoomstudio.mp3studio.ui.theme.fadeThrough
import com.autoomstudio.mp3studio.ui.playlist.PlaylistsScreen
import com.autoomstudio.mp3studio.separation.ModelState
import com.autoomstudio.mp3studio.ui.separation.ModelMissingDialog
import com.autoomstudio.mp3studio.ui.separation.SeparationMessage
import com.autoomstudio.mp3studio.ui.separation.SeparationNoticeDialog
import com.autoomstudio.mp3studio.ui.singalong.SingAlongOverlay
import com.autoomstudio.mp3studio.ui.separation.SeparationProgressPill
import com.autoomstudio.mp3studio.ui.separation.SeparationScreen
import com.autoomstudio.mp3studio.ui.separation.SeparationUiState
import com.autoomstudio.mp3studio.ui.separation.SeparationViewModel
import com.autoomstudio.mp3studio.ui.separation.SeparatorUi
import com.autoomstudio.mp3studio.ui.separation.separationMessageText
import com.autoomstudio.mp3studio.ui.settings.AboutScreen
import com.autoomstudio.mp3studio.ui.settings.AccountViewModel
import com.autoomstudio.mp3studio.ui.settings.AppearanceSettingsScreen
import com.autoomstudio.mp3studio.ui.settings.EditProfileScreen
import com.autoomstudio.mp3studio.ui.settings.LibrarySettingsScreen
import com.autoomstudio.mp3studio.ui.settings.LicensesScreen
import com.autoomstudio.mp3studio.ui.settings.PlaybackSettingsScreen
import com.autoomstudio.mp3studio.ui.settings.ProfileScreen
import com.autoomstudio.mp3studio.ui.settings.ProfileTabIcon
import com.autoomstudio.mp3studio.ui.settings.SeparationSettingsScreen
import com.autoomstudio.mp3studio.ui.trim.TrimEditorActivity
import com.autoomstudio.mp3studio.ui.trim.TrimMode
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
    Metronome(R.string.nav_metronome, MetronomeIcons.Default, MetronomeIcons.Default),
    Profile(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
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
    openMetronomeRequest: Boolean,
    onOpenMetronomeHandled: () -> Unit,
    modifier: Modifier = Modifier,
    plansViewModel: PlansViewModel = viewModel(factory = PlansViewModel.Factory),
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
) {
    var destination by rememberSaveable { mutableStateOf(Destination.Library) }
    var previousDestination by rememberSaveable { mutableStateOf(Destination.Library) }
    val account by accountViewModel.account.collectAsStateWithLifecycle()
    val allSongs = (libraryState as? LibraryUiState.Content)?.allSongs.orEmpty()
    val duplicates = (libraryState as? LibraryUiState.Content)?.duplicates ?: DuplicateIndex.EMPTY
    // Hidden duplicate copies can still be deleted from the review screen.
    val deletableSongs = remember(allSongs, duplicates) {
        (allSongs + duplicates.groups.flatMap { it.songs }).distinctBy { it.id }
    }
    var profilePage by rememberSaveable { mutableStateOf(ProfilePage.Main) }
    val openProfile = {
        if (destination != Destination.Profile) previousDestination = destination
        destination = Destination.Profile
    }
    val openSeparation = {
        openProfile()
        profilePage = ProfilePage.SeparationQueue
    }
    LaunchedEffect(openSeparationRequest) {
        if (openSeparationRequest) {
            openSeparation()
            onShowNowPlayingChange(false)
            onOpenSeparationHandled()
        }
    }
    LaunchedEffect(openMetronomeRequest) {
        if (openMetronomeRequest) {
            destination = Destination.Metronome
            onShowNowPlayingChange(false)
            onOpenMetronomeHandled()
        }
    }
    val openPlans = {
        openProfile()
        profilePage = ProfilePage.Plans
    }
    val openPlansRequest by plansViewModel.openPlansRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openPlansRequest) {
        if (openPlansRequest) {
            openPlans()
            onShowNowPlayingChange(false)
            plansViewModel.onOpenPlansHandled()
        }
    }
    val plan by plansViewModel.state.collectAsStateWithLifecycle()
    var trialBannerDismissed by rememberSaveable { mutableStateOf(false) }

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
    val currentSongId by rememberUpdatedState(nowPlaying?.songId)
    LaunchedEffect(separationViewModel) {
        separationViewModel.messages.collect { message ->
            val text = separationMessageText(context, message)
            val playInstrumental = message is SeparationMessage.Ready && message.songId == currentSongId
            val partial = message is SeparationMessage.PartiallyQueued
            scope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = text,
                    actionLabel = when {
                        message is SeparationMessage.Queued -> resources.getString(R.string.action_view_queue)
                        partial -> resources.getString(R.string.upgrade_see_plans)
                        playInstrumental -> resources.getString(R.string.message_separation_ready_action)
                        else -> null
                    },
                    duration = if (message is SeparationMessage.Ready || partial) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    when {
                        playInstrumental -> playerActions.setStemMode(StemMode.Instrumental)
                        partial -> plansViewModel.openPlans()
                        else -> openSeparation()
                    }
                }
            }
        }
    }

    var infoSongId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addToPlaylistSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var newPlaylistSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var deleteSongIds by rememberSaveable { mutableStateOf<LongArray?>(null) }
    // Offered whenever the phone qualifies; a missing model is explained when the action is picked.
    val canSeparate = separationState.deviceEligible
    val withStorage = rememberWithLegacyStorage(separationViewModel::exportPermissionDenied)
    val songActions = remember(onPlayNext, onAddToQueue, resources, context, canSeparate, withStorage) {
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
            onSaveStem = { song, mode -> withStorage { separationViewModel.export(song.id, mode) } },
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
            snackbarHost = { if (contentBackEnabled) SnackbarHost(snackbarHostState) },
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
                } else if (
                    destination != Destination.Profile ||
                    (profilePage != ProfilePage.Main && profilePage != ProfilePage.Admin)
                ) {
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
                        avatarUrl = account?.avatarUrl,
                        onSelect = {
                            if (it != Destination.Library && searchActive) closeSearch()
                            if (it != destination) {
                                selection.clear()
                                previousDestination = destination
                            }
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(Modifier.fillMaxSize()) {
                    if (plan.showTrialReminder && !trialBannerDismissed) {
                        TrialBanner(
                            daysLeft = plan.trialDaysLeft ?: 0,
                            onOpenPlans = openPlans,
                            onDismiss = { trialBannerDismissed = true },
                        )
                    }
                    AnimatedContent(
                        targetState = destination,
                        transitionSpec = { fadeThrough() },
                        label = "destination",
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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

                            Destination.Metronome -> MetronomeScreen(modifier = contentModifier)

                            Destination.Profile -> AnimatedContent(
                                targetState = profilePage,
                                transitionSpec = { fadeThrough() },
                                label = "profilePage",
                            ) { page ->
                                val pageBackEnabled = screenBackEnabled && profilePage == page
                                val backToMain = { profilePage = ProfilePage.Main }
                                when (page) {
                                    ProfilePage.Main -> ProfileScreen(
                                        themeMode = themeSettings.mode,
                                        onBack = {
                                            destination = previousDestination.takeUnless { it == Destination.Profile }
                                                ?: Destination.Library
                                        },
                                        onOpenEdit = { profilePage = ProfilePage.EditProfile },
                                        onOpenPlans = { profilePage = ProfilePage.Plans },
                                        onOpenAdmin = { profilePage = ProfilePage.Admin },
                                        onOpenAppearance = { profilePage = ProfilePage.Appearance },
                                        onOpenLibrary = { profilePage = ProfilePage.Library },
                                        onOpenSeparation = { profilePage = ProfilePage.Separation },
                                        onOpenPlayback = { profilePage = ProfilePage.Playback },
                                        onOpenAbout = { profilePage = ProfilePage.About },
                                        modifier = contentModifier,
                                        accountViewModel = accountViewModel,
                                        plansViewModel = plansViewModel,
                                    )
                                    ProfilePage.EditProfile -> EditProfileScreen(
                                        onBack = backToMain,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                        viewModel = accountViewModel,
                                    )
                                    ProfilePage.Appearance -> AppearanceSettingsScreen(
                                        theme = themeSettings,
                                        onThemeModeChange = onThemeModeChange,
                                        onDynamicColorChange = onDynamicColorChange,
                                        onBack = backToMain,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.Library -> LibrarySettingsScreen(
                                        hideDuplicates = duplicateActions.hideDuplicates,
                                        onHideDuplicatesChange = duplicateActions.onHideDuplicatesChange,
                                        duplicateGroupCount = duplicates.groups.size,
                                        onReviewDuplicates = { profilePage = ProfilePage.Duplicates },
                                        onBack = backToMain,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.Duplicates -> ReviewDuplicatesScreen(
                                        duplicates = duplicates,
                                        actions = duplicateActions,
                                        onDeleteHidden = { songs -> deleteSongIds = songs.map { it.id }.toLongArray() },
                                        onBack = { profilePage = ProfilePage.Library },
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.Separation -> SeparationSettingsScreen(
                                        state = separationState,
                                        viewModel = separationViewModel,
                                        onOpenQueue = { profilePage = ProfilePage.SeparationQueue },
                                        onBack = backToMain,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                        usage = plan.usage,
                                    )
                                    ProfilePage.SeparationQueue -> SeparationScreen(
                                        state = separationState,
                                        viewModel = separationViewModel,
                                        onBack = { profilePage = ProfilePage.Separation },
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                        usage = plan.usage,
                                    )
                                    ProfilePage.Playback -> PlaybackSettingsScreen(
                                        onBack = backToMain,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.About -> AboutScreen(
                                        onBack = backToMain,
                                        onOpenLicenses = { profilePage = ProfilePage.Licenses },
                                        onMessage = showMessage,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.Licenses -> LicensesScreen(
                                        onBack = { profilePage = ProfilePage.About },
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.Plans -> PlansScreen(
                                        onBack = backToMain,
                                        onMessage = showMessage,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                    ProfilePage.Admin -> AdminScreen(
                                        onBack = backToMain,
                                        onMessage = showMessage,
                                        modifier = contentModifier,
                                        backEnabled = pageBackEnabled,
                                    )
                                }
                            }
                        }
                    }
                }
                val onQueueScreen = destination == Destination.Profile && profilePage == ProfilePage.SeparationQueue
                SeparationProgressPill(
                    job = separationState.runningJob.takeUnless { onQueueScreen },
                    onClick = openSeparation,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        val state = lastNowPlaying
        if (state != null && miniSlotAttached && !collapsedTop.isNaN()) {
            val currentSong = allSongs.firstOrNull { it.id == state.songId }
            val separator = if (canSeparate && currentSong != null) {
                SeparatorUi(
                    job = separationState.activeJobs[currentSong.id],
                    onSeparate = { separationViewModel.requestSeparation(listOf(currentSong)) },
                    onCancel = separationViewModel::cancel,
                    onRetry = separationViewModel::retry,
                )
            } else {
                null
            }
            ExpandablePlayer(
                state = state,
                position = position,
                actions = playerActions,
                sheetState = sheetState,
                sheetFling = sheetFling,
                collapsedTop = { collapsedTop },
                onExpand = { onShowNowPlayingChange(true) },
                onCollapse = { onShowNowPlayingChange(false) },
                song = currentSong,
                songActions = songActions,
                suggestedSleepMinutes = suggestedSleepMinutes,
                separator = separator,
            )
        }
        if (!contentBackEnabled) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
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
    noticeRequest?.let { request ->
        SeparationNoticeDialog(
            request = request,
            onConfirm = separationViewModel::confirmNotice,
            onDismiss = separationViewModel::dismissNotice,
            usage = plan.usage,
        )
    }
    val limitReachedAt by separationViewModel.limitReachedAt.collectAsStateWithLifecycle()
    limitReachedAt?.let { resetsAt ->
        UpgradeSheet(
            feature = Feature.UnlimitedSeparator,
            onDismiss = separationViewModel::dismissLimitReached,
            limitResetsAt = resetsAt,
            viewModel = plansViewModel,
        )
    }
    val modelRequest by separationViewModel.modelRequest.collectAsStateWithLifecycle()
    val missingModel = separationState.modelState as? ModelState.NotInstalled
    if (modelRequest != null && missingModel != null) {
        ModelMissingDialog(state = missingModel, viewModel = separationViewModel)
    }
    SingAlongOverlay()
}

private enum class ProfilePage {
    Main,
    EditProfile,
    Appearance,
    Library,
    Duplicates,
    Separation,
    SeparationQueue,
    Playback,
    About,
    Licenses,
    Plans,
    Admin,
}

/** Last days of the free trial (PRD TR6); tapping it opens Plans. */
@Composable
private fun TrialBanner(daysLeft: Int, onOpenPlans: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        onClick = onOpenPlans,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        ) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                pluralStringResource(R.plurals.trial_banner, daysLeft, daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.trial_banner_dismiss))
            }
        }
    }
}

@Composable
private fun MPlayNavigationBar(
    selected: Destination,
    avatarUrl: String?,
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
                    if (item == Destination.Profile) {
                        ProfileTabIcon(avatarUrl = avatarUrl, selected = isSelected)
                    } else {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = null,
                        )
                    }
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
