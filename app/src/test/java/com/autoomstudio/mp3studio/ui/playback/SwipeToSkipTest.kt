package com.autoomstudio.mp3studio.ui.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeToSkipTest {

    private fun decide(offset: Float, velocity: Float = 0f, canNext: Boolean = true, canPrevious: Boolean = true) =
        swipeDecision(offset, velocity, width = 1000f, flingVelocity = 2000f, canNext = canNext, canPrevious = canPrevious)

    @Test
    fun `a long swipe left plays the next song and right the previous one`() {
        assertEquals(SwipeResult.Next, decide(-301f))
        assertEquals(SwipeResult.Previous, decide(301f))
    }

    @Test
    fun `a short slow swipe springs back`() {
        assertEquals(SwipeResult.None, decide(-299f))
        assertEquals(SwipeResult.None, decide(299f))
        assertEquals(SwipeResult.None, decide(0f, velocity = -5000f))
    }

    @Test
    fun `a fast fling counts even when short`() {
        assertEquals(SwipeResult.Next, decide(-40f, velocity = -2500f))
        assertEquals(SwipeResult.Previous, decide(40f, velocity = 2500f))
    }

    @Test
    fun `a fling back toward the middle cancels`() {
        assertEquals(SwipeResult.None, decide(-100f, velocity = 2500f))
    }

    @Test
    fun `no song in that direction springs back`() {
        assertEquals(SwipeResult.None, decide(-600f, velocity = -5000f, canNext = false))
        assertEquals(SwipeResult.None, decide(600f, velocity = 5000f, canPrevious = false))
    }

    @Test
    fun `nothing happens before the element is measured`() {
        assertEquals(SwipeResult.None, swipeDecision(-500f, -5000f, 0f, 2000f, canNext = true, canPrevious = true))
    }
}
