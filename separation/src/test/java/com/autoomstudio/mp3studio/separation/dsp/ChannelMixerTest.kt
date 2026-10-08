package com.autoomstudio.mp3studio.separation.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelMixerTest {

    @Test
    fun monoIsDuplicated() {
        val out = FloatArray(4)
        ChannelMixer.toStereo(floatArrayOf(0.1f, -0.2f), channels = 1, frames = 2, output = out)
        assertArrayEquals(floatArrayOf(0.1f, 0.1f, -0.2f, -0.2f), out, 0f)
    }

    @Test
    fun stereoIsCopied() {
        val out = FloatArray(4)
        ChannelMixer.toStereo(floatArrayOf(1f, 2f, 3f, 4f), channels = 2, frames = 2, output = out)
        assertArrayEquals(floatArrayOf(1f, 2f, 3f, 4f), out, 0f)
    }

    @Test
    fun fivePointOneKeepsCentreAndDropsLfe() {
        val out = FloatArray(2)
        // FL FR FC LFE BL BR
        ChannelMixer.toStereo(floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f), channels = 6, frames = 1, output = out)
        assertEquals(out[0], out[1], 0f)
        assertEquals(0.70710677f / (1 + 2 * 0.70710677f), out[0], 1e-6f)
    }

    @Test
    fun otherLayoutsKeepFirstTwoChannels() {
        val out = FloatArray(2)
        ChannelMixer.toStereo(floatArrayOf(0.3f, 0.4f, 0.9f, 0.9f), channels = 4, frames = 1, output = out)
        assertArrayEquals(floatArrayOf(0.3f, 0.4f), out, 0f)
    }
}
