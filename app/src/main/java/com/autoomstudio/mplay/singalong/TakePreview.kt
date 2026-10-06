package com.autoomstudio.mplay.singalong

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Plays the mixed take on the review screen (SA12); level and offset changes are heard right away. */
class TakePreview(context: Context, private val take: Take, private val settings: () -> MixSettings) {
    private val renderer = TakeRenderer(context, take)

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    private val _positionFrames = MutableStateFlow(0L)
    val positionFrames: StateFlow<Long> = _positionFrames.asStateFlow()

    @Volatile private var stopRequested = false
    private var thread: Thread? = null

    fun toggle() = if (_playing.value) pause() else play()

    @Synchronized
    fun play() {
        if (_playing.value) return
        val from = _positionFrames.value.takeIf { it < take.lengthFrames - MIN_REMAINING_FRAMES } ?: 0L
        val track = buildTrack() ?: return
        stopRequested = false
        _playing.value = true
        thread = Thread({ run(track, from) }, "TakePreview").apply { start() }
    }

    @Synchronized
    fun pause() {
        stopRequested = true
        thread?.join(JOIN_TIMEOUT_MS)
        thread = null
        _playing.value = false
    }

    fun seekTo(frame: Long) {
        val wasPlaying = _playing.value
        pause()
        _positionFrames.value = frame.coerceIn(0, take.lengthFrames)
        if (wasPlaying) play()
    }

    fun release() = pause()

    private fun run(track: AudioTrack, from: Long) {
        var finished = false
        try {
            track.play()
            renderer.render(from, settings, { stopRequested }) { block, frames, position ->
                var offset = 0
                while (offset < frames * 2 && !stopRequested) {
                    val written = track.write(block, offset, frames * 2 - offset, AudioTrack.WRITE_BLOCKING)
                    if (written < 0) throw IllegalStateException("AudioTrack write failed: $written")
                    offset += written
                }
                _positionFrames.value = position + frames
            }
            finished = !stopRequested
        } catch (e: Exception) {
            Log.w(TAG, "Preview failed", e)
        } finally {
            runCatching { track.pause() }
            runCatching { track.flush() }
            track.release()
        }
        if (finished) _positionFrames.value = 0
        _playing.value = false
    }

    private fun buildTrack(): AudioTrack? = try {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SingAlongMixer.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            SingAlongMixer.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            // A short buffer keeps level changes responsive.
            .setBufferSizeInBytes(maxOf(minBuffer, SingAlongMixer.SAMPLE_RATE / 10 * 2 * Float.SIZE_BYTES))
            .build()
    } catch (e: Exception) {
        Log.e(TAG, "Could not open preview output", e)
        null
    }

    private companion object {
        const val TAG = "TakePreview"
        const val JOIN_TIMEOUT_MS = 1_000L
        const val MIN_REMAINING_FRAMES = SingAlongMixer.SAMPLE_RATE / 2L
    }
}
