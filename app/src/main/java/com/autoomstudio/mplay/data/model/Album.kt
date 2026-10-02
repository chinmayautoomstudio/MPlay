package com.autoomstudio.mplay.data.model

import android.net.Uri

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri,
    /** In track order. */
    val songs: List<Song>,
)
