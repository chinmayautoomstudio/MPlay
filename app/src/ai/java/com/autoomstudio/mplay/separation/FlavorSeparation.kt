package com.autoomstudio.mplay.separation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.autoomstudio.mplay.data.settings.AppSettings
import com.autoomstudio.mplay.separation.android.ModelSource
import com.autoomstudio.mplay.separation.worker.SeparationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit

object FlavorSeparation {
    fun createBackend(context: Context, settings: AppSettings): SeparationBackend =
        AiSeparationBackend(context.applicationContext, settings)
}

private class AiSeparationBackend(
    private val context: Context,
    private val settings: AppSettings,
) : SeparationBackend {

    private val deviceReasons: List<UnsupportedReason> by lazy {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val specs = DeviceSpecs(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamBytes = memory.totalMem,
            isLowRamDevice = activityManager.isLowRamDevice,
            freeStorageBytes = context.filesDir.usableSpace,
        )
        DeviceEligibility.check(specs)
    }

    override fun checkAvailability(): SeparationAvailability {
        val reasons = deviceReasons.toMutableList()
        if (ModelSource.find(context) == null) reasons += UnsupportedReason.ModelMissing
        return if (reasons.isEmpty()) SeparationAvailability.Available else SeparationAvailability.Unavailable(reasons)
    }

    /**
     * A running worker picks up newly queued songs itself, but it may be about to finish, so new work is appended
     * behind it. Waiting work is replaced, which also applies changed charging and battery settings.
     */
    override suspend fun schedule() {
        val current = settings.separationSettings.first()
        val request = OneTimeWorkRequestBuilder<SeparationWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresCharging(current.chargingOnly)
                    .setRequiresBatteryNotLow(current.pauseOnLowBattery)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        val workManager = WorkManager.getInstance(context)
        val running = workManager.getWorkInfosForUniqueWork(SeparationWorker.UNIQUE_NAME).await()
            .any { it.state == WorkInfo.State.RUNNING }
        val policy = if (running) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(SeparationWorker.UNIQUE_NAME, policy, request)
    }

    private companion object {
        const val BACKOFF_MINUTES = 5L
    }
}
