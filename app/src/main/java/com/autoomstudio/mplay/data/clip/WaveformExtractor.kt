package com.autoomstudio.mplay.data.clip

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder

class WaveformExtractor(private val context: Context) {

    /**
     * Decodes the whole file; throws [IOException] when it has no decodable audio.
     * [onPartial] receives the peaks decoded so far every few hundred milliseconds, from a background thread.
     */
    suspend fun extract(
        uri: Uri,
        durationMs: Long,
        bucketCount: Int = DEFAULT_BUCKETS,
        onPartial: (FloatArray) -> Unit = {},
    ): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(context, uri, null)
                val track = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: throw IOException("No audio track")
                extractor.selectTrack(track)
                val inputFormat = extractor.getTrackFormat(track)
                val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
                val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                    inputFormat.getLong(MediaFormat.KEY_DURATION)
                } else {
                    durationMs * 1000
                }

                val decoder = MediaCodec.createDecoderByType(mime).also { codec = it }
                decoder.configure(inputFormat, null, null, 0)
                decoder.start()

                var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                var accumulator: PeakAccumulator? = null
                var scratch = ShortArray(0)
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                var lastPartialNs = System.nanoTime()

                while (!outputDone) {
                    ensureActive()
                    // Fill every free input buffer, then drain all ready output; blocking per frame is far slower.
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
                            }
                            outIndex >= 0 -> {
                                if (info.size > 0) {
                                    val acc = accumulator ?: PeakAccumulator(
                                        bucketCount,
                                        durationUs * sampleRate / 1_000_000,
                                    ).also { accumulator = it }
                                    val bytes = decoder.getOutputBuffer(outIndex)!!
                                    bytes.position(info.offset)
                                    bytes.limit(info.offset + info.size)
                                    val shorts = bytes.slice().order(ByteOrder.nativeOrder()).asShortBuffer()
                                    val count = shorts.remaining()
                                    if (scratch.size < count) scratch = ShortArray(count)
                                    shorts.get(scratch, 0, count)
                                    acc.add(scratch, count, channels)
                                val now = System.nanoTime()
                                if (now - lastPartialNs > PARTIAL_INTERVAL_NS) {
                                    lastPartialNs = now
                                    onPartial(acc.result())
                                }
                                }
                                decoder.releaseOutputBuffer(outIndex, false)
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                            }
                        }
                        if (!outputDone) outIndex = decoder.dequeueOutputBuffer(info, 0)
                    }
                }
                accumulator?.result() ?: throw IOException("No audio decoded")
            } catch (e: IllegalStateException) {
                throw IOException("Decoder failed", e)
            } catch (e: IllegalArgumentException) {
                throw IOException("Unsupported file", e)
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                extractor.release()
            }
        }

    private companion object {
        const val DEFAULT_BUCKETS = 500
        const val TIMEOUT_US = 10_000L
        const val PARTIAL_INTERVAL_NS = 300_000_000L
    }
}
