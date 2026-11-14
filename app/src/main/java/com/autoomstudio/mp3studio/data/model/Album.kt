package com.autoomstudio.mp3studio.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri,
    /** Song ids in track order. Resolve against the library song list when full [Song]s are needed. */
    val songIds: List<Long>,
) {
    val songCount: Int get() = songIds.size
}
