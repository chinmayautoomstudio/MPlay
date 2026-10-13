package com.autoomstudio.mp3studio.data.library

import android.content.Context
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.mp4.Mp4Tag
import java.io.File

/** Writes the app logo as MP4 `covr` into an AAC/M4A file before it is copied to MediaStore. */
class CoverArtEmbedder(private val context: Context) {

    /** Embeds [R.drawable.ic_mp3studio_logo] into [file]. Leaves the audio stream untouched. */
    fun embedLogo(file: File) {
        val jpeg = AppLogoArtwork.jpegBytes(context)
        embed(file, jpeg)
    }

    /** Embeds [jpeg] cover bytes into [file]. Exposed for tests. */
    fun embed(file: File, jpeg: ByteArray) {
        require(file.isFile) { "Not a file: $file" }
        require(jpeg.isNotEmpty()) { "Empty cover art" }
        val audioFile = AudioFileIO.read(file)
        val tag = (audioFile.tagOrCreateAndSetDefault as? Mp4Tag)
            ?: error("Expected Mp4Tag for $file")
        tag.deleteArtworkField()
        tag.setField(tag.createArtworkField(jpeg))
        audioFile.commit()
        Log.d(TAG, "Embedded cover art into ${file.name} (${jpeg.size} bytes)")
    }

    private companion object {
        const val TAG = "CoverArtEmbedder"
    }
}
