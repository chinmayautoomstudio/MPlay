package com.autoomstudio.mp3studio.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.autoomstudio.mp3studio.data.model.Song

/**
 * The original file is kept in the request metadata as well as the URI, because [PlaybackService] may point the
 * URI at a separated stem and later needs the original back.
 */
fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
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

/** The song file itself, whichever version currently plays. */
val MediaItem.originalUri: Uri?
    get() = requestMetadata.mediaUri ?: localConfiguration?.uri

/** Points [item] at [uri] if it isn't already; returns null when nothing changes. */
fun MediaItem.withPlaybackUri(uri: Uri): MediaItem? =
    if (localConfiguration?.uri == uri) null else buildUpon().setUri(uri).build()
