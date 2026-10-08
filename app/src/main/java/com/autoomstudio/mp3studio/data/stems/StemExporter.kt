package com.autoomstudio.mp3studio.data.stems

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.autoomstudio.mp3studio.data.clip.ClipNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/** Copies a stem into Music/MPlay Stems as a normal song (AI16). The cached copy stays where it is. */
class StemExporter(private val context: Context) {

    private val resolver = context.contentResolver

    suspend fun export(set: StemSetEntity, mode: StemMode): Uri = withContext(Dispatchers.IO) {
        val source = File(
            when (mode) {
                StemMode.Vocals -> set.vocalsPath
                StemMode.Instrumental, StemMode.Original -> set.instrumentalPath
            },
        )
        if (!source.isFile) throw IOException("Stem file is missing")
        val suffix = if (mode == StemMode.Vocals) "Vocals" else "Instrumental"
        val title = "${set.title} ($suffix)"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveScoped(source, title, set.artist) else saveLegacy(source, title)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(source: File, title: String, artist: String): Uri {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, ClipNames.fileName(title, "Stem"))
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.MIME_TYPE, MIME_TYPE)
            put(MediaStore.Audio.Media.RELATIVE_PATH, STEMS_RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Could not open $uri")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    @Suppress("DEPRECATION")
    private suspend fun saveLegacy(source: File, title: String): Uri {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), STEMS_FOLDER)
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Could not create $folder")
        val base = ClipNames.clean(title, "Stem")
        var target = File(folder, base + ClipNames.EXTENSION)
        var n = 1
        while (target.exists()) target = File(folder, "$base ($n)${ClipNames.EXTENSION}").also { n++ }
        source.copyTo(target)
        val uri = suspendCancellableCoroutine { cont ->
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(MIME_TYPE)) { _, uri ->
                cont.resume(uri)
            }
        }
        if (uri == null) {
            target.delete()
            throw IOException("Scan failed for $target")
        }
        return uri
    }

    companion object {
        const val STEMS_FOLDER = "MP3 Studio Stems"
        val STEMS_RELATIVE_PATH = "${Environment.DIRECTORY_MUSIC}/$STEMS_FOLDER/"
        private const val MIME_TYPE = "audio/mp4"
    }
}
