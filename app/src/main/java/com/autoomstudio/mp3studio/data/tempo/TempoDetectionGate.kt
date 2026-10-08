package com.autoomstudio.mp3studio.data.tempo

import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.metronome.RhythmEstimate

/** Where tempo results come from; [SongTempoAnalyzer] in the app. */
interface TempoSource {
    suspend fun cached(song: Song): RhythmEstimate?
    suspend fun detect(song: Song, onProgress: (Float) -> Unit): RhythmEstimate?
}

sealed interface GatedDetection {
    /** BPM Detector isn't in the user's plan and the song was never analyzed. */
    data object Locked : GatedDetection

    /** [rhythm] is null when the song couldn't be analyzed. */
    data class Finished(val rhythm: RhythmEstimate?) : GatedDetection
}

/**
 * BPM detection is Pro and Trial only (PRD section 6.3), but a tempo detected earlier stays usable on any plan, so
 * the cache is checked before the plan.
 */
class TempoDetectionGate(
    private val source: TempoSource,
    private val canDetect: suspend () -> Boolean,
) {
    suspend fun detect(song: Song, onProgress: (Float) -> Unit): GatedDetection {
        source.cached(song)?.let { return GatedDetection.Finished(it) }
        if (!canDetect()) return GatedDetection.Locked
        return GatedDetection.Finished(source.detect(song, onProgress))
    }
}
