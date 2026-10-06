package com.autoomstudio.mplay.singalong

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.autoomstudio.mplay.MPlayApp
import com.autoomstudio.mplay.MainActivity
import com.autoomstudio.mplay.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps a sing-along recording going with the screen locked, with a Stop action in its notification (SA10). */
class RecordingService : Service() {
    private val scope = MainScope()
    private var watch: Job? = null
    private val session get() = (application as MPlayApp).container.singAlongSession

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            session.stop()
            return START_NOT_STICKY
        }
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(session.state.value), type)
        } catch (e: Exception) {
            Log.w(TAG, "Could not go foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        watch?.cancel()
        watch = scope.launch {
            session.state.collect { state ->
                if (!session.inProgress) {
                    stopSelf()
                } else if (NotificationManagerCompat.from(this@RecordingService).areNotificationsEnabled()) {
                    try {
                        NotificationManagerCompat.from(this@RecordingService).notify(NOTIFICATION_ID, notification(state))
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.singalong_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun notification(state: SingAlongState): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mic)
            .setContentIntent(open)
            .addAction(0, getString(R.string.singalong_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (state is SingAlongState.Recording) {
            builder.setContentTitle(getString(R.string.singalong_notification_recording))
                .setContentText(state.title)
                .setUsesChronometer(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() - (SystemClock.elapsedRealtime() - state.startedAtMs))
        } else {
            builder.setContentTitle(getString(R.string.singalong_notification_preparing))
        }
        return builder.build()
    }

    private companion object {
        const val TAG = "RecordingService"
        const val CHANNEL_ID = "singalong"
        const val NOTIFICATION_ID = 4301
        const val ACTION_STOP = "com.autoomstudio.mplay.singalong.STOP"
    }
}
