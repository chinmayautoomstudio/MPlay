package com.autoomstudio.mplay.separation.worker

import com.autoomstudio.mplay.separation.pipeline.HeatLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatLevelsTest {

    private val none = 0
    private val light = 1
    private val moderate = 2
    private val severe = 3
    private val critical = 4

    /** What the emulator publishes; real phones use their own scale. */
    private val published = HeadroomThresholds(warm = 0.933f, hot = 0.967f)
    private val unknown = HeadroomThresholds.Unknown

    @Test
    fun statusAloneSetsTheLevel() {
        assertEquals(HeatLevel.Cool, HeatLevels.from(none, Float.NaN, unknown))
        assertEquals(HeatLevel.Warm, HeatLevels.from(light, Float.NaN, unknown))
        assertEquals(HeatLevel.Hot, HeatLevels.from(moderate, Float.NaN, unknown))
        assertEquals(HeatLevel.Critical, HeatLevels.from(severe, Float.NaN, unknown))
        assertEquals(HeatLevel.Critical, HeatLevels.from(critical, Float.NaN, unknown))
    }

    @Test
    fun forecastWarnsBeforeTheStatusChanges() {
        assertEquals(HeatLevel.Cool, HeatLevels.from(none, 0.91f, published))
        assertEquals(HeatLevel.Warm, HeatLevels.from(none, 0.94f, published))
        assertEquals(HeatLevel.Hot, HeatLevels.from(none, 0.97f, published))
        assertEquals(HeatLevel.Hot, HeatLevels.from(light, 1.2f, published))
    }

    @Test
    fun headroomIsIgnoredWithoutThePhonesThresholds() {
        assertEquals(HeatLevel.Cool, HeatLevels.from(none, 0.99f, unknown))
    }

    @Test
    fun forecastNeverLowersTheStatus() {
        assertEquals(HeatLevel.Hot, HeatLevels.from(moderate, 0.1f, published))
        assertEquals(HeatLevel.Critical, HeatLevels.from(severe, 0.1f, published))
    }

    @Test
    fun batteryTemperatureBeforeAndroid10() {
        assertEquals(HeatLevel.Cool, HeatLevels.fromBatteryTemperature(399))
        assertEquals(HeatLevel.Warm, HeatLevels.fromBatteryTemperature(400))
        assertEquals(HeatLevel.Critical, HeatLevels.fromBatteryTemperature(450))
    }
}
