package com.autoomstudio.mplay.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `formats minutes and seconds`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:31", formatDuration(31_999))
        assertEquals("3:05", formatDuration(185_000))
    }

    @Test
    fun `formats hours`() {
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `negative durations clamp to zero`() {
        assertEquals("0:00", formatDuration(-5))
    }
}
