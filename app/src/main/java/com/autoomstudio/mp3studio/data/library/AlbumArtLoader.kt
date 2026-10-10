package com.autoomstudio.mp3studio.data.library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads album art for MediaStore album-art URIs. MediaProvider refuses to serve thumbnails for files in
 * `Download/`, so when it fails the embedded picture of a song from that album is used instead.
 */
class AlbumArtLoader(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val albumsWithoutArt: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** The art bytes for [uri], or null when there is none. Blocking; call off the main thread. */
    fun load(uri: Uri): ByteArray? {
        try {
            contentResolver.openInputStream(uri)?.use { return it.readBytes() }
        } catch (_: FileNotFoundException) {
            // Fall through to the embedded picture.
        }
        val albumId = albumIdOf(uri.toString()) ?: return null
        if (albumId in albumsWithoutArt) return null
        val picture = embeddedPicture(albumId)
        if (picture == null) albumsWithoutArt += albumId
        return picture
    }

    private fun embeddedPicture(albumId: Long): ByteArray? {
        val songUri = firstSongOfAlbum(albumId) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, songUri)
            retriever.embeddedPicture
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read embedded art for album $albumId", e)
            null
        } finally {
            retriever.release()
        }
    }

    private fun firstSongOfAlbum(albumId: Long): Uri? {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return contentResolver.query(
            collection,
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.ALBUM_ID} = ?",
            arrayOf(albumId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
        }
    }

    companion object {
        private const val TAG = "AlbumArtLoader"
        private const val ALBUM_ART_BASE = "content://media/external/audio/albumart"

        val ALBUM_ART_URI: Uri by lazy { ALBUM_ART_BASE.toUri() }

        /** The album ID of a `content://media/external/audio/albumart/<id>` URI, or null for any other URI. */
        fun albumIdOf(uri: String): Long? {
            val id = uri.removePrefix("$ALBUM_ART_BASE/").takeIf { it.length < uri.length } ?: return null
            return id.toLongOrNull()?.takeIf { it >= 0 }
        }
    }
}
