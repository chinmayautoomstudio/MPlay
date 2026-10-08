package com.autoomstudio.mp3studio.separation.android

import android.content.Context
import android.net.Uri
import com.autoomstudio.mp3studio.separation.pipeline.SeparationCancelledException
import com.autoomstudio.mp3studio.separation.pipeline.SeparationError
import com.autoomstudio.mp3studio.separation.pipeline.SeparationException
import com.autoomstudio.mp3studio.separation.pipeline.SeparationProgress
import com.autoomstudio.mp3studio.separation.pipeline.StemSeparator

/**
 * Decodes [uri], separates it and writes the two stems as M4A. On any failure or cancellation both output
 * files are deleted, so callers never see partial results. Throws [SeparationException] or
 * [SeparationCancelledException].
 */
fun StemSeparator.separateToFiles(
    context: Context,
    uri: Uri,
    vocals: java.io.File,
    instrumental: java.io.File,
    maxFrames: Long? = null,
    bitrate: Int = AacStemSink.DEFAULT_BITRATE,
    isCancelled: () -> Boolean = { false },
    onProgress: (SeparationProgress) -> Unit = {},
) {
    val sink = try {
        AacStemSink(vocals, instrumental, bitrate)
    } catch (e: Exception) {
        throw SeparationException(SeparationError.Storage, "Could not create output files", e)
    }
    try {
        separate(MediaPcmSource(context, uri, maxFrames), sink, isCancelled, onProgress)
    } catch (e: OutOfMemoryError) {
        sink.abort()
        throw SeparationException(SeparationError.OutOfMemory, "Out of memory", e)
    } catch (e: SeparationException) {
        sink.abort()
        throw e
    } catch (e: SeparationCancelledException) {
        sink.abort()
        throw e
    } catch (e: Exception) {
        sink.abort()
        throw SeparationException(SeparationError.Unknown, e.message ?: e.javaClass.simpleName, e)
    }
}
