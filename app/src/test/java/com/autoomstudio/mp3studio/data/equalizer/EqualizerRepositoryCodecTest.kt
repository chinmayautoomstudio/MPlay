package com.autoomstudio.mp3studio.data.equalizer

import com.autoomstudio.mp3studio.playback.equalizer.EqualizerBands
import com.autoomstudio.mp3studio.playback.equalizer.SavedEqualizerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerRepositoryCodecTest {

    @Test
    fun gainsRoundTrip() {
        val gains = floatArrayOf(-1f, 2f, 3.5f, 0f, -12f, 12f, 0.5f)
        val encoded = EqualizerRepository.encodeGains(gains)
        assertTrue(EqualizerRepository.decodeGains(encoded).contentEquals(gains))
    }

    @Test
    fun corruptGainsFallBackToFlat() {
        assertTrue(EqualizerRepository.decodeGains("bad").contentEquals(EqualizerBands.flatGains()))
        assertTrue(EqualizerRepository.decodeGains(null).contentEquals(EqualizerBands.flatGains()))
        assertTrue(EqualizerRepository.decodeGains("1,2").contentEquals(EqualizerBands.flatGains()))
    }

    @Test
    fun savedPresetsRoundTrip() {
        val presets = listOf(
            SavedEqualizerPreset("My Rock", floatArrayOf(5f, 3f, -1f, -1f, 3f, 4f, 5f), preampDb = 1.5f),
            SavedEqualizerPreset("Soft", EqualizerBands.flatGains(), preampDb = -2f),
        )
        val decoded = EqualizerRepository.decodeSaved(EqualizerRepository.encodeSaved(presets))
        assertEquals(2, decoded.size)
        assertEquals("My Rock", decoded[0].name)
        assertEquals(1.5f, decoded[0].preampDb)
        assertTrue(decoded[0].gainsDb.contentEquals(presets[0].gainsDb))
    }

    @Test
    fun corruptSavedPresetsFallBackToEmpty() {
        assertTrue(EqualizerRepository.decodeSaved("||||").isEmpty())
        assertTrue(EqualizerRepository.decodeSaved("no-pipes-here").isEmpty())
        assertTrue(EqualizerRepository.decodeSaved(null).isEmpty())
    }
}
