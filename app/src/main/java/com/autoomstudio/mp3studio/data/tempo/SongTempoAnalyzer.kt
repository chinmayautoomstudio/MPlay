package com.autoomstudio.mp3studio.data.tempo

import android.content.Context
import android.util.Log
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.data.stems.StemMode
import com.autoomstudio.mp3studio.data.stems.StemRepository
import com.autoomstudio.mp3studio.metronome.RhythmEstimate
import com.autoomstudio.mp3studio.metronome.TempoDetector
import com.autoomstudio.mp3studio.separation.android.MediaPcmSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Detects a song's tempo and time signature from a slice of its middle (MT9, MT18), preferring the instrumental
 * stem when the song has been separated, since the drums are clearer without vocals (MT11). Results are cached per
 * song (MT10, MT20).
 */
class SongTempoAnalyzer(
    private val context: Context,
    private val dao: TempoDao,
    private val stemRepository: StemRepository,
) {
    /** The stored result, if the song's file hasn't changed since it was analyzed with meter detection. */
    suspend fun cached(song: Song): RhythmEstimate? =
        when (val lookup = TempoCache.lookup(dao.tempo(song.id), song.sizeBytes, song.dateModified)) {
            is TempoLookup.Hit -> lookup.rhythm
            TempoLookup.Stale -> {
                dao.delete(song.id)
                null
            }
            TempoLookup.Empty, TempoLookup.Incomplete -> null
        }

    /** Returns null when the file can't be decoded or has no clear beat. */
    suspend fun detect(song: Song, onProgress: (Float) -> Unit): RhythmEstimate? {
        cached(song)?.let { return it }
        val rhythm = withContext(Dispatchers.Default) { analyze(song, onProgress) } ?: return null
        dao.upsert(TempoCache.entry(song.id, song.sizeBytes, song.dateModified, rhythm))
        return rhythm
    }

    private suspend fun analyze(song: Song, onProgress: (Float) -> Unit): RhythmEstimate? {
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
            MediaPcmSource(
                this.context, uri, maxFrames = windowFrames, startUs = startMs * 1000, exactStart = true,
            ).read { block, frames ->
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
        val rhythm = TempoDetector(TempoDetector.ANALYSIS_RATE).detectRhythm(mono, written) ?: return null
        // The grid is measured from the start of the window; the metronome needs it in song time.
        return rhythm.copy(grid = rhythm.grid?.shifted(startMs.toDouble()))
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
