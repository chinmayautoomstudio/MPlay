package com.autoomstudio.mp3studio.data.admin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.autoomstudio.mp3studio.MainActivity
import com.autoomstudio.mp3studio.R

/** Phone notifications for new Admin activity (sign-ups, subscriptions, deleted accounts). */
class AdminNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.admin_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.admin_channel_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** One notification per event, oldest first, or a single summary when there are more than [MAX_SINGLE]. */
    fun show(events: List<AdminActivity>) {
        if (events.isEmpty()) return
        ensureChannel()
        if (events.size > MAX_SINGLE) {
            val text = context.resources.getQuantityString(R.plurals.admin_notification_summary, events.size, events.size)
            notify(SUMMARY_ID, builder().setContentTitle(text).setNumber(events.size).build())
            return
        }
        events.forEach { event ->
            notify(
                notificationId(event),
                builder()
                    .setContentTitle(title(event))
                    .setContentText(detail(event))
                    .setWhen(eventMillisOrNull(event) ?: System.currentTimeMillis())
                    .setShowWhen(true)
                    .build(),
            )
        }
    }

    private fun builder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_admin)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setGroup(GROUP)
            .setContentIntent(openActivity())
            .setAutoCancel(true)

    private fun title(event: AdminActivity): String = when (event.type) {
        "subscribed" -> context.getString(
            R.string.admin_activity_subscribed,
            planName(event.plan ?: "pro"),
            providerName(event.provider.orEmpty()),
        )
        "deleted" -> context.getString(R.string.admin_activity_deleted, planName(event.plan ?: "free"))
        else -> context.getString(R.string.admin_activity_signup)
    }

    private fun detail(event: AdminActivity): String =
        if (event.type == "deleted") {
            context.getString(R.string.admin_activity_deleted_detail)
        } else {
            event.email ?: event.name.orEmpty()
        }

    private fun planName(plan: String): String = context.getString(
        when (plan) {
            "pro" -> R.string.plan_pro
            "trial" -> R.string.plan_trial
            else -> R.string.plan_free
        },
    )

    private fun providerName(provider: String): String = when (provider) {
        "admin" -> context.getString(R.string.admin_provider_admin)
        "payu" -> context.getString(R.string.admin_provider_payu)
        else -> provider
    }

    private fun notify(id: Int, notification: Notification) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS was revoked between the check and the call.
        }
    }

    private fun openActivity(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CODE,
        openActivityIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notificationId(event: AdminActivity): Int =
        BASE_ID + ("${event.type}|${event.at}|${event.userId}".hashCode() and 0x3FF)

    companion object {
        const val CHANNEL_ID = "admin_activity"
        const val ACTION_OPEN_ADMIN_ACTIVITY = "com.autoomstudio.mp3studio.action.OPEN_ADMIN_ACTIVITY"
        const val MAX_SINGLE = 5
        private const val GROUP = "admin_activity"
        private const val SUMMARY_ID = 4401

        /** Per-event IDs are 5000-6023, clear of the service and separation notification IDs (41xx-44xx). */
        private const val BASE_ID = 5000
        private const val REQUEST_CODE = 4401

        fun openActivityIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_ADMIN_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
