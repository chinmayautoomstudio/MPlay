package com.autoomstudio.mplay.metronome

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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

/**
 * Keeps the metronome running with the screen off or the app in the background (MT15). It is separate from the
 * music playback service, whose foreground state Media3 ties to the music player.
 */
class MetronomeService : Service() {
    private val scope = MainScope()
    private var watch: Job? = null
    private val controller get() = (application as MPlayApp).container.metronomeController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            controller.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(controller.state.value.settings), type)
        watch?.cancel()
        watch = scope.launch {
            controller.state.collect { state ->
                if (!state.running) {
                    stopSelf()
                } else if (NotificationManagerCompat.from(this@MetronomeService).areNotificationsEnabled()) {
                    try {
                        NotificationManagerCompat.from(this@MetronomeService)
                            .notify(NOTIFICATION_ID, notification(state.settings))
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
            getString(R.string.metronome_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun notification(settings: MetronomeSettings): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_METRONOME, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_metronome)
            .setContentTitle(getString(R.string.metronome_notification_title))
            .setContentText(getString(R.string.metronome_notification_text, settings.bpm, settings.timeSignature.toString()))
            .setContentIntent(open)
            .addAction(0, getString(R.string.metronome_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "metronome"
        const val NOTIFICATION_ID = 4201
        const val ACTION_STOP = "com.autoomstudio.mplay.metronome.STOP"
    }
}
