package com.autoomstudio.mplay.singalong

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sign
import kotlin.math.tanh

/** The review screen's levels and sync offset (SA12). A positive offset plays the voice later. */
data class MixSettings(
    val voiceGain: Float = 1f,
    val instrumentalGain: Float = 0.8f,
    val offsetMs: Int = 0,
) {
    companion object {
        const val MAX_OFFSET_MS = 300
        const val MAX_GAIN = 1.5f
    }
}

/** Mixes the mono voice take over the stereo instrumental, with soft clipping instead of hard clipping (SA14). */
object SingAlongMixer {
    const val SAMPLE_RATE = 44_100

    private const val KNEE = 0.8f

    /** tanh rounds to exactly 1 in float for loud input, so the curve tops out just below full scale. */
    private const val CEILING = 0.99f

    /**
     * The voice frame heard with instrumental frame [instrumentalFrame]. [leadFrames] is how much voice was
     * recorded before the instrumental started; the offset shifts the voice against the music.
     */
    fun voiceFrameFor(instrumentalFrame: Long, leadFrames: Long, offsetMs: Int): Long =
        instrumentalFrame + leadFrames - offsetFrames(offsetMs)

    fun offsetFrames(offsetMs: Int): Long = (offsetMs.toLong() * SAMPLE_RATE / 1000.0).roundToLong()

    /**
     * Writes [frames] of mixed stereo into [out]. [instrumental] is interleaved stereo and [voice] mono, both
     * already lined up frame by frame.
     */
    fun mix(instrumental: FloatArray, voice: FloatArray, frames: Int, settings: MixSettings, out: FloatArray) {
        val music = settings.instrumentalGain
        val vocal = settings.voiceGain
        for (frame in 0 until frames) {
            val v = voice[frame] * vocal
            out[frame * 2] = limit(instrumental[frame * 2] * music + v)
            out[frame * 2 + 1] = limit(instrumental[frame * 2 + 1] * music + v)
        }
    }

    /** Leaves quiet samples alone and bends loud ones smoothly towards, but never past, full scale. */
    fun limit(x: Float): Float {
        val magnitude = abs(x)
        if (magnitude <= KNEE) return x
        val over = (magnitude - KNEE) / (CEILING - KNEE)
        return sign(x) * (KNEE + (CEILING - KNEE) * tanh(over))
    }
}
