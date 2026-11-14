package com.autoomstudio.mp3studio.data.library

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads album art for MediaStore album-art URIs. MediaProvider refuses to serve thumbnails for files in
 * `Download/`, so when it fails the embedded picture of a song from that album is used instead.
 *
 * Reads are capped at [MAX_BYTES] so multi‑megabyte covers do not spike RAM while scrolling.
 */
class AlbumArtLoader(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val albumsWithoutArt: MutableSet<Long> = ConcurrentHashMap.newKeySet()

    /** The art bytes for [uri], or null when there is none. Blocking; call off the main thread. */
    fun load(uri: Uri): ByteArray? {
        try {
            contentResolver.openInputStream(uri)?.use { return it.readBytesCapped(MAX_BYTES) }
        } catch (_: FileNotFoundException) {
            // Fall through to the embedded picture.
        }
        val albumId = albumIdOf(uri.toString()) ?: return null
        if (albumId in albumsWithoutArt) return null
        val picture = embeddedPicture(albumId)
        if (picture == null) {
            albumsWithoutArt += albumId
            return null
        }
        // Prefer truncated embedded art over nothing when over the cap.
        return if (picture.size <= MAX_BYTES) picture else picture.copyOf(MAX_BYTES)
    }

    /** Drop remembered “no art” album IDs so a newly embedded cover can be found after a rescan. */
    fun clearMissingCache() {
        albumsWithoutArt.clear()
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

        /** Soft cap for raw cover bytes kept in memory before decode. */
        const val MAX_BYTES = 512 * 1024

        val ALBUM_ART_URI: Uri by lazy { ALBUM_ART_BASE.toUri() }

        /** The album ID of a `content://media/external/audio/albumart/<id>` URI, or null for any other URI. */
        fun albumIdOf(uri: String): Long? {
            val id = uri.removePrefix("$ALBUM_ART_BASE/").takeIf { it.length < uri.length } ?: return null
            return id.toLongOrNull()?.takeIf { it >= 0 }
        }

        /** Decode [bytes] with [inSampleSize] so the longer side is at most [maxSidePx]. */
        fun decodeSampled(bytes: ByteArray, maxSidePx: Int): Bitmap? {
            if (maxSidePx <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxSidePx && bounds.outHeight / (sample * 2) >= maxSidePx) {
                sample *= 2
            }
            return BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }

        /**
         * Re-encodes [bytes] as a JPEG whose longer side is at most [maxSidePx], so Coil/Media3
         * do not keep a full-resolution buffer when only a thumbnail is needed.
         */
        fun subsampledJpeg(bytes: ByteArray, maxSidePx: Int, quality: Int = 85): ByteArray {
            if (maxSidePx <= 0) return bytes
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val longer = maxOf(bounds.outWidth, bounds.outHeight)
            if (longer <= 0) return bytes
            if (longer <= maxSidePx && bytes.size <= MAX_BYTES / 2) return bytes
            val bitmap = decodeSampled(bytes, maxSidePx) ?: return bytes
            return try {
                ByteArrayOutputStream(maxOf(8_192, bytes.size / 4)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}

private fun java.io.InputStream.readBytesCapped(maxBytes: Int): ByteArray {
    val buffer = ByteArrayOutputStream(minOf(maxBytes, available().coerceAtLeast(8_192).coerceAtMost(maxBytes)))
    val chunk = ByteArray(8_192)
    var total = 0
    while (total < maxBytes) {
        val toRead = minOf(chunk.size, maxBytes - total)
        val n = read(chunk, 0, toRead)
        if (n < 0) break
        buffer.write(chunk, 0, n)
        total += n
    }
    return buffer.toByteArray()
}
