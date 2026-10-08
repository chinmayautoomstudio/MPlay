package com.autoomstudio.mp3studio.spike

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File

/** Readings the spike records before, during and after a run. */
class DeviceProbe(private val context: Context) {

    /** Peak and current resident memory of this process in MB, native allocations included. */
    fun memoryMb(): Pair<Long, Long> {
        var peak = 0L
        var current = 0L
        File("/proc/self/status").forEachLine { line ->
            when {
                line.startsWith("VmHWM:") -> peak = kb(line) / 1024
                line.startsWith("VmRSS:") -> current = kb(line) / 1024
            }
        }
        return peak to current
    }

    fun batteryPercent(): Int =
        context.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    fun batteryTempC(): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    }

    /** PowerManager thermal status (0 none .. 6 shutdown), or -1 before Android 10. */
    fun thermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(PowerManager::class.java).currentThermalStatus
        } else {
            -1
        }

    fun description(): String {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        return "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, " +
            "${info.totalMem / (1024 * 1024)} MB RAM, ${Runtime.getRuntime().availableProcessors()} cores, " +
            "SoC ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else Build.HARDWARE}"
    }

    private fun kb(line: String): Long = line.filter(Char::isDigit).toLongOrNull() ?: 0L
}
