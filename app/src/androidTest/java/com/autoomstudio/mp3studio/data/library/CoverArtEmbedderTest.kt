package com.autoomstudio.mp3studio.data.library

import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autoomstudio.mp3studio.separation.android.AacFileWriter
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CoverArtEmbedderTest {

    @Test
    fun embedWritesCoverReadableByRetriever() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "cover-art-embedder-test.m4a")
        file.delete()
        try {
            writeSilentAac(file)
            val jpeg = AppLogoArtwork.jpegBytes(context, sizePx = 64)
            CoverArtEmbedder(context).embed(file, jpeg)

            val tag = AudioFileIO.read(file).tag as Mp4Tag
            assertNotNull(tag.firstArtwork)
            assertTrue(tag.firstArtwork.binaryData.isNotEmpty())

            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                val picture = retriever.embeddedPicture
                assertNotNull(picture)
                assertTrue(picture!!.isNotEmpty())
            } finally {
                retriever.release()
            }
        } finally {
            file.delete()
        }
    }

    private fun writeSilentAac(file: File) {
        val writer = AacFileWriter(file)
        try {
            val frames = 4_410 // 0.1 s at 44.1 kHz
            writer.write(FloatArray(frames * 2), frames)
            writer.finish()
        } catch (e: Exception) {
            writer.abort()
            throw e
        }
    }
}
