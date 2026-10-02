package com.autoomstudio.mplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.ui.library.LibraryUiState
import com.autoomstudio.mplay.ui.library.LibraryViewModel
import com.autoomstudio.mplay.ui.library.SongListScreen
import com.autoomstudio.mplay.ui.permission.PermissionRationaleScreen
import com.autoomstudio.mplay.ui.permission.rememberAudioPermissionState
import com.autoomstudio.mplay.ui.theme.MPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MPlayTheme {
                MPlayRoot()
            }
        }
    }
}

@Composable
private fun MPlayRoot(viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)) {
    val permission = rememberAudioPermissionState()
    LaunchedEffect(permission.isGranted) {
        viewModel.onPermissionChanged(permission.isGranted)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState is LibraryUiState.NoPermission) {
        PermissionRationaleScreen(
            permanentlyDenied = permission.isPermanentlyDenied,
            onGrant = permission::request,
            onOpenSettings = permission::openSettings,
        )
    } else {
        SongListScreen(state = uiState, onSongClick = { /* Playback arrives in M2. */ })
    }
}
