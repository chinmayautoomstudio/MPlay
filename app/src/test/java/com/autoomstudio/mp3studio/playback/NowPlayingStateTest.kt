package com.autoomstudio.mp3studio.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingStateTest {

    @Test
    fun repeatCyclesOffAllOne() {
        assertEquals(RepeatMode.All, nextRepeatMode(RepeatMode.Off))
        assertEquals(RepeatMode.One, nextRepeatMode(RepeatMode.All))
        assertEquals(RepeatMode.Off, nextRepeatMode(RepeatMode.One))
    }
}
