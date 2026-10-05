package com.autoomstudio.mplay.separation.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoomstudio.mplay.R
import com.autoomstudio.mplay.separation.SeparationLinks
import kotlin.math.roundToInt

internal class SeparationNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.separation_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.separation_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun progress(jobId: Long, title: String, fraction: Float?, remainingMs: Long?, waiting: Int): Notification {
        val text = when {
            fraction == null -> context.getString(R.string.separation_notification_preparing)
            remainingMs == null -> context.getString(R.string.separation_notification_percent, percent(fraction))
            else -> context.getString(
                R.string.separation_notification_percent_eta,
                percent(fraction),
                formatRemaining(remainingMs),
            )
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_separation)
            .setContentTitle(context.getString(R.string.separation_notification_title, title))
            .setContentText(text)
            .apply {
                if (waiting > 0) {
                    setSubText(
                        context.resources.getQuantityString(R.plurals.separation_notification_queued, waiting, waiting),
                    )
                }
            }
            .setProgress(PROGRESS_MAX, fraction?.let { (it * PROGRESS_MAX).roundToInt() } ?: 0, fraction == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openQueue())
            .addAction(0, context.getString(R.string.separation_notification_cancel), cancel(jobId))
            .build()
    }

    /** Android 12+ refused to start the foreground service from the background. */
    fun showNeedsApp() = notify(
        RESULT_ID,
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_separation)
            .setContentTitle(context.getString(R.string.separation_needs_app_title))
            .setContentText(context.getString(R.string.separation_needs_app_message))
            .setContentIntent(openQueue())
            .setAutoCancel(true)
            .build(),
    )

    fun showFinished(done: Int, failed: Int) {
        if (done == 0 && failed == 0) return
        val res = context.resources
        val text = listOfNotNull(
            res.getQuantityString(R.plurals.separation_finished_message, done, done).takeIf { done > 0 },
            res.getQuantityString(R.plurals.separation_failed_message, failed, failed).takeIf { failed > 0 },
        ).joinToString("\n")
        notify(
            RESULT_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_separation)
                .setContentTitle(context.getString(R.string.separation_finished_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openQueue())
                .setAutoCancel(true)
                .build(),
        )
    }

    fun cancelResult() = manager.cancel(RESULT_ID)

    private fun notify(id: Int, notification: Notification) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the call.
        }
    }

    private fun openQueue(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        SeparationLinks.openQueueIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancel(jobId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        jobId.toInt(),
        Intent(context, CancelSeparationReceiver::class.java).putExtra(CancelSeparationReceiver.EXTRA_JOB_ID, jobId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun percent(fraction: Float) = (fraction * 100).roundToInt().coerceIn(0, 100)

    private fun formatRemaining(ms: Long): String {
        val minutes = ((ms + 30_000) / 60_000).toInt()
        return if (minutes < 1) {
            context.getString(R.string.duration_under_minute)
        } else {
            context.getString(R.string.duration_minutes_short, minutes)
        }
    }

    companion object {
        const val CHANNEL_ID = "separation"
        const val PROGRESS_ID = 4101
        private const val RESULT_ID = 4102
        private const val PROGRESS_MAX = 1000
    }
}
