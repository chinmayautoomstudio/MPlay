package com.autoomstudio.mplay.metronome

enum class ClickSound { Classic, WoodBlock, SoftBeep }

/** A time signature preset: [beats] per bar, counted in [unit] notes (MT3). */
data class TimeSignature(val beats: Int, val unit: Int) {
    override fun toString(): String = "$beats/$unit"

    companion object {
        val Presets = listOf(TimeSignature(2, 4), TimeSignature(3, 4), TimeSignature(4, 4), TimeSignature(6, 8))
    }
}

data class MetronomeSettings(
    val bpm: Int = DEFAULT_BPM,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val accent: Boolean = true,
    val sound: ClickSound = ClickSound.Classic,
    val volume: Float = 0.8f,
) {
    val timeSignature: TimeSignature get() = TimeSignature(beatsPerBar, beatUnit)

    companion object {
        const val MIN_BPM = 20
        const val MAX_BPM = 300
        const val DEFAULT_BPM = 120
        const val MAX_BEATS = 12

        fun clampBpm(bpm: Int): Int = bpm.coerceIn(MIN_BPM, MAX_BPM)

        /** Unknown or out-of-range stored values fall back to the defaults instead of failing. */
        fun of(
            bpm: Int?,
            beatsPerBar: Int?,
            beatUnit: Int?,
            accent: Boolean?,
            sound: String?,
            volume: Float?,
        ): MetronomeSettings {
            val defaults = MetronomeSettings()
            return MetronomeSettings(
                bpm = bpm?.let(::clampBpm) ?: defaults.bpm,
                beatsPerBar = beatsPerBar?.coerceIn(1, MAX_BEATS) ?: defaults.beatsPerBar,
                beatUnit = beatUnit?.takeIf { it == 4 || it == 8 } ?: defaults.beatUnit,
                accent = accent ?: defaults.accent,
                sound = ClickSound.entries.firstOrNull { it.name == sound } ?: defaults.sound,
                volume = volume?.coerceIn(0f, 1f) ?: defaults.volume,
            )
        }
    }
}
