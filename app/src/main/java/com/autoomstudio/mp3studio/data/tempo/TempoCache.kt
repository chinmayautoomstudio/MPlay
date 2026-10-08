package com.autoomstudio.mp3studio.data.tempo

import com.autoomstudio.mp3studio.metronome.BeatGrid
import com.autoomstudio.mp3studio.metronome.MeterEstimate
import com.autoomstudio.mp3studio.metronome.RhythmEstimate
import com.autoomstudio.mp3studio.metronome.TempoConfidence
import com.autoomstudio.mp3studio.metronome.TempoEstimate

/** What a stored [SongTempoEntity] is worth for the song as it is now (MT10, MT20). */
sealed interface TempoLookup {
    /** Never analyzed. */
    data object Empty : TempoLookup

    /** The file changed since; the row should be deleted. */
    data object Stale : TempoLookup

    /** Analyzed before time signatures or beat grids were detected, so it needs one more pass. */
    data object Incomplete : TempoLookup

    data class Hit(val rhythm: RhythmEstimate) : TempoLookup
}

/** Converts between [RhythmEstimate] and the song_tempos row, and decides whether a row can be reused. */
object TempoCache {
    /** Stored in meterConfidence when the song was analyzed but its meter couldn't be told. */
    const val NO_METER = "None"

    /** Stored in beatPeriodMs when the song was analyzed but its beats couldn't be placed. */
    const val NO_GRID = 0.0

    fun lookup(entry: SongTempoEntity?, sizeBytes: Long, dateModified: Long): TempoLookup {
        if (entry == null) return TempoLookup.Empty
        if (entry.sizeBytes != sizeBytes || entry.dateModified != dateModified) return TempoLookup.Stale
        val tempo = TempoEstimate(entry.bpm, confidenceOf(entry.confidence) ?: TempoConfidence.Low, 0f)
        val stored = entry.meterConfidence ?: return TempoLookup.Incomplete
        val period = entry.beatPeriodMs ?: return TempoLookup.Incomplete
        val grid = if (period > NO_GRID) {
            BeatGrid(entry.downbeatMs ?: return TempoLookup.Incomplete, period)
        } else {
            null
        }
        if (stored == NO_METER) return TempoLookup.Hit(RhythmEstimate(tempo, null, grid))
        val confidence = confidenceOf(stored)
        val beats = entry.beatsPerBar
        val unit = entry.beatUnit
        val clickBpm = entry.meterBpm
        if (confidence == null || beats == null || unit == null || clickBpm == null) return TempoLookup.Incomplete
        return TempoLookup.Hit(RhythmEstimate(tempo, MeterEstimate(beats, unit, confidence, 0f, clickBpm), grid))
    }

    fun entry(songId: Long, sizeBytes: Long, dateModified: Long, rhythm: RhythmEstimate): SongTempoEntity {
        val meter = rhythm.meter
        return SongTempoEntity(
            songId = songId,
            sizeBytes = sizeBytes,
            dateModified = dateModified,
            bpm = rhythm.tempo.bpm,
            confidence = rhythm.tempo.confidence.name,
            beatsPerBar = meter?.beatsPerBar,
            beatUnit = meter?.beatUnit,
            meterConfidence = meter?.confidence?.name ?: NO_METER,
            meterBpm = meter?.clickBpm,
            downbeatMs = rhythm.grid?.downbeatMs,
            beatPeriodMs = rhythm.grid?.periodMs ?: NO_GRID,
        )
    }

    private fun confidenceOf(name: String): TempoConfidence? = TempoConfidence.entries.firstOrNull { it.name == name }
}
