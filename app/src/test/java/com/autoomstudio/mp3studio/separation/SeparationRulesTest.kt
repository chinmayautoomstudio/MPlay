package com.autoomstudio.mp3studio.separation

import com.autoomstudio.mp3studio.data.stems.StemMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeparationRulesTest {

    private val gib = 1024L * 1024 * 1024
    private val releaseAbis = setOf("arm64-v8a")
    private val capable = DeviceSpecs(
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        totalRamBytes = (5.6 * gib).toLong(),
        isLowRamDevice = false,
        freeStorageBytes = 8 * gib,
    )

    @Test
    fun sixGigabytePhoneIsEligible() {
        assertTrue(DeviceEligibility.check(capable, releaseAbis).isEmpty())
    }

    @Test
    fun thirtyTwoBitOrX86IsNotEligible() {
        assertEquals(
            listOf(UnsupportedReason.Architecture),
            DeviceEligibility.check(capable.copy(supportedAbis = listOf("armeabi-v7a", "armeabi")), releaseAbis),
        )
    }

    @Test
    fun x86WithArmTranslationNeedsTheRuntimeForItsPrimaryAbi() {
        val emulator = capable.copy(supportedAbis = listOf("x86_64", "arm64-v8a"))
        assertEquals(listOf(UnsupportedReason.Architecture), DeviceEligibility.check(emulator, releaseAbis))
        assertTrue(DeviceEligibility.check(emulator, releaseAbis + "x86_64").isEmpty())
    }

    @Test
    fun fourGigabytePhoneIsNotEligible() {
        assertEquals(
            listOf(UnsupportedReason.Memory),
            DeviceEligibility.check(capable.copy(totalRamBytes = (3.7 * gib).toLong()), releaseAbis),
        )
    }

    @Test
    fun lowRamDeviceIsNotEligibleWhateverItReports() {
        assertEquals(
            listOf(UnsupportedReason.Memory),
            DeviceEligibility.check(capable.copy(isLowRamDevice = true), releaseAbis),
        )
    }

    @Test
    fun allReasonsAreReported() {
        val reasons = DeviceEligibility.check(
            DeviceSpecs(listOf("x86"), gib, isLowRamDevice = false, freeStorageBytes = 100L * 1024 * 1024),
            releaseAbis,
        )
        assertEquals(
            listOf(UnsupportedReason.Architecture, UnsupportedReason.Memory, UnsupportedReason.Storage),
            reasons,
        )
    }

    @Test
    fun fourMinuteSongNeedsAboutTwelveMegabytes() {
        // 2 stems x 192 kbps x 240 s = 11.52 MB, plus 5% container overhead.
        assertEquals(12_096_000L, StorageEstimate.stemBytes(240_000))
        assertEquals(12_096_000L + StorageEstimate.MARGIN_BYTES, StorageEstimate.requiredFreeBytes(240_000))
    }

    @Test
    fun batchEstimateAddsSongsButOneMargin() {
        val songs = listOf(240_000L, 180_000L, -5L)
        assertEquals(
            StorageEstimate.stemBytes(240_000) + StorageEstimate.stemBytes(180_000) + StorageEstimate.MARGIN_BYTES,
            StorageEstimate.requiredFreeBytes(songs),
        )
    }

    @Test
    fun unknownStemModeFallsBackToOriginal() {
        assertEquals(StemMode.Original, StemMode.fromName(null))
        assertEquals(StemMode.Original, StemMode.fromName("Karaoke"))
        assertEquals(StemMode.Vocals, StemMode.fromName("Vocals"))
    }
}
