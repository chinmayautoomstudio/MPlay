package com.autoomstudio.mplay.singalong

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class SingAlongMixerTest {

    @Test
    fun quietSignalsPassThroughUnchanged() {
        listOf(0f, 0.3f, -0.5f, 0.8f, -0.8f).forEach { assertEquals(it, SingAlongMixer.limit(it), 1e-6f) }
    }

    @Test
    fun loudSignalsNeverReachFullScale() {
        listOf(0.9f, 1f, 2f, 10f, -3f).forEach { x ->
            val y = SingAlongMixer.limit(x)
            assertTrue("$x -> $y", abs(y) < 1f && abs(y) > 0.8f)
            assertEquals(Math.signum(x), Math.signum(y))
        }
    }

    @Test
    fun limiterIsMonotonic() {
        var previous = SingAlongMixer.limit(-5f)
        var x = -5f
        while (x <= 5f) {
            val y = SingAlongMixer.limit(x)
            assertTrue(y >= previous)
            previous = y
            x += 0.01f
        }
    }

    @Test
    fun mixAppliesLevelsAndCentersTheVoice() {
        val instrumental = floatArrayOf(0.2f, -0.2f, 0.1f, 0.1f)
        val voice = floatArrayOf(0.1f, 0.2f)
        val out = FloatArray(4)
        SingAlongMixer.mix(instrumental, voice, 2, MixSettings(voiceGain = 1f, instrumentalGain = 0.5f), out)
        assertEquals(0.2f, out[0], 1e-6f)
        assertEquals(0f, out[1], 1e-6f)
        assertEquals(0.25f, out[2], 1e-6f)
        assertEquals(0.25f, out[3], 1e-6f)
    }

    @Test
    fun mutedTrackLeavesOnlyTheOther() {
        val out = FloatArray(2)
        SingAlongMixer.mix(floatArrayOf(0.5f, 0.5f), floatArrayOf(0.3f), 1, MixSettings(0f, 1f), out)
        assertEquals(0.5f, out[0], 1e-6f)
        SingAlongMixer.mix(floatArrayOf(0.5f, 0.5f), floatArrayOf(0.3f), 1, MixSettings(1f, 0f), out)
        assertEquals(0.3f, out[1], 1e-6f)
    }

    @Test
    fun offsetShiftsWhichVoiceFrameIsHeard() {
        // 100 ms is 4410 frames at 44.1 kHz.
        assertEquals(4_410L, SingAlongMixer.offsetFrames(100))
        assertEquals(1_000L, SingAlongMixer.voiceFrameFor(0, leadFrames = 1_000, offsetMs = 0))
        // Positive offset plays the voice later, so an earlier voice frame lines up with the same music.
        assertEquals(1_000L - 4_410L, SingAlongMixer.voiceFrameFor(0, 1_000, 100))
        assertEquals(1_000L + 13_230L, SingAlongMixer.voiceFrameFor(0, 1_000, -300))
    }

    @Test
    fun voiceFileReadsSamplesAndPadsWithSilence() {
        val file = File.createTempFile("voice", ".pcm")
        try {
            val samples = shortArrayOf(0, 16384, -16384, 32767)
            val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { bytes.putShort(it) }
            file.writeBytes(bytes.array())
            VoiceFile(file).use { voice ->
                assertEquals(4L, voice.frames)
                val out = FloatArray(4)
                voice.read(-1, 4, out)
                assertEquals(0f, out[0], 0f)
                assertEquals(0f, out[1], 0f)
                assertEquals(0.5f, out[2], 1e-4f)
                assertEquals(-0.5f, out[3], 1e-4f)
                voice.read(3, 4, out)
                assertEquals(32767 / 32768f, out[0], 1e-4f)
                assertEquals(0f, out[1], 0f)
            }
        } finally {
            file.delete()
        }
    }
}
