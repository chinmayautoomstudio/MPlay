package com.autoomstudio.mplay.ui.trim

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.ui.theme.MPlayTheme

/** The trim editor, kept apart from the main screen so the player sheet's back handling stays out of the way. */
class TrimEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java) == null) {
            finish()
            return
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            MPlayTheme {
                TrimEditorScreen(
                    viewModel = viewModel(factory = TrimEditorViewModel.Factory),
                    onClose = ::finish,
                )
            }
        }
    }

    companion object {
        const val EXTRA_URI = "com.autoomstudio.mplay.extra.TRIM_URI"
        const val EXTRA_TITLE = "com.autoomstudio.mplay.extra.TRIM_TITLE"
        const val EXTRA_ARTIST = "com.autoomstudio.mplay.extra.TRIM_ARTIST"
        const val EXTRA_DURATION = "com.autoomstudio.mplay.extra.TRIM_DURATION"
        const val EXTRA_SONG_ID = "com.autoomstudio.mplay.extra.TRIM_SONG_ID"
        const val EXTRA_MODE = "com.autoomstudio.mplay.extra.TRIM_MODE"

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
