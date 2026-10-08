package com.autoomstudio.mp3studio.data.tempo

import android.net.Uri
import com.autoomstudio.mp3studio.data.model.Song
import com.autoomstudio.mp3studio.metronome.RhythmEstimate
import com.autoomstudio.mp3studio.metronome.TempoConfidence
import com.autoomstudio.mp3studio.metronome.TempoEstimate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class TempoDetectionGateTest {

    private val uri: Uri = mock(Uri::class.java)
    private val song = Song(
        id = 7,
        uri = uri,
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = 1,
        durationMs = 200_000,
        dateAdded = 0,
        albumArtUri = uri,
    )
    private val rhythm = RhythmEstimate(TempoEstimate(120.0, TempoConfidence.High, 1f), meter = null)

    private class FakeSource(private val cached: RhythmEstimate?, private val detected: RhythmEstimate?) : TempoSource {
        var detectCalls = 0
        override suspend fun cached(song: Song) = cached
        override suspend fun detect(song: Song, onProgress: (Float) -> Unit): RhythmEstimate? {
            detectCalls++
            return detected
        }
    }

    @Test
    fun aCachedTempoIsUsableOnAnyPlan() = runBlocking {
        val source = FakeSource(cached = rhythm, detected = null)
        val result = TempoDetectionGate(source) { false }.detect(song) {}
        assertEquals(GatedDetection.Finished(rhythm), result)
        assertEquals(0, source.detectCalls)
    }

    @Test
    fun aNewDetectionIsLockedWithoutBpmDetector() = runBlocking {
        val source = FakeSource(cached = null, detected = rhythm)
        val result = TempoDetectionGate(source) { false }.detect(song) {}
        assertEquals(GatedDetection.Locked, result)
        assertEquals(0, source.detectCalls)
    }

    @Test
    fun aNewDetectionRunsWithBpmDetector() = runBlocking {
        val source = FakeSource(cached = null, detected = rhythm)
        val result = TempoDetectionGate(source) { true }.detect(song) {}
        assertEquals(GatedDetection.Finished(rhythm), result)
        assertEquals(1, source.detectCalls)
    }
}
