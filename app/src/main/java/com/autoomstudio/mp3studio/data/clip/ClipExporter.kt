package com.autoomstudio.mp3studio.data.clip

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cuts [range] out of a track into an AAC/M4A file in the cache folder. The source is only read. */
class ClipExporter(private val context: Context) {

    /** Returns the temp file; the caller moves or deletes it. Cancelling stops the export. */
    @OptIn(UnstableApi::class)
    suspend fun export(source: Uri, range: TrimRange, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.Main) {
            val output = File(context.cacheDir, "clip-${System.currentTimeMillis()}.m4a")
            val mediaItem = MediaItem.Builder()
                .setUri(source)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(range.startMs)
                        .setEndPositionMs(range.endMs)
                        .build(),
                )
                .build()
            val edited = EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).build()
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder().setBitrate(AAC_BITRATE).build(),
                )
                .build()

            coroutineScope {
                var transformer: Transformer? = null
                val poller = launch {
                    val holder = ProgressHolder()
                    while (true) {
                        delay(PROGRESS_POLL_MS)
                        val state = transformer?.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress / 100f)
                    }
                }
                try {
                    suspendCancellableCoroutine { cont ->
                        val t = Transformer.Builder(context)
                            .setAudioMimeType(MimeTypes.AUDIO_AAC)
                            .setEncoderFactory(encoderFactory)
                            // Streamable output reserves about 400 KB up front, more than a short clip itself.
                            .setMuxerFactory(InAppMp4Muxer.Factory().setAttemptStreamableOutputEnabled(false))
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, result: ExportResult) {
                                    cont.resume(output)
                                }

                                override fun onError(
                                    composition: Composition,
                                    result: ExportResult,
                                    exception: ExportException,
                                ) {
                                    output.delete()
                                    cont.resumeWithException(exception)
                                }
                            })
                            .build()
                        transformer = t
                        cont.invokeOnCancellation {
                            t.cancel()
                            output.delete()
                        }
                        t.start(edited, output.absolutePath)
                    }
                } finally {
                    poller.cancel()
                }
            }
        }

    private companion object {
        const val AAC_BITRATE = 192_000
        const val PROGRESS_POLL_MS = 100L
    }
}
