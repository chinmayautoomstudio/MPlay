package com.autoomstudio.mplay.separation.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentPlanTest {

    private val segment = 343_980

    @Test
    fun strideMatchesApplyModel() {
        assertEquals(257_985, SegmentPlan(1_000_000, segment, 0.25).stride)
        assertEquals(309_582, SegmentPlan(1_000_000, segment, 0.1).stride)
    }

    @Test
    fun offsetsCoverTheTrack() {
        val total = 10_584_000L // 4 minutes
        val plan = SegmentPlan(total, segment, 0.25)
        assertEquals(0L, plan.offset(0))
        assertTrue(plan.offset(plan.count - 1) < total)
        assertTrue(plan.offset(plan.count - 1) + plan.stride >= total)
        assertEquals(total, plan.offset(plan.count - 1) + plan.length(plan.count - 1))
    }

    @Test
    fun shortLastSegmentIsCentredInItsWindow() {
        val plan = SegmentPlan(400_000, segment, 0.25)
        assertEquals(2, plan.count)
        val last = plan.length(1)
        assertEquals(400_000 - 257_985, last)
        assertEquals(257_985L - (segment - last) / 2, plan.windowStart(1))
    }

    @Test
    fun trackShorterThanOneSegmentStartsBeforeZero() {
        val plan = SegmentPlan(44_100, segment, 0.25)
        assertEquals(1, plan.count)
        assertEquals(-(segment - 44_100L) / 2, plan.windowStart(0))
        assertEquals(44_100L, plan.finalBefore(0))
    }

    @Test
    fun weightIsTriangularAndPeaksAtOne() {
        val plan = SegmentPlan(1_000_000, segment, 0.25)
        val half = segment / 2
        assertEquals(1f / half, plan.weight(0), 0f)
        assertEquals(1f, plan.weight(half - 1), 0f)
        assertEquals(1f, plan.weight(half), 0f)
        assertEquals(1f / half, plan.weight(segment - 1), 0f)
    }

    @Test
    fun emptyTrackHasNoSegments() {
        assertEquals(0, SegmentPlan(0, segment, 0.25).count)
    }
}
