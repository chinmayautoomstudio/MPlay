package com.autoomstudio.mplay.data.clip

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

class NotEnoughStorageException : IOException("Not enough storage")

/** Saves clips as public audio in Music/MPlay Clips, where other apps and the library can see them. */
class ClipStore(private val context: Context) {

    private val resolver = context.contentResolver

    /** Throws [NotEnoughStorageException] when the temp file and the saved copy may not both fit. */
    fun ensureSpaceFor(range: TrimRange) {
        val clipBytes = range.lengthMs * BYTES_PER_MS
        val needed = clipBytes * 2 + SPACE_MARGIN_BYTES
        val available = minOf(
            StatFs(context.cacheDir.path).availableBytes,
            StatFs(Environment.getExternalStorageDirectory().path).availableBytes,
        )
        if (available < needed) throw NotEnoughStorageException()
    }

    /** Copies [temp] into the clips folder and deletes it. Returns the new MediaStore row. */
    suspend fun save(temp: File, name: String, artist: String): Uri = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(temp, name, artist)
            else saveLegacy(temp, name)
        } finally {
            temp.delete()
        }
    }

    suspend fun markAsSound(uri: Uri, type: SoundType) = withContext(Dispatchers.IO) {
        resolver.update(uri, ContentValues().apply { put(type.mediaStoreColumn, 1) }, null, null)
    }

    suspend fun delete(uri: Uri) = withContext(Dispatchers.IO) {
        resolver.delete(uri, null, null)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(temp: File, name: String, artist: String): Uri {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, ClipNames.fileName(name, "Clip"))
            put(MediaStore.Audio.Media.TITLE, name)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, CLIPS_RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not open $uri")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(temp: File, name: String): Uri {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), CLIPS_FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create $folder")
        val base = ClipNames.clean(name, "Clip")
        var target = File(folder, base + ClipNames.EXTENSION)
        var n = 1
        while (target.exists()) target = File(folder, "$base ($n)${ClipNames.EXTENSION}").also { n++ }
        temp.copyTo(target)
        val uri = suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(MIME_TYPE)) { _, uri ->
                cont.resume(uri)
            }
        }
        if (uri == null) {
            target.delete()
            throw IOException("Scan failed for $target")
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_MUSIC, 1) }, null, null)
        return uri
    }

    companion object {
        const val CLIPS_FOLDER = "MP3 Studio Clips"
        val CLIPS_RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/$CLIPS_FOLDER/"

        /** Where clips were saved before the rename to MP3 Studio; still listed in the library. */
        val LEGACY_CLIPS_RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/MPlay Clips/"
        private const val MIME_TYPE = "audio/mp4"
        private const val BYTES_PER_MS = 192_000L / 8 / 1000
        private const val SPACE_MARGIN_BYTES = 5L * 1024 * 1024
    }
}
