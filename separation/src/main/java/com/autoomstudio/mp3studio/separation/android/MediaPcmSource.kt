package com.autoomstudio.mp3studio.separation.android

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.autoomstudio.mp3studio.separation.dsp.ChannelMixer
import com.autoomstudio.mp3studio.separation.dsp.StereoResampler
import com.autoomstudio.mp3studio.separation.pipeline.PcmSource
import com.autoomstudio.mp3studio.separation.pipeline.SeparationError
import com.autoomstudio.mp3studio.separation.pipeline.SeparationException
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes [uri] with MediaCodec into 44.1 kHz interleaved stereo float, whatever the source's rate and channels.
 * [maxFrames] cuts the output short, for example to test on the first 30 seconds. [startUs] skips ahead first;
 * it lands on the sync point at or before it. With [exactStart] the decoded audio before [startUs] is dropped, using
 * each buffer's presentation time, so the first output frame is the one at [startUs].
 */
class MediaPcmSource(
    private val context: Context,
    private val uri: Uri,
    private val maxFrames: Long? = null,
    private val startUs: Long = 0,
    private val exactStart: Boolean = false,
) : PcmSource {

    override fun read(consumer: (interleaved: FloatArray, frames: Int) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: FileNotFoundException) {
                throw SeparationException(SeparationError.SourceMissing, "Cannot open $uri", e)
            } catch (e: SecurityException) {
                throw SeparationException(SeparationError.SourceMissing, "No access to $uri", e)
            } catch (e: IOException) {
                throw SeparationException(SeparationError.CorruptFile, "Cannot read $uri", e)
            }
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw SeparationException(SeparationError.UnsupportedFormat, "No audio track in $uri")
            extractor.selectTrack(track)
            if (startUs > 0) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val decoder = try {
                MediaCodec.createDecoderByType(mime).also { codec = it }
            } catch (e: Exception) {
                throw SeparationException(SeparationError.UnsupportedFormat, "No decoder for $mime", e)
            }
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decodeLoop(extractor, decoder, inputFormat, consumer)
        } catch (e: SeparationException) {
            throw e
        } catch (e: StopDecoding) {
            // Reached maxFrames.
        } catch (e: IllegalStateException) {
            throw SeparationException(SeparationError.CorruptFile, "Decoder failed on $uri", e)
        } catch (e: IllegalArgumentException) {
            throw SeparationException(SeparationError.UnsupportedFormat, "Unsupported file $uri", e)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun decodeLoop(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        inputFormat: MediaFormat,
        consumer: (FloatArray, Int) -> Unit,
    ) {
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        var resampler: StereoResampler? = null
        var raw = FloatArray(0)
        var stereo = FloatArray(0)
        var emitted = 0L
        val limit = maxFrames ?: Long.MAX_VALUE

        val emit: (FloatArray, Int) -> Unit = { block, frames ->
            val take = minOf(frames.toLong(), limit - emitted).toInt()
            if (take > 0) {
                consumer(block, take)
                emitted += take
            }
            if (emitted >= limit) throw StopDecoding()
        }

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            while (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(0)
                if (inIndex < 0) break
                val buffer = decoder.getInputBuffer(inIndex)!!
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) {
                    decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    inputDone = true
                } else {
                    decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
            var outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            while (outIndex != MediaCodec.INFO_TRY_AGAIN_LATER && !outputDone) {
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = decoder.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        resampler = null
                    }
                    outIndex >= 0 -> {
                        if (info.size > 0) {
                            val bytes = decoder.getOutputBuffer(outIndex)!!
                            bytes.position(info.offset)
                            bytes.limit(info.offset + info.size)
                            val ordered = bytes.slice().order(ByteOrder.nativeOrder())
                            val samples = if (floatPcm) info.size / 4 else info.size / 2
                            if (raw.size < samples) raw = FloatArray(samples)
                            if (floatPcm) {
                                ordered.asFloatBuffer().get(raw, 0, samples)
                            } else {
                                val shorts = ordered.asShortBuffer()
                                for (i in 0 until samples) raw[i] = shorts.get(i) / 32768f
                            }
                            val frames = samples / channels
                            if (stereo.size < frames * 2) stereo = FloatArray(frames * 2)
                            ChannelMixer.toStereo(raw, channels, frames, stereo)
                            decoder.releaseOutputBuffer(outIndex, false)
                            val skip = if (exactStart && info.presentationTimeUs < startUs) {
                                ((startUs - info.presentationTimeUs) * sampleRate / 1_000_000L)
                                    .coerceAtMost(frames.toLong()).toInt()
                            } else {
                                0
                            }
                            if (skip > 0) stereo.copyInto(stereo, 0, skip * 2, frames * 2)
                            val r = resampler ?: StereoResampler(sampleRate, TARGET_RATE).also { resampler = it }
                            if (frames > skip) r.process(stereo, frames - skip, emit)
                        } else {
                            decoder.releaseOutputBuffer(outIndex, false)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                if (!outputDone) outIndex = decoder.dequeueOutputBuffer(info, 0)
            }
        }
        resampler?.flush(emit)
        if (emitted == 0L) throw SeparationException(SeparationError.CorruptFile, "No audio decoded from $uri")
    }

    private class StopDecoding : RuntimeException()

    companion object {
        const val TARGET_RATE = 44_100
        private const val TIMEOUT_US = 10_000L
    }
}
