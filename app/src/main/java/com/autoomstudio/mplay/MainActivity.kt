package com.autoomstudio.mplay

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.ui.library.LibraryUiState
import com.autoomstudio.mplay.ui.library.LibraryViewModel
import com.autoomstudio.mplay.ui.main.MainScreen
import com.autoomstudio.mplay.ui.permission.PermissionRationaleScreen
import com.autoomstudio.mplay.ui.permission.rememberAudioPermissionState
import com.autoomstudio.mplay.ui.playback.PlaybackViewModel
import com.autoomstudio.mplay.ui.theme.MPlayTheme

class MainActivity : ComponentActivity() {

    /** Set when launched from the media notification; consumed once by the UI. */
    private var openNowPlayingRequest by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MPlayTheme {
                MPlayRoot(
                    openNowPlayingRequest = openNowPlayingRequest,
                    onOpenNowPlayingHandled = { openNowPlayingRequest = false },
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
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    playbackViewModel: PlaybackViewModel = viewModel(factory = PlaybackViewModel.Factory),
) {
    val permission = rememberAudioPermissionState()
    LaunchedEffect(permission.isGranted) {
        viewModel.onPermissionChanged(permission.isGranted)
    }
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
    val nowPlaying by playbackViewModel.state.collectAsStateWithLifecycle()
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
            showNowPlaying = showNowPlaying,
            onShowNowPlayingChange = { showNowPlaying = it },
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            onSongClick = { song ->
                val songs = (uiState as? LibraryUiState.Content)?.songs.orEmpty()
                playbackViewModel.onSongClick(songs, song)
            },
        )
    }
}
