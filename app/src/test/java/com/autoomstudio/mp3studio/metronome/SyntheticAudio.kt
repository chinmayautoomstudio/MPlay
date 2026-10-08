package com.autoomstudio.mp3studio.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Synthetic test audio at the detector's analysis rate. */
object SyntheticAudio {

    const val RATE = TempoDetector.ANALYSIS_RATE

    /** One drum hit at [beat] (in beats, fractions allowed) of a repeating bar. */
    data class Hit(val beat: Double, val drum: Drum, val gain: Float)

    enum class Drum { Kick, Snare, Hat }

    /** Decaying tone bursts on every beat; [offbeatGain] adds quieter eighth notes in between. */
    fun clicks(
        bpm: Double,
        seconds: Int = 30,
        offbeatGain: Float = 0f,
        noise: Float = 0f,
        accentEvery: Int = 0,
        seed: Int = 1,
    ): FloatArray {
        val out = FloatArray(RATE * seconds)
        val random = Random(seed)
        if (noise > 0) for (i in out.indices) out[i] = (random.nextFloat() * 2 - 1) * noise
        val beat = RATE * 60.0 / bpm
        var n = 0
        while (n * beat < out.size) {
            val accent = accentEvery > 0 && n % accentEvery == 0
            burst(out, n * beat, if (accent) 1f else 0.7f, if (accent) 1500.0 else 1000.0)
            if (offbeatGain > 0) burst(out, n * beat + beat / 2, offbeatGain, 3000.0)
            n++
        }
        return out
    }

    /**
     * A drum loop: [hits] repeat every [beatsPerBar] beats at [bpm]. Kicks are low sine thumps (mostly below
     * 200 Hz), snares and hats are the same short high bursts [clicks] uses.
     */
    fun pattern(
        bpm: Double,
        beatsPerBar: Int,
        hits: List<Hit>,
        seconds: Int = 45,
        noise: Float = 0f,
        seed: Int = 1,
    ): FloatArray {
        val out = FloatArray(RATE * seconds)
        val random = Random(seed)
        if (noise > 0) for (i in out.indices) out[i] = (random.nextFloat() * 2 - 1) * noise
        val beat = RATE * 60.0 / bpm
        var bar = 0
        while (bar * beatsPerBar * beat < out.size) {
            for (hit in hits) {
                val at = (bar * beatsPerBar + hit.beat) * beat
                when (hit.drum) {
                    Drum.Kick -> burst(out, at, hit.gain, 70.0, seconds = 0.15, decay = 0.04)
                    Drum.Snare -> burst(out, at, hit.gain, 1000.0)
                    Drum.Hat -> burst(out, at, hit.gain, 3000.0)
                }
            }
            bar++
        }
        return out
    }

    private fun burst(
        out: FloatArray,
        at: Double,
        gain: Float,
        freq: Double,
        seconds: Double = 0.05,
        decay: Double = 0.01,
    ) {
        val start = at.toInt()
        for (i in 0 until (RATE * seconds).toInt()) {
            val index = start + i
            if (index >= out.size) return
            out[index] += (gain * exp(-i / (RATE * decay)) * sin(2 * PI * freq * i / RATE)).toFloat()
        }
    }
}
