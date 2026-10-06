package com.autoomstudio.mplay.data.tempo

import android.content.Context
import android.util.Log
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.stems.StemMode
import com.autoomstudio.mplay.data.stems.StemRepository
import com.autoomstudio.mplay.metronome.TempoConfidence
import com.autoomstudio.mplay.metronome.TempoDetector
import com.autoomstudio.mplay.metronome.TempoEstimate
import com.autoomstudio.mplay.separation.android.MediaPcmSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Detects a song's tempo from a slice of its middle (MT9), preferring the instrumental stem when the song has been
 * separated, since the drums are clearer without vocals (MT11). Results are cached per song (MT10).
 */
class SongTempoAnalyzer(
    private val context: Context,
    private val dao: TempoDao,
    private val stemRepository: StemRepository,
) {
    /** The stored result, if the song's file hasn't changed since it was analyzed. */
    suspend fun cached(song: Song): TempoEstimate? {
        val entry = dao.tempo(song.id) ?: return null
        if (entry.sizeBytes != song.sizeBytes || entry.dateModified != song.dateModified) {
            dao.delete(song.id)
            return null
        }
        val confidence = TempoConfidence.entries.firstOrNull { it.name == entry.confidence } ?: TempoConfidence.Low
        return TempoEstimate(entry.bpm, confidence, 0f)
    }

    /** Returns null when the file can't be decoded or has no clear beat. */
    suspend fun detect(song: Song, onProgress: (Float) -> Unit): TempoEstimate? {
        cached(song)?.let { return it }
        val estimate = withContext(Dispatchers.Default) { analyze(song, onProgress) } ?: return null
        dao.upsert(
            SongTempoEntity(
                songId = song.id,
                sizeBytes = song.sizeBytes,
                dateModified = song.dateModified,
                bpm = estimate.bpm,
                confidence = estimate.confidence.name,
            ),
        )
        return estimate
    }

    private suspend fun analyze(song: Song, onProgress: (Float) -> Unit): TempoEstimate? {
        val uri = stemRepository.stemUri(song.id, StemMode.Instrumental) ?: song.uri
        val startMs = ((song.durationMs - WINDOW_MS) / 2).coerceIn(0, MAX_START_MS)
        val windowFrames = WINDOW_MS * MediaPcmSource.TARGET_RATE / 1000
        val mono = FloatArray((windowFrames / DECIMATION).toInt())
        var written = 0
        // Partial sums carry over between decoder blocks so every output sample averages DECIMATION input frames.
        var pending = 0f
        var pendingCount = 0
        var decoded = 0L
        val context = currentCoroutineContext()
        try {
            MediaPcmSource(this.context, uri, maxFrames = windowFrames, startUs = startMs * 1000).read { block, frames ->
                if (!context.isActive) throw Stopped()
                for (frame in 0 until frames) {
                    pending += (block[frame * 2] + block[frame * 2 + 1]) * 0.5f
                    if (++pendingCount == DECIMATION) {
                        if (written < mono.size) mono[written++] = pending / DECIMATION
                        pending = 0f
                        pendingCount = 0
                    }
                }
                decoded += frames
                onProgress((decoded.toFloat() / windowFrames).coerceAtMost(1f))
            }
        } catch (e: Stopped) {
            throw CancellationException("Tempo detection cancelled")
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode ${song.id} for tempo detection", e)
            return null
        }
        return TempoDetector(TempoDetector.ANALYSIS_RATE).detect(mono, written)
    }

    /** Not a CancellationException, which the decoder would report as a corrupt file. */
    private class Stopped : RuntimeException()

    private companion object {
        const val TAG = "SongTempoAnalyzer"
        const val WINDOW_MS = 45_000L
        const val MAX_START_MS = 45_000L
        const val DECIMATION = MediaPcmSource.TARGET_RATE / TempoDetector.ANALYSIS_RATE
    }
}
