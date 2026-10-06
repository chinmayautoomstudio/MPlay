package com.autoomstudio.mplay.singalong

import android.content.Context
import android.net.Uri
import com.autoomstudio.mplay.separation.android.AacFileWriter
import com.autoomstudio.mplay.separation.android.MediaPcmSource
import java.io.File

/** One recorded take and what it was sung against. */
data class Take(
    val songId: Long,
    val title: String,
    val artist: String,
    val instrumentalUri: Uri,
    /** Song position where the instrumental started, in ms. */
    val instrumentalStartMs: Long,
    val voiceFile: File,
    /** Voice recorded before the instrumental became audible. */
    val leadFrames: Long,
    val voiceFrames: Long,
    val endReason: EndReason,
) {
    /** Length of the mix: the instrumental from its start until the voice ran out. */
    val lengthFrames: Long get() = (voiceFrames - leadFrames).coerceAtLeast(0)

    val durationMs: Long get() = lengthFrames * 1000 / SingAlongMixer.SAMPLE_RATE
}

enum class EndReason { Stopped, SongEnded, Interrupted, Failed }

/**
 * Streams the mix of a [Take] (SA14): decodes the instrumental from the recording's start position and lays the
 * voice over it with the current [settings]. Settings are read per block, so level changes apply while previewing.
 */
class TakeRenderer(private val context: Context, private val take: Take) {

    /** Ends decoding early; a plain RuntimeException, which the decoder passes through instead of reporting. */
    private class Stopped : RuntimeException()

    /**
     * Mixes from [fromFrame] to the end, handing each block of interleaved stereo to [sink]. Return true from
     * [shouldStop] to end early.
     */
    fun render(
        fromFrame: Long,
        settings: () -> MixSettings,
        shouldStop: () -> Boolean,
        sink: (interleaved: FloatArray, frames: Int, position: Long) -> Unit,
    ) {
        val remaining = take.lengthFrames - fromFrame
        if (remaining <= 0) return
        val startUs = take.instrumentalStartMs * 1000 + fromFrame * 1_000_000 / SingAlongMixer.SAMPLE_RATE
        var position = fromFrame
        var voice = FloatArray(0)
        var mixed = FloatArray(0)
        VoiceFile(take.voiceFile).use { voiceFile ->
            try {
                MediaPcmSource(context, take.instrumentalUri, maxFrames = remaining, startUs = startUs)
                    .read { block, frames ->
                        if (shouldStop()) throw Stopped()
                        val current = settings()
                        if (voice.size < frames) voice = FloatArray(frames)
                        if (mixed.size < frames * 2) mixed = FloatArray(frames * 2)
                        voiceFile.read(
                            SingAlongMixer.voiceFrameFor(position, take.leadFrames, current.offsetMs),
                            frames,
                            voice,
                        )
                        SingAlongMixer.mix(block, voice, frames, current, mixed)
                        sink(mixed, frames, position)
                        position += frames
                    }
            } catch (e: Stopped) {
                return
            }
        }
    }

    /** Writes the whole mix to [output] as stereo AAC/M4A, 192 kbps, 44.1 kHz (SA13). */
    fun export(output: File, settings: MixSettings, onProgress: (Float) -> Unit, shouldStop: () -> Boolean) {
        val writer = AacFileWriter(output)
        try {
            render(0, { settings }, shouldStop) { block, frames, position ->
                writer.write(block, frames)
                onProgress(((position + frames).toFloat() / take.lengthFrames).coerceAtMost(1f))
            }
            if (shouldStop()) {
                writer.abort()
                return
            }
            writer.finish()
        } catch (e: Exception) {
            writer.abort()
            throw e
        }
    }
}
