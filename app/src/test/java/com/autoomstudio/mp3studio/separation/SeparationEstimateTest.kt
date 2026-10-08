package com.autoomstudio.mp3studio.separation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeparationEstimateTest {

    @Test
    fun firstMeasurementBecomesTheFactor() {
        assertEquals(1.5, SeparationEstimate.updatedFactor(null, 1.5)!!, 1e-9)
    }

    @Test
    fun laterMeasurementsAreAveraged() {
        assertEquals(1.3, SeparationEstimate.updatedFactor(1.0, 2.0)!!, 1e-9)
    }

    @Test
    fun impossibleMeasurementsKeepThePreviousFactor() {
        assertEquals(1.0, SeparationEstimate.updatedFactor(1.0, 0.0)!!, 1e-9)
        assertEquals(1.0, SeparationEstimate.updatedFactor(1.0, Double.NaN)!!, 1e-9)
        assertNull(SeparationEstimate.updatedFactor(null, -2.0))
    }

    @Test
    fun noHistoryGivesNoEstimate() {
        assertNull(SeparationEstimate.minutes(null, listOf(240_000L)))
    }

    @Test
    fun minutesRoundUpAndCoverEverySong() {
        // Two 4-minute songs at 1.1x real time: 8.8 minutes.
        assertEquals(9, SeparationEstimate.minutes(1.1, listOf(240_000L, 240_000L)))
    }

    @Test
    fun shortSongsStillShowAtLeastOneMinute() {
        assertEquals(1, SeparationEstimate.minutes(0.2, listOf(30_000L)))
    }

    @Test
    fun unknownDurationsGiveNoEstimate() {
        assertNull(SeparationEstimate.minutes(1.0, listOf(0L)))
    }
}
