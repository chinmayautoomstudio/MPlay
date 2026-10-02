package com.autoomstudio.mplay.data.clip

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PeakAccumulatorTest {

    @Test
    fun producesRequestedBucketCount() {
        val acc = PeakAccumulator(bucketCount = 10, totalFrames = 1_000)
        acc.add(ShortArray(1_000) { 100 }, 1_000, channels = 1)
        assertEquals(10, acc.result().size)
    }

    @Test
    fun normalizesAgainstLoudestPeak() {
        val acc = PeakAccumulator(bucketCount = 4, totalFrames = 4)
        acc.add(shortArrayOf(1_000, -2_000, 500, 4_000), 4, channels = 1)
        assertArrayEquals(floatArrayOf(0.25f, 0.5f, 0.125f, 1f), acc.result(), 0.0001f)
    }

    @Test
    fun silenceGivesZeros() {
        val acc = PeakAccumulator(bucketCount = 5, totalFrames = 50)
        acc.add(ShortArray(50), 50, channels = 1)
        assertArrayEquals(FloatArray(5), acc.result(), 0f)
    }

    @Test
    fun stereoUsesLouderChannelPerFrame() {
        val acc = PeakAccumulator(bucketCount = 2, totalFrames = 2)
        acc.add(shortArrayOf(100, -400, 200, 50), 4, channels = 2)
        assertArrayEquals(floatArrayOf(1f, 0.5f), acc.result(), 0.0001f)
    }

    @Test
    fun framesBeyondEstimateLandInLastBucket() {
        val acc = PeakAccumulator(bucketCount = 2, totalFrames = 2)
        acc.add(shortArrayOf(10, 10, 10, 1_000), 4, channels = 1)
        assertArrayEquals(floatArrayOf(0.01f, 1f), acc.result(), 0.0001f)
    }

    @Test
    fun handlesChunkedInput() {
        val acc = PeakAccumulator(bucketCount = 2, totalFrames = 4)
        acc.add(shortArrayOf(100, 200, 0, 0), 2, channels = 1)
        acc.add(shortArrayOf(400, 300), 2, channels = 1)
        assertArrayEquals(floatArrayOf(0.5f, 1f), acc.result(), 0.0001f)
    }
}
