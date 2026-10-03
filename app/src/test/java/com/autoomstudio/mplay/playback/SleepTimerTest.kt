package com.autoomstudio.mplay.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepTimerTest {

    @Test
    fun startCountsFromNow() {
        assertEquals(SleepTimerStatus.Running(1_000L + 30 * 60_000L), SleepTimer.start(30, nowElapsedMs = 1_000L))
    }

    @Test
    fun startClampsToAllowedRange() {
        assertEquals(SleepTimerStatus.Running(60_000L), SleepTimer.start(0, nowElapsedMs = 0L))
        assertEquals(SleepTimerStatus.Running(180 * 60_000L), SleepTimer.start(500, nowElapsedMs = 0L))
    }

    @Test
    fun extendAddsToTheEndTime() {
        val running = SleepTimerStatus.Running(10 * 60_000L)
        assertEquals(SleepTimerStatus.Running(20 * 60_000L), SleepTimer.extend(running, 10, nowElapsedMs = 0L))
    }

    @Test
    fun extendAfterExpiryCountsFromNow() {
        val expired = SleepTimerStatus.Running(5_000L)
        assertEquals(
            SleepTimerStatus.Running(9_000L + 10 * 60_000L),
            SleepTimer.extend(expired, 10, nowElapsedMs = 9_000L),
        )
    }

    @Test
    fun extendTurnsEndOfSongIntoTimedTimer() {
        assertEquals(
            SleepTimerStatus.Running(2_000L + 10 * 60_000L),
            SleepTimer.extend(SleepTimerStatus.EndOfSong, 10, nowElapsedMs = 2_000L),
        )
    }

    @Test
    fun extendDoesNothingWhenOff() {
        assertEquals(SleepTimerStatus.Off, SleepTimer.extend(SleepTimerStatus.Off, 10, nowElapsedMs = 0L))
    }

    @Test
    fun remainingNeverNegative() {
        val running = SleepTimerStatus.Running(1_000L)
        assertEquals(500L, SleepTimer.remainingMs(running, nowElapsedMs = 500L))
        assertEquals(0L, SleepTimer.remainingMs(running, nowElapsedMs = 5_000L))
    }

    @Test
    fun volumeIsFullUntilTheFadeThenLinear() {
        assertEquals(1f, SleepTimer.volumeAt(60_000L), 0f)
        assertEquals(1f, SleepTimer.volumeAt(SleepTimer.FADE_MS), 0f)
        assertEquals(0.5f, SleepTimer.volumeAt(SleepTimer.FADE_MS / 2), 0.001f)
        assertEquals(0f, SleepTimer.volumeAt(0L), 0f)
    }

    @Test
    fun songRemainingAccountsForPlaybackSpeed() {
        assertEquals(90_000L, SleepTimer.songRemainingMs(durationMs = 120_000L, positionMs = 30_000L, speed = 1f))
        assertEquals(100_000L, SleepTimer.songRemainingMs(durationMs = 120_000L, positionMs = 30_000L, speed = 0.9f))
        assertEquals(0L, SleepTimer.songRemainingMs(durationMs = 10_000L, positionMs = 12_000L, speed = 1f))
    }
}
