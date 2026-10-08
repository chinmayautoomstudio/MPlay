package com.autoomstudio.mp3studio.separation.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class TrackStatsTest {

    @Test
    fun meanAndSampleStdOfMonoMix() {
        val stats = TrackStats()
        // Mono values 1, 2, 3, 4 split over two blocks.
        stats.add(floatArrayOf(0f, 2f, 2f, 2f), 2)
        stats.add(floatArrayOf(3f, 3f, 5f, 3f), 2)
        val result = stats.result()
        assertEquals(4L, stats.frames)
        assertEquals(2.5f, result.mean, 1e-6f)
        assertEquals(sqrt(5.0 / 3).toFloat(), result.std, 1e-6f)
    }

    @Test
    fun silenceGetsUnitStd() {
        val stats = TrackStats()
        stats.add(FloatArray(200), 100)
        assertEquals(1f, stats.result().std, 0f)
    }
}
