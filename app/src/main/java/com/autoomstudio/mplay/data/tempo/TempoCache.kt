package com.autoomstudio.mplay.data.tempo

import com.autoomstudio.mplay.metronome.MeterEstimate
import com.autoomstudio.mplay.metronome.RhythmEstimate
import com.autoomstudio.mplay.metronome.TempoConfidence
import com.autoomstudio.mplay.metronome.TempoEstimate

/** What a stored [SongTempoEntity] is worth for the song as it is now (MT10, MT20). */
sealed interface TempoLookup {
    /** Never analyzed. */
    data object Empty : TempoLookup

    /** The file changed since; the row should be deleted. */
    data object Stale : TempoLookup

    /** Analyzed before time signatures were detected, so it needs one more pass. */
    data object MissingMeter : TempoLookup

    data class Hit(val rhythm: RhythmEstimate) : TempoLookup
}

/** Converts between [RhythmEstimate] and the song_tempos row, and decides whether a row can be reused. */
object TempoCache {
    /** Stored in meterConfidence when the song was analyzed but its meter couldn't be told. */
    const val NO_METER = "None"

    fun lookup(entry: SongTempoEntity?, sizeBytes: Long, dateModified: Long): TempoLookup {
        if (entry == null) return TempoLookup.Empty
        if (entry.sizeBytes != sizeBytes || entry.dateModified != dateModified) return TempoLookup.Stale
        val tempo = TempoEstimate(entry.bpm, confidenceOf(entry.confidence) ?: TempoConfidence.Low, 0f)
        val stored = entry.meterConfidence ?: return TempoLookup.MissingMeter
        if (stored == NO_METER) return TempoLookup.Hit(RhythmEstimate(tempo, null))
        val confidence = confidenceOf(stored)
        val beats = entry.beatsPerBar
        val unit = entry.beatUnit
        val clickBpm = entry.meterBpm
        if (confidence == null || beats == null || unit == null || clickBpm == null) return TempoLookup.MissingMeter
        return TempoLookup.Hit(RhythmEstimate(tempo, MeterEstimate(beats, unit, confidence, 0f, clickBpm)))
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
        )
    }

    private fun confidenceOf(name: String): TempoConfidence? = TempoConfidence.entries.firstOrNull { it.name == name }
}
