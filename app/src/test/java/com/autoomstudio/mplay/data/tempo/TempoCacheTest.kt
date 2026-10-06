package com.autoomstudio.mplay.data.tempo

import com.autoomstudio.mplay.metronome.MeterEstimate
import com.autoomstudio.mplay.metronome.RhythmEstimate
import com.autoomstudio.mplay.metronome.TempoConfidence
import com.autoomstudio.mplay.metronome.TempoEstimate
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
    )

    @Test
    fun nothingStoredIsEmpty() {
        assertEquals(TempoLookup.Empty, TempoCache.lookup(null, size, date))
    }

    @Test
    fun legacyRowWithoutMeterForcesAnotherAnalysis() {
        assertEquals(TempoLookup.MissingMeter, TempoCache.lookup(legacy, size, date))
    }

    @Test
    fun changedFileIsStaleEvenWithAMeter() {
        val entry = TempoCache.entry(7, size, date, waltz)
        assertEquals(TempoLookup.Stale, TempoCache.lookup(entry, size + 1, date))
        assertEquals(TempoLookup.Stale, TempoCache.lookup(entry, size, date + 1))
        assertEquals(TempoLookup.Stale, TempoCache.lookup(legacy, size, date + 1))
    }

    @Test
    fun storedMeterRoundTrips() {
        val lookup = TempoCache.lookup(TempoCache.entry(7, size, date, waltz), size, date)
        assertTrue(lookup is TempoLookup.Hit)
        val rhythm = (lookup as TempoLookup.Hit).rhythm
        assertEquals(90.0, rhythm.tempo.bpm, 0.0)
        assertEquals(TempoConfidence.High, rhythm.tempo.confidence)
        assertEquals(MeterEstimate(3, 4, TempoConfidence.Medium, 0f, 90), rhythm.meter)
    }

    @Test
    fun sixEightKeepsItsClickBpm() {
        val sixEight = RhythmEstimate(
            TempoEstimate(60.0, TempoConfidence.Medium, 0f),
            MeterEstimate(6, 8, TempoConfidence.High, 0f, 180),
        )
        val rhythm = (TempoCache.lookup(TempoCache.entry(1, size, date, sixEight), size, date) as TempoLookup.Hit).rhythm
        assertEquals(180, rhythm.meter!!.clickBpm)
    }

    @Test
    fun undeterminedMeterIsCachedAsNoMeter() {
        val entry = TempoCache.entry(7, size, date, RhythmEstimate(TempoEstimate(100.0, TempoConfidence.Low, 0f), null))
        assertEquals(TempoCache.NO_METER, entry.meterConfidence)
        val lookup = TempoCache.lookup(entry, size, date)
        assertTrue(lookup is TempoLookup.Hit)
        assertNull((lookup as TempoLookup.Hit).rhythm.meter)
    }

    @Test
    fun incompleteMeterRowIsReanalyzed() {
        val broken = legacy.copy(meterConfidence = "High", beatsPerBar = 3, beatUnit = null, meterBpm = 90)
        assertEquals(TempoLookup.MissingMeter, TempoCache.lookup(broken, size, date))
    }
}
