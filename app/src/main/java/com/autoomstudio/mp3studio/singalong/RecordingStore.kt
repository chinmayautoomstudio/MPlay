package com.autoomstudio.mp3studio.singalong

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Saves sing-alongs as public audio in Music/MPlay Recordings (SA13), where the library picks them up (SA17).
 * Rows stay pending until fully written, so a killed save never shows up half-written (SA16).
 */
class RecordingStore(private val context: Context) {

    private val resolver = context.contentResolver

    /** Free bytes where both the temporary take and the saved file go. */
    fun availableBytes(): Long = minOf(
        StatFs(context.cacheDir.path).availableBytes,
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes,
    )

    /** Copies [temp] into the recordings folder and deletes it. Returns the new MediaStore row. */
    suspend fun save(temp: File, name: String, artist: String): Uri = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(temp, name, artist) else saveLegacy(temp, name)
        } finally {
            temp.delete()
        }
    }

    /** Removes rows left pending by a save that was killed halfway. */
    suspend fun deleteAbandoned() = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.IS_PENDING} = 1",
            )
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(RELATIVE_PATH))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
        }
        try {
            resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    resolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(0)), null, null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean up pending recordings", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(temp: File, name: String, artist: String): Uri {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val fileName = RecordingNames.unique(name) { candidate -> existsScoped(collection, candidate) }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(RecordingNames.EXTENSION))
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun existsScoped(collection: Uri, fileName: String): Boolean = resolver.query(
        collection,
        arrayOf(MediaStore.Audio.Media._ID),
        "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} = ?",
        arrayOf(RELATIVE_PATH, fileName),
        null,
    )?.use { it.count > 0 } ?: false

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(temp: File, name: String): Uri {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create $folder")
        val target = File(folder, RecordingNames.unique(name) { File(folder, it).exists() })
        // Written under a hidden name first, so the scanner never indexes a half-copied file.
        val partial = File(folder, ".${target.name}.partial")
        try {
            temp.copyTo(partial, overwrite = true)
            if (!partial.renameTo(target)) throw IOException("Could not move $partial")
        } finally {
            partial.delete()
        }
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
        private const val TAG = "RecordingStore"
        const val FOLDER = "MP3 Studio Recordings"
        val RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/$FOLDER/"

        /** Where recordings were saved before the rename to MP3 Studio; still listed in the library. */
        val LEGACY_RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/MPlay Recordings/"
        private const val MIME_TYPE = "audio/mp4"
    }
}
