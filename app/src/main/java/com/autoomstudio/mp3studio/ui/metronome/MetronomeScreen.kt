package com.autoomstudio.mp3studio.ui.metronome

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autoomstudio.mp3studio.data.model.Song

/** The Metronome tab (MT1), with Detect BPM for [song], the song that's playing. */
@Composable
fun MetronomeScreen(song: Song?, modifier: Modifier = Modifier) {
    MetronomeWithDetection(
        song = song,
        onBack = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = modifier,
    )
}
