package com.autoomstudio.mp3studio.separation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.autoomstudio.mp3studio.BuildConfig
import com.autoomstudio.mp3studio.data.settings.AppSettings
import com.autoomstudio.mp3studio.separation.worker.SeparationWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import java.util.concurrent.TimeUnit

class WorkManagerSeparationBackend(
    private val context: Context,
    private val settings: AppSettings,
) : SeparationBackend {

    override val deviceReasons: List<UnsupportedReason> by lazy {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val specs = DeviceSpecs(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamBytes = memory.totalMem,
            isLowRamDevice = activityManager.isLowRamDevice,
            freeStorageBytes = context.filesDir.usableSpace,
        )
        DeviceEligibility.check(specs, BuildConfig.SEPARATION_ABIS.split(',').toSet())
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

    override suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(SeparationWorker.UNIQUE_NAME).result.await()
    }

    private companion object {
        const val BACKOFF_MINUTES = 5L
    }
}
