package com.autoomstudio.mp3studio.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSyncTest {

    private val rate = 48_000
    private val grid = BeatGrid(downbeatMs = 1_000.0, periodMs = 500.0)

    @Test
    fun downbeatLandsWhereTheSongReachesIt() {
        // Song at 0 ms heard at t = 10 s; metronome frame 0 heard at t = 10 s too. The downbeat is 1 s later.
        val music = MusicSnapshot(positionMs = 0, nanoTime = 10_000_000_000, speed = 1f)
        val heard = HeardFrame(frame = 0, nanoTime = 10_000_000_000)
        val alignment = SongSync.align(grid, music, heard, rate)
        assertEquals(48_000.0, alignment.anchorFrame, 0.01)
        assertEquals(24_000.0, alignment.framesPerBeat, 0.01)
    }

    @Test
    fun accountsForWhenEachSideWasHeard() {
        // The song was at 4.2 s when t = 20 s; frame 96000 was heard at t = 20.1 s.
        val music = MusicSnapshot(positionMs = 4_200, nanoTime = 20_000_000_000, speed = 1f)
        val heard = HeardFrame(frame = 96_000, nanoTime = 20_100_000_000)
        val alignment = SongSync.align(grid, music, heard, rate)
        // The downbeat (1 s) was 3.2 s before t = 20 s, i.e. 3.3 s before frame 96000.
        assertEquals(96_000 - 3.3 * rate, alignment.anchorFrame, 0.01)
        // Song beat 9 (5.5 s) is then 1.3 s after t = 20 s.
        val beat9 = alignment.anchorFrame + 9 * alignment.framesPerBeat
        assertEquals(96_000 + 1.2 * rate, beat9, 0.01)
    }

    @Test
    fun speedShortensThePeriod() {
        val music = MusicSnapshot(positionMs = 1_000, nanoTime = 0, speed = 1.25f)
        val alignment = SongSync.align(grid, music, HeardFrame(0, 0), rate)
        assertEquals(24_000.0 / 1.25, alignment.framesPerBeat, 0.01)
        assertEquals(0.0, alignment.anchorFrame, 0.01)
    }

    @Test
    fun errorIsSignedAndInMilliseconds() {
        val current = Alignment(anchorFrame = 1_000.0, framesPerBeat = 24_000.0)
        val earlier = current.copy(anchorFrame = 1_000.0 - 0.03 * rate)
        // The song's beats now come 30 ms earlier than the clicks, so the clicks are 30 ms late.
        assertEquals(30.0, SongSync.errorMs(current, earlier, atFrame = 500_000, rate), 0.01)
        assertEquals(-30.0, SongSync.errorMs(earlier, current, atFrame = 500_000, rate), 0.01)
        assertEquals(0.0, SongSync.errorMs(current, current, atFrame = 500_000, rate), 0.0)
    }

    @Test
    fun changedPeriodIsAlwaysOutOfSync() {
        val current = Alignment(anchorFrame = 0.0, framesPerBeat = 24_000.0)
        val faster = Alignment(anchorFrame = 0.0, framesPerBeat = 19_200.0)
        assertTrue(SongSync.errorMs(current, faster, atFrame = 0, rate).isInfinite())
    }
}
