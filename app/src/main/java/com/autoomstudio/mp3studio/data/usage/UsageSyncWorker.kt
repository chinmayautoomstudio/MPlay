package com.autoomstudio.mp3studio.data.usage

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autoomstudio.mp3studio.MPlayApp
import java.util.concurrent.TimeUnit

/** Sends job outcomes to the server once there is a network, retrying until they arrive. */
class UsageSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MPlayApp).container
        container.authRepository.awaitReady()
        return if (container.usageReporter.sendPending()) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_NAME = "usage-sync"

        /** Appends behind a running sync, so a report made while it sends isn't missed. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
