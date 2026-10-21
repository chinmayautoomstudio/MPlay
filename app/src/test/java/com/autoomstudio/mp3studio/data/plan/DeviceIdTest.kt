package com.autoomstudio.mp3studio.data.plan

import com.autoomstudio.mp3studio.data.account.DeviceDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdTest {

    @Test
    fun formatNameDropsARepeatedBrand() {
        assertEquals("Google Pixel 8", DeviceId.formatName("Google", "Pixel 8"))
        assertEquals("Google Pixel 8", DeviceId.formatName("Google", "Google Pixel 8"))
        assertEquals("samsung SM-S918B", DeviceId.formatName("samsung", "SM-S918B"))
    }

    @Test
    fun formatNameFallsBackWhenTheModelIsBlank() {
        assertEquals("Google", DeviceId.formatName("Google", "  "))
        assertEquals("Pixel 8", DeviceId.formatName(null, "Pixel 8"))
        assertEquals(DeviceId.FALLBACK_NAME, DeviceId.formatName("  ", null))
    }

    @Test
    fun formatNameCapsAtEightyCharacters() {
        val long = "Brand " + "X".repeat(100)
        assertEquals(DeviceId.MAX_NAME_LENGTH, DeviceId.formatName("Brand", "X".repeat(100)).length)
        assertTrue(DeviceId.formatName("Brand", "X".repeat(100)).startsWith("Brand "))
        assertEquals(DeviceId.MAX_NAME_LENGTH, DeviceId.formatName(long, null).length)
    }

    @Test
    fun deviceJsonMapsToLoggedInDevice() {
        val json = Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<DeviceDto>(
            """{"deviceId":"${"a".repeat(64)}","deviceName":"Pixel 8",
                "signedInAt":"2026-10-21T10:00:00+00:00","lastSeenAt":"2026-10-21T12:00:00+00:00","current":true}""",
        )
        val device = dto.toDevice()
        assertEquals("a".repeat(64), device.deviceId)
        assertEquals("Pixel 8", device.deviceName)
        assertEquals(epochMillis("2026-10-21T10:00:00+00:00"), device.signedInAt)
        assertEquals(epochMillis("2026-10-21T12:00:00+00:00"), device.lastSeenAt)
        assertTrue(device.isCurrent)
        assertFalse(json.decodeFromString<DeviceDto>(
            """{"deviceId":"${"b".repeat(64)}","deviceName":"Other",
                "signedInAt":"2026-10-21T10:00:00+00:00","lastSeenAt":"2026-10-21T12:00:00+00:00"}""",
        ).toDevice().isCurrent)
    }
}
