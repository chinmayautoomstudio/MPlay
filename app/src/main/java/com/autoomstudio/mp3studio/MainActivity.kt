package com.autoomstudio.mp3studio

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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.admin.AdminNotifications
import com.autoomstudio.mp3studio.data.settings.ThemeSettings
import com.autoomstudio.mp3studio.ui.auth.AuthViewModel
import com.autoomstudio.mp3studio.ui.auth.SignInScreen
import com.autoomstudio.mp3studio.ui.auth.SignedInViewModelOwner
import com.autoomstudio.mp3studio.ui.auth.SignedInViewModelScope
import com.autoomstudio.mp3studio.ui.duplicates.DuplicateActions
import com.autoomstudio.mp3studio.ui.library.LibraryUiState
import com.autoomstudio.mp3studio.ui.library.LibraryViewModel
import com.autoomstudio.mp3studio.ui.library.LocalSeparatedSongIds
import com.autoomstudio.mp3studio.separation.SeparationLinks
import com.autoomstudio.mp3studio.ui.separation.SeparationViewModel
import com.autoomstudio.mp3studio.ui.main.MainScreen
import com.autoomstudio.mp3studio.ui.permission.PermissionRationaleScreen
import com.autoomstudio.mp3studio.ui.permission.rememberAudioPermissionState
import com.autoomstudio.mp3studio.ui.plans.BillingHost
import com.autoomstudio.mp3studio.ui.plans.PaymentReturnLink
import com.autoomstudio.mp3studio.ui.playback.PlaybackViewModel
import com.autoomstudio.mp3studio.ui.playlist.PlaylistsViewModel
import com.autoomstudio.mp3studio.ui.settings.SettingsViewModel
import com.autoomstudio.mp3studio.ui.theme.MPlayAppTheme

class MainActivity : ComponentActivity() {

    /** Set when launched from the media notification; consumed once by the UI. */
    private var openNowPlayingRequest by mutableStateOf(false)

    /** Set when launched from the vocal separation notification; consumed once by the UI. */
    private var openSeparationRequest by mutableStateOf(false)

    /** Set when launched from the metronome notification; consumed once by the UI. */
    private var openMetronomeRequest by mutableStateOf(false)

    /** Set when launched from an Admin activity notification; consumed once by the Admin screen. */
    private var openAdminActivityRequest by mutableStateOf(false)

    /** Set when the PayU return page opened the app: the transaction ID from the link, or "". Consumed once. */
    private var paymentReturnRequest by mutableStateOf<String?>(null)

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    private val authViewModel: AuthViewModel by viewModels { AuthViewModel.Factory }

    private val signedInScope: SignedInViewModelScope by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Holds the splash until the saved theme and the saved session are known, so neither the theme nor the
        // sign-in screen flashes for a returning user.
        splash.setKeepOnScreenCondition {
            settingsViewModel.theme.value == null || authViewModel.state.value is AuthState.Loading
        }
        if (savedInstanceState == null) handleIntent(intent)
        (application as MPlayApp).container.taskRemoval.onAppOpened()
        setContent {
            val theme = settingsViewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            val auth by authViewModel.state.collectAsStateWithLifecycle()
            MPlayAppTheme(activity = this, settings = theme, forceDark = auth is AuthState.SignedOut) {
                // Sign-in is mandatory: nothing below the sign-in screen exists until someone is signed in.
                // Notification requests stay pending meanwhile and are handled once MPlayRoot appears.
                when (val state = auth) {
                    AuthState.Loading -> Unit
                    AuthState.SignedOut -> {
                        LaunchedEffect(Unit) { signedInScope.storeFor(null) }
                        SignInScreen(authViewModel)
                    }
                    is AuthState.SignedIn -> {
                        val owner = remember(state.user.id) {
                            SignedInViewModelOwner(signedInScope.storeFor(state.user.id), this)
                        }
                        CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                            MPlayRoot(
                                openNowPlayingRequest = openNowPlayingRequest,
                                onOpenNowPlayingHandled = { openNowPlayingRequest = false },
                                openSeparationRequest = openSeparationRequest,
                                onOpenSeparationHandled = { openSeparationRequest = false },
                                openMetronomeRequest = openMetronomeRequest,
                                onOpenMetronomeHandled = { openMetronomeRequest = false },
                                openAdminActivityRequest = openAdminActivityRequest,
                                onOpenAdminActivityHandled = { openAdminActivityRequest = false },
                                paymentReturnRequest = paymentReturnRequest,
                                onPaymentReturnHandled = { paymentReturnRequest = null },
                                themeSettings = theme,
                                settingsViewModel = settingsViewModel,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        authViewModel.verifyAccount()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlayingRequest = true
        }
        if (intent?.action == SeparationLinks.ACTION_OPEN_QUEUE) openSeparationRequest = true
        if (intent?.getBooleanExtra(EXTRA_OPEN_METRONOME, false) == true) openMetronomeRequest = true
        if (intent?.action == AdminNotifications.ACTION_OPEN_ADMIN_ACTIVITY) openAdminActivityRequest = true
        PaymentReturnLink.parse(intent?.data)?.let { paymentReturnRequest = it }
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "com.autoomstudio.mp3studio.extra.OPEN_NOW_PLAYING"
        const val EXTRA_OPEN_METRONOME = "com.autoomstudio.mp3studio.extra.OPEN_METRONOME"
    }
}

@Composable
private fun MPlayRoot(
    openNowPlayingRequest: Boolean,
    onOpenNowPlayingHandled: () -> Unit,
    openSeparationRequest: Boolean,
    onOpenSeparationHandled: () -> Unit,
    openMetronomeRequest: Boolean,
    onOpenMetronomeHandled: () -> Unit,
    openAdminActivityRequest: Boolean,
    onOpenAdminActivityHandled: () -> Unit,
    paymentReturnRequest: String?,
    onPaymentReturnHandled: () -> Unit,
    themeSettings: ThemeSettings,
    settingsViewModel: SettingsViewModel,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    playbackViewModel: PlaybackViewModel = viewModel(factory = PlaybackViewModel.Factory),
    playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.Factory),
    separationViewModel: SeparationViewModel = viewModel(factory = SeparationViewModel.Factory),
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
    val separationState by separationViewModel.state.collectAsStateWithLifecycle()
    if (uiState is LibraryUiState.NoPermission) {
        PermissionRationaleScreen(
            permanentlyDenied = permission.isPermanentlyDenied,
            onGrant = permission::request,
            onOpenSettings = permission::openSettings,
        )
    } else CompositionLocalProvider(LocalSeparatedSongIds provides separationState.readySongIds) {
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
            separationState = separationState,
            separationViewModel = separationViewModel,
            openSeparationRequest = openSeparationRequest,
            onOpenSeparationHandled = onOpenSeparationHandled,
            openMetronomeRequest = openMetronomeRequest,
            onOpenMetronomeHandled = onOpenMetronomeHandled,
            openAdminActivityRequest = openAdminActivityRequest,
            onOpenAdminActivityHandled = onOpenAdminActivityHandled,
        )
    }
    BillingHost(returnRequest = paymentReturnRequest, onReturnHandled = onPaymentReturnHandled)
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
