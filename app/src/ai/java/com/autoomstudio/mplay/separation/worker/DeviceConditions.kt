package com.autoomstudio.mplay.separation.worker

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.autoomstudio.mplay.data.settings.SeparationSettings
import com.autoomstudio.mplay.data.stems.PauseReason

/**
 * Live checks between and during songs. Charging and battery are deliberately at least as lenient as WorkManager's
 * own constraints, so a paused job rescheduled with those constraints never starts and pauses in a loop.
 */
internal object DeviceConditions {
    private const val LOW_BATTERY_PERCENT = 15
    private const val HOT_BATTERY_TENTHS_C = 450

    fun pauseReason(context: Context, settings: SeparationSettings): PauseReason? {
        if (isHot(context)) return PauseReason.Heat
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (settings.chargingOnly && !plugged) return PauseReason.Charging
        if (settings.pauseOnLowBattery && !plugged && percent(battery) in 0 until LOW_BATTERY_PERCENT) {
            return PauseReason.Battery
        }
        return null
    }

    private fun isHot(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(PowerManager::class.java) ?: return false
            return power.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        return battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) >= HOT_BATTERY_TENTHS_C
    }

    private fun percent(battery: Intent): Int {
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) -1 else level * 100 / scale
    }
}
