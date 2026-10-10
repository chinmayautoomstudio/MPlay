package com.autoomstudio.mp3studio.data.admin

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.plan.epochMillis
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Checks the Admin activity feed in the background and posts new events as phone notifications. Scheduled by
 * [com.autoomstudio.mp3studio.di.AuthEffects] only while the signed-in user is an admin with the switch on.
 */
class AdminActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MPlayApp).container
        val user = (container.authRepository.awaitReady() as? AuthState.SignedIn)?.user ?: return Result.success()
        val settings = container.appSettings
        if (!settings.adminNotificationsEnabled.first()) return Result.success()
        val events = try {
            container.adminBackend.activity()
        } catch (e: AdminException) {
            return when (e.error) {
                AdminError.Offline -> Result.retry()
                AdminError.Forbidden -> {
                    container.entitlementsRepository.refresh(user.id)
                    Result.success()
                }
                else -> Result.success()
            }
        }
        val check = checkNewActivity(
            events = events,
            notifiedAt = settings.adminActivityNotifiedAt(),
            seenAt = settings.adminActivitySeenAt.first(),
            now = System.currentTimeMillis(),
        )
        AdminNotifications(applicationContext).show(check.toNotify)
        settings.setAdminActivityNotifiedAt(check.notifiedAt)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "admin-activity"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AdminActivityWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

/** What one background check posts, and the event time to remember as notified. */
internal data class AdminActivityCheck(val toNotify: List<AdminActivity>, val notifiedAt: Long)

/**
 * Events newer than both the last notified and the last seen time, oldest first. The first check ([notifiedAt] 0)
 * only sets the baseline, so an admin isn't flooded with the backlog.
 */
internal fun checkNewActivity(events: List<AdminActivity>, notifiedAt: Long, seenAt: Long, now: Long): AdminActivityCheck {
    val newest = events.mapNotNull(::eventMillisOrNull).maxOrNull()
    if (notifiedAt == 0L) return AdminActivityCheck(emptyList(), maxOf(newest ?: now, seenAt))
    val after = maxOf(notifiedAt, seenAt)
    val fresh = events
        .mapNotNull { event -> eventMillisOrNull(event)?.takeIf { it > after }?.let { event to it } }
        .sortedBy { it.second }
        .map { it.first }
    return AdminActivityCheck(fresh, maxOf(notifiedAt, newest ?: 0L))
}

internal fun eventMillisOrNull(event: AdminActivity): Long? = runCatching { epochMillis(event.at) }.getOrNull()
