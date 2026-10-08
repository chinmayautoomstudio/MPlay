package com.autoomstudio.mp3studio.separation.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.autoomstudio.mp3studio.data.settings.SeparationSettings
import com.autoomstudio.mp3studio.data.stems.PauseReason
import com.autoomstudio.mp3studio.separation.pipeline.HeatLevel

/**
 * Live checks between and during songs. Charging and battery are deliberately at least as lenient as WorkManager's
 * own constraints, so a paused job rescheduled with those constraints never starts and pauses in a loop.
 */
internal object DeviceConditions {
    private const val LOW_BATTERY_PERCENT = 15

    /**
     * [heatLimit] is [HeatLevel.Hot] before a song starts. During a song the separator cools down by itself at Hot,
     * so only [HeatLevel.Critical] should stop it.
     */
    fun pauseReason(
        context: Context,
        settings: SeparationSettings,
        thermal: ThermalMonitor,
        heatLimit: HeatLevel,
    ): PauseReason? {
        if (thermal.level() >= heatLimit) return PauseReason.Heat
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (settings.chargingOnly && !plugged) return PauseReason.Charging
        if (settings.pauseOnLowBattery && !plugged && percent(battery) in 0 until LOW_BATTERY_PERCENT) {
            return PauseReason.Battery
        }
        return null
    }

    private fun percent(battery: Intent): Int {
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }
}
