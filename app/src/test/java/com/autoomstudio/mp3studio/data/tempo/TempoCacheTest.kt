package com.autoomstudio.mp3studio.data.tempo

import com.autoomstudio.mp3studio.metronome.BeatGrid
import com.autoomstudio.mp3studio.metronome.MeterEstimate
import com.autoomstudio.mp3studio.metronome.RhythmEstimate
import com.autoomstudio.mp3studio.metronome.TempoConfidence
import com.autoomstudio.mp3studio.metronome.TempoEstimate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoCacheTest {

    private val size = 4_000_000L
    private val date = 1_700_000_000L

    /** A row as it was stored before time signatures (schema 4, migrated with null meter columns). */
    private val legacy = SongTempoEntity(songId = 7, sizeBytes = size, dateModified = date, bpm = 120.0, confidence = "High")

    private val waltz = RhythmEstimate(
        TempoEstimate(90.0, TempoConfidence.High, 0f),
        MeterEstimate(3, 4, TempoConfidence.Medium, 0f, 90),
        BeatGrid(61_234.5, 666.667),
    )

    @Test
    fun nothingStoredIsEmpty() {
        assertEquals(TempoLookup.Empty, TempoCache.lookup(null, size, date))
    }

    @Test
    fun legacyRowWithoutMeterForcesAnotherAnalysis() {
        assertEquals(TempoLookup.Incomplete, TempoCache.lookup(legacy, size, date))
    }

    @Test
    fun schemaFiveRowWithoutGridForcesAnotherAnalysis() {
        val schemaFive = legacy.copy(meterConfidence = "High", beatsPerBar = 3, beatUnit = 4, meterBpm = 90)
        assertEquals(TempoLookup.Incomplete, TempoCache.lookup(schemaFive, size, date))
    }

    @Test
    fun changedFileIsStaleEvenWithAMeter() {
        val entry = TempoCache.entry(7, size, date, waltz)
        assertEquals(TempoLookup.Stale, TempoCache.lookup(entry, size + 1, date))
        assertEquals(TempoLookup.Stale, TempoCache.lookup(entry, size, date + 1))
        assertEquals(TempoLookup.Stale, TempoCache.lookup(legacy, size, date + 1))
    }

    @Test
    fun storedMeterAndGridRoundTrip() {
        val lookup = TempoCache.lookup(TempoCache.entry(7, size, date, waltz), size, date)
        assertTrue(lookup is TempoLookup.Hit)
        val rhythm = (lookup as TempoLookup.Hit).rhythm
        assertEquals(90.0, rhythm.tempo.bpm, 0.0)
        assertEquals(TempoConfidence.High, rhythm.tempo.confidence)
        assertEquals(MeterEstimate(3, 4, TempoConfidence.Medium, 0f, 90), rhythm.meter)
        assertEquals(BeatGrid(61_234.5, 666.667), rhythm.grid)
    }

    @Test
    fun sixEightKeepsItsClickBpm() {
        val sixEight = RhythmEstimate(
            TempoEstimate(60.0, TempoConfidence.Medium, 0f),
            MeterEstimate(6, 8, TempoConfidence.High, 0f, 180),
            BeatGrid(1_000.0, 333.333),
        )
        val rhythm = (TempoCache.lookup(TempoCache.entry(1, size, date, sixEight), size, date) as TempoLookup.Hit).rhythm
        assertEquals(180, rhythm.meter!!.clickBpm)
        assertEquals(333.333, rhythm.grid!!.periodMs, 0.0)
    }

    @Test
    fun undeterminedMeterIsCachedAsNoMeter() {
        val rhythm = RhythmEstimate(TempoEstimate(100.0, TempoConfidence.Low, 0f), null, BeatGrid(250.0, 600.0))
        val entry = TempoCache.entry(7, size, date, rhythm)
        assertEquals(TempoCache.NO_METER, entry.meterConfidence)
        val lookup = TempoCache.lookup(entry, size, date)
        assertTrue(lookup is TempoLookup.Hit)
        assertNull((lookup as TempoLookup.Hit).rhythm.meter)
        assertEquals(BeatGrid(250.0, 600.0), lookup.rhythm.grid)
    }

    @Test
    fun missingGridIsCachedAsNoGrid() {
        val entry = TempoCache.entry(7, size, date, RhythmEstimate(TempoEstimate(100.0, TempoConfidence.Low, 0f), null))
        assertEquals(TempoCache.NO_GRID, entry.beatPeriodMs!!, 0.0)
        val lookup = TempoCache.lookup(entry, size, date)
        assertTrue(lookup is TempoLookup.Hit)
        assertNull((lookup as TempoLookup.Hit).rhythm.grid)
    }

    @Test
    fun incompleteMeterRowIsReanalyzed() {
        val broken = legacy.copy(meterConfidence = "High", beatsPerBar = 3, beatUnit = null, meterBpm = 90, beatPeriodMs = 500.0, downbeatMs = 0.0)
        assertEquals(TempoLookup.Incomplete, TempoCache.lookup(broken, size, date))
    }

    @Test
    fun gridWithoutDownbeatIsReanalyzed() {
        val broken = TempoCache.entry(7, size, date, waltz).copy(downbeatMs = null)
        assertEquals(TempoLookup.Incomplete, TempoCache.lookup(broken, size, date))
    }
}
