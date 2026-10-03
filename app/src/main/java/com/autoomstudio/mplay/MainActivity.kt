package com.autoomstudio.mplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.data.settings.ThemeSettings
import com.autoomstudio.mplay.ui.duplicates.DuplicateActions
import com.autoomstudio.mplay.ui.library.LibraryUiState
import com.autoomstudio.mplay.ui.library.LibraryViewModel
import com.autoomstudio.mplay.ui.main.MainScreen
import com.autoomstudio.mplay.ui.permission.PermissionRationaleScreen
import com.autoomstudio.mplay.ui.permission.rememberAudioPermissionState
import com.autoomstudio.mplay.ui.playback.PlaybackViewModel
import com.autoomstudio.mplay.ui.playlist.PlaylistsViewModel
import com.autoomstudio.mplay.ui.settings.SettingsViewModel
import com.autoomstudio.mplay.ui.theme.MPlayAppTheme

class MainActivity : ComponentActivity() {

    /** Set when launched from the media notification; consumed once by the UI. */
    private var openNowPlayingRequest by mutableStateOf(false)

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Holds the splash until the saved theme is known, so a forced light or dark theme doesn't flash.
        splash.setKeepOnScreenCondition { settingsViewModel.theme.value == null }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val theme = settingsViewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            MPlayAppTheme(activity = this, settings = theme) {
                MPlayRoot(
                    openNowPlayingRequest = openNowPlayingRequest,
                    onOpenNowPlayingHandled = { openNowPlayingRequest = false },
                    themeSettings = theme,
                    settingsViewModel = settingsViewModel,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlayingRequest = true
        }
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.autoomstudio.mplay.extra.OPEN_NOW_PLAYING"
    }
}

@Composable
private fun MPlayRoot(
    openNowPlayingRequest: Boolean,
    onOpenNowPlayingHandled: () -> Unit,
    themeSettings: ThemeSettings,
    settingsViewModel: SettingsViewModel,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    playbackViewModel: PlaybackViewModel = viewModel(factory = PlaybackViewModel.Factory),
    playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory),
) {
    val songDeleter = (LocalContext.current.applicationContext as MPlayApp).container.songDeleter
    val permission = rememberAudioPermissionState()
    LaunchedEffect(permission.isGranted) {
        viewModel.onPermissionChanged(permission.isGranted)
    }
    NotificationPermissionPrompt(
        audioGranted = permission.isGranted,
        claimPrompt = settingsViewModel::claimNotificationPrompt,
    )
    LifecycleResumeEffect(viewModel) {
        viewModel.onAppForeground()
        onPauseOrDispose { }
    }

    var showNowPlaying by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openNowPlayingRequest) {
        if (openNowPlayingRequest) {
            showNowPlaying = true
            onOpenNowPlayingHandled()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val nowPlaying by playbackViewModel.state.collectAsStateWithLifecycle()
    val suggestedSleepMinutes by playbackViewModel.suggestedSleepMinutes.collectAsStateWithLifecycle()
    val playlists by playlistsViewModel.playlists.collectAsStateWithLifecycle()
    val content = uiState as? LibraryUiState.Content
    LaunchedEffect(content?.allSongs, content?.duplicates, content?.hidingDuplicates, uiState is LibraryUiState.Empty) {
        when (val state = uiState) {
            is LibraryUiState.Content -> playlistsViewModel.onLibraryChanged(state.allSongs, state::canonicalId)
            LibraryUiState.Empty -> playlistsViewModel.onLibraryChanged(emptyList())
            else -> Unit
        }
    }
    val hideDuplicates by viewModel.hideDuplicates.collectAsStateWithLifecycle()
    if (uiState is LibraryUiState.NoPermission) {
        PermissionRationaleScreen(
            permanentlyDenied = permission.isPermanentlyDenied,
            onGrant = permission::request,
            onOpenSettings = permission::openSettings,
        )
    } else {
        MainScreen(
            libraryState = uiState,
            nowPlaying = nowPlaying,
            position = playbackViewModel.position,
            playerActions = playbackViewModel,
            suggestedSleepMinutes = suggestedSleepMinutes,
            showNowPlaying = showNowPlaying,
            onShowNowPlayingChange = { showNowPlaying = it },
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            searchQuery = searchQuery,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onClearSearch = viewModel::clearSearch,
            sortOrder = sortOrder,
            onSortOrderChange = viewModel::onSortOrderChange,
            onPlay = playbackViewModel::onSongClick,
            onShuffle = playbackViewModel::onShuffle,
            onPlayNext = playbackViewModel::onPlayNext,
            onAddToQueue = playbackViewModel::onAddToQueue,
            playlists = playlists,
            playlistActions = playlistsViewModel,
            playlistMessages = playlistsViewModel.messages,
            songDeleter = songDeleter,
            onSongsDeleted = { ids ->
                playbackViewModel.removeFromQueue(ids)
                playlistsViewModel.onSongsDeleted(ids)
            },
            themeSettings = themeSettings,
            onThemeModeChange = settingsViewModel::setThemeMode,
            onDynamicColorChange = settingsViewModel::setDynamicColor,
            duplicateActions = DuplicateActions(
                hideDuplicates = hideDuplicates,
                onHideDuplicatesChange = viewModel::setHideDuplicates,
                onKeep = viewModel::keepDuplicate,
                onRestore = viewModel::restoreDuplicate,
                onHideAgain = viewModel::hideDuplicateAgain,
            ),
        )
    }
}

/**
 * Asks once for POST_NOTIFICATIONS (Android 13+) after audio access is granted, so the two prompts
 * don't stack on first launch. Playback works without it; only the media notification is affected.
 */
@Composable
private fun NotificationPermissionPrompt(audioGranted: Boolean, claimPrompt: suspend () -> Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(audioGranted) {
        if (!audioGranted) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && claimPrompt()) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
