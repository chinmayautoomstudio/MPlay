package com.autoomstudio.mplay.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.autoomstudio.mplay.data.model.Song

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(albumArtUri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
