package com.autoomstudio.mp3studio.ui.trim

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.model.Song
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.autoomstudio.mp3studio.ui.settings.SettingsViewModel
import com.autoomstudio.mp3studio.ui.theme.MPlayAppTheme

/** The trim editor, kept apart from the main screen so the player sheet's back handling stays out of the way. */
class TrimEditorActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java) == null) {
            finish()
            return
        }
        // Only reachable from the signed-in app; close it so the sign-in screen underneath shows.
        val auth = (application as MPlayApp).container.authRepository
        lifecycleScope.launch {
            auth.state.first { it is AuthState.SignedOut }
            finish()
        }
        setContent {
            val theme = settingsViewModel.theme.collectAsStateWithLifecycle().value ?: return@setContent
            MPlayAppTheme(activity = this, settings = theme) {
                TrimEditorScreen(
                    viewModel = viewModel(factory = TrimEditorViewModel.Factory),
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_URI = "com.autoomstudio.mp3studio.extra.TRIM_URI"
        const val EXTRA_TITLE = "com.autoomstudio.mp3studio.extra.TRIM_TITLE"
        const val EXTRA_ARTIST = "com.autoomstudio.mp3studio.extra.TRIM_ARTIST"
        const val EXTRA_DURATION = "com.autoomstudio.mp3studio.extra.TRIM_DURATION"
        const val EXTRA_SONG_ID = "com.autoomstudio.mp3studio.extra.TRIM_SONG_ID"
        const val EXTRA_MODE = "com.autoomstudio.mp3studio.extra.TRIM_MODE"

        fun intent(context: Context, song: Song, mode: TrimMode): Intent =
            Intent(context, TrimEditorActivity::class.java)
                .putExtra(EXTRA_URI, song.uri)
                .putExtra(EXTRA_TITLE, song.title)
                .putExtra(EXTRA_ARTIST, song.artist)
                .putExtra(EXTRA_DURATION, song.durationMs)
                .putExtra(EXTRA_SONG_ID, song.id)
                .putExtra(EXTRA_MODE, mode.name)
    }
}
