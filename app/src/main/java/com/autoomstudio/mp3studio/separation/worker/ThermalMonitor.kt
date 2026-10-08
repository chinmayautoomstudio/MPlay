package com.autoomstudio.mp3studio.separation.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.autoomstudio.mp3studio.separation.pipeline.HeatLevel

/** Reads how warm the phone is. One instance per process, since thermal headroom may only be polled once a second. */
internal class ThermalMonitor(context: Context, private val clock: () -> Long = SystemClock::elapsedRealtime) {

    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)
    private var headroomAt = Long.MIN_VALUE
    private var headroom = Float.NaN

    /**
     * Headroom at which this phone reports light and moderate throttling. Only Android 15+ publishes them; the
     * scale differs per phone, so without them the headroom is ignored and the status alone decides.
     */
    private val thresholds: HeadroomThresholds by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM || power == null) {
            HeadroomThresholds.Unknown
        } else {
            val map = try {
                power.thermalHeadroomThresholds
            } catch (_: RuntimeException) {
                emptyMap()
            }
            HeadroomThresholds(
                warm = map[PowerManager.THERMAL_STATUS_LIGHT] ?: Float.NaN,
                hot = map[PowerManager.THERMAL_STATUS_MODERATE] ?: Float.NaN,
            )
        }
    }

    @Synchronized
    fun level(): HeatLevel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || power == null) {
            val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?: return HeatLevel.Cool
            return HeatLevels.fromBatteryTemperature(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0))
        }
        val known = thresholds
        val forecast = if (known == HeadroomThresholds.Unknown) Float.NaN else headroom()
        return HeatLevels.from(power.currentThermalStatus, forecast, known)
    }

    private fun headroom(): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Float.NaN
        val now = clock()
        if (headroomAt == Long.MIN_VALUE || now - headroomAt >= HEADROOM_INTERVAL_MS) {
            headroom = power!!.getThermalHeadroom(FORECAST_SECONDS)
            headroomAt = now
        }
        return headroom
    }

    private companion object {
        const val HEADROOM_INTERVAL_MS = 1_000L
        const val FORECAST_SECONDS = 10
    }
}

/** NaN marks a threshold the phone doesn't publish. */
internal data class HeadroomThresholds(val warm: Float, val hot: Float) {
    companion object {
        val Unknown = HeadroomThresholds(Float.NaN, Float.NaN)
    }
}

internal object HeatLevels {
    const val WARM_BATTERY_TENTHS_C = 400
    const val CRITICAL_BATTERY_TENTHS_C = 450

    /**
     * [headroom] is the forecast in 10 seconds, so it warns before the status changes; NaN when unknown. NaN never
     * compares as reached, and the headroom never lowers what the status says.
     */
    fun from(status: Int, headroom: Float, thresholds: HeadroomThresholds): HeatLevel = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> HeatLevel.Critical
        status >= PowerManager.THERMAL_STATUS_MODERATE || headroom >= thresholds.hot -> HeatLevel.Hot
        status >= PowerManager.THERMAL_STATUS_LIGHT || headroom >= thresholds.warm -> HeatLevel.Warm
        else -> HeatLevel.Cool
    }

    /** Before Android 10 only the battery temperature is available, in tenths of a degree. */
    fun fromBatteryTemperature(tenthsC: Int): HeatLevel = when {
        tenthsC >= CRITICAL_BATTERY_TENTHS_C -> HeatLevel.Critical
        tenthsC >= WARM_BATTERY_TENTHS_C -> HeatLevel.Warm
        else -> HeatLevel.Cool
    }
}
