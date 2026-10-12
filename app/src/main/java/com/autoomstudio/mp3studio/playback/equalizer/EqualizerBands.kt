package com.autoomstudio.mp3studio.playback.equalizer

import kotlin.math.roundToInt

/** Band centre frequencies in Hz (60 Hz low shelf … 14 kHz high shelf). */
object EqualizerBands {
    const val COUNT = 7
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val GAIN_STEP_DB = 0.5f

    val FREQUENCIES_HZ = doubleArrayOf(60.0, 150.0, 400.0, 1_000.0, 2_500.0, 6_000.0, 14_000.0)

    val LABELS = listOf("60", "150", "400", "1K", "2.5K", "6K", "14K")

    /** Peaking Q for the five middle bands (~1.3 octaves apart). */
    const val PEAKING_Q = 1.1

    fun clampGain(db: Float): Float {
        val steps = (db / GAIN_STEP_DB).roundToInt()
        return (steps * GAIN_STEP_DB).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
    }

    fun flatGains(): FloatArray = FloatArray(COUNT)

    fun copyGains(source: FloatArray): FloatArray =
        FloatArray(COUNT) { i -> clampGain(source.getOrElse(i) { 0f }) }
}
