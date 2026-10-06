package com.autoomstudio.mplay.separation.android

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.autoomstudio.mplay.separation.pipeline.SeparationError
import com.autoomstudio.mplay.separation.pipeline.SeparationException
import com.autoomstudio.mplay.separation.pipeline.StemSink
import java.io.File
import java.io.IOException

/** Encodes the two stems to AAC/M4A files as they arrive. Call [abort] instead of [finish] to discard them. */
class AacStemSink(
    vocalsFile: File,
    instrumentalFile: File,
    bitrate: Int = DEFAULT_BITRATE,
) : StemSink {
    private val vocals = AacFileWriter(vocalsFile, bitrate)
    private val instrumental = try {
        AacFileWriter(instrumentalFile, bitrate)
    } catch (e: Exception) {
        vocals.abort()
        throw e
    }

    override fun write(vocals: FloatArray, instrumental: FloatArray, frames: Int) {
        guarded {
            this.vocals.write(vocals, frames)
            this.instrumental.write(instrumental, frames)
        }
    }

    override fun finish() {
        guarded {
            vocals.finish()
            instrumental.finish()
        }
    }

    fun abort() {
        vocals.abort()
        instrumental.abort()
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            throw SeparationException(SeparationError.Storage, "Could not write stems", e)
        } catch (e: IllegalStateException) {
            throw SeparationException(SeparationError.Unknown, "AAC encoder failed", e)
        }
    }

    companion object {
        const val DEFAULT_BITRATE = 192_000
    }
}

/** Encodes 44.1 kHz interleaved stereo float to one AAC/M4A file. Call [abort] instead of [finish] to discard it. */
class AacFileWriter(private val file: File, bitrate: Int = AacStemSink.DEFAULT_BITRATE) {
    private val codec: MediaCodec
    private val muxer: MediaMuxer
    private var track = -1
    private var muxerStarted = false
    private var framesQueued = 0L
    private val info = MediaCodec.BufferInfo()
    private var released = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    fun write(interleaved: FloatArray, frames: Int) {
        var frame = 0
        while (frame < frames) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                drain(endOfStream = false)
                continue
            }
            val buffer = codec.getInputBuffer(index)!!
            buffer.clear()
            val count = minOf(frames - frame, buffer.remaining() / (CHANNELS * 2))
            val shorts = buffer.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
            for (i in 0 until count * CHANNELS) {
                val x = interleaved[frame * CHANNELS + i].coerceIn(-1f, 1f)
                shorts.put(i, (x * 32767f).toInt().toShort())
            }
            val pts = framesQueued * 1_000_000L / SAMPLE_RATE
            codec.queueInputBuffer(index, 0, count * CHANNELS * 2, pts, 0)
            framesQueued += count
            frame += count
            drain(endOfStream = false)
        }
    }

    fun finish() {
        while (true) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                val pts = framesQueued * 1_000_000L / SAMPLE_RATE
                codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                break
            }
            drain(endOfStream = false)
        }
        drain(endOfStream = true)
        if (!muxerStarted) throw IOException("Encoder produced no output for $file")
        muxer.stop()
        release()
    }

    fun abort() {
        release()
        file.delete()
    }

    private fun drain(endOfStream: Boolean) {
        var waits = 0
        while (true) {
            val index = codec.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (++waits > MAX_EOS_WAITS) throw IOException("Encoder did not finish $file")
                }
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)!!
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (info.size > 0 && !isConfig && muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(track, buffer, info)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun release() {
        if (released) return
        released = true
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { muxer.release() }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val TIMEOUT_US = 10_000L
        const val MAX_INPUT_BYTES = 16 * 1024
        const val MAX_EOS_WAITS = 500
    }
}
