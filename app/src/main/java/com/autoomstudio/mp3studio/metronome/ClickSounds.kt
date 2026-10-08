package com.autoomstudio.mp3studio.metronome

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** The click samples, synthesized at the output rate so nothing ships as an asset (MT5). Mono, -1..1. */
class ClickSounds(private val sampleRate: Int) {
    private val cache = HashMap<Pair<ClickSound, Boolean>, FloatArray>()

    fun get(sound: ClickSound, accent: Boolean): FloatArray =
        cache.getOrPut(sound to accent) { synthesize(sound, accent) }

    private fun synthesize(sound: ClickSound, accent: Boolean): FloatArray = when (sound) {
        ClickSound.Classic -> tone(
            frequency = if (accent) 2_600.0 else 1_800.0,
            durationMs = 35,
            decayMs = 7.0,
            level = if (accent) 0.95 else 0.75,
        )
        ClickSound.WoodBlock -> tone(
            frequency = if (accent) 1_250.0 else 900.0,
            durationMs = 60,
            decayMs = 12.0,
            level = if (accent) 0.95 else 0.75,
            overtone = 2.76,
        )
        ClickSound.SoftBeep -> tone(
            frequency = if (accent) 1_320.0 else 880.0,
            durationMs = 90,
            decayMs = 45.0,
            level = if (accent) 0.6 else 0.45,
            attackMs = 4.0,
        )
    }

    private fun tone(
        frequency: Double,
        durationMs: Int,
        decayMs: Double,
        level: Double,
        overtone: Double? = null,
        attackMs: Double = 0.5,
    ): FloatArray {
        val frames = sampleRate * durationMs / 1000
        val attack = sampleRate * attackMs / 1000
        val decay = sampleRate * decayMs / 1000
        // A short fade at the end so the tail never clicks.
        val fade = sampleRate * 0.003
        return FloatArray(frames) { i ->
            val t = i.toDouble() / sampleRate
            var wave = sin(2 * PI * frequency * t)
            if (overtone != null) wave = 0.7 * wave + 0.3 * sin(2 * PI * frequency * overtone * t)
            val envelope = min(1.0, i / attack) * exp(-i / decay) * min(1.0, (frames - i) / fade)
            (wave * envelope * level).toFloat()
        }
    }
}
