package com.autoomstudio.mplay

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import com.autoomstudio.mplay.ui.main.MainScreen
import com.autoomstudio.mplay.ui.permission.PermissionRationaleScreen
import com.autoomstudio.mplay.ui.permission.rememberAudioPermissionState
import com.autoomstudio.mplay.ui.theme.MPlayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
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
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    if (uiState is LibraryUiState.NoPermission) {
        PermissionRationaleScreen(
            permanentlyDenied = permission.isPermanentlyDenied,
            onGrant = permission::request,
            onOpenSettings = permission::openSettings,
        )
    } else {
        MainScreen(
            libraryState = uiState,
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            onSongClick = { /* Playback arrives in M2. */ },
        )
    }
}
