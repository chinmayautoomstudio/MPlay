package com.autoomstudio.mp3studio.separation.worker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.autoomstudio.mp3studio.MPlayApp

/**
 * Started for the length of a [SeparationWorker] run, only so that swiping the app away from recents reaches
 * [com.autoomstudio.mp3studio.di.TaskRemoval]. Android reports a removed task only to started services, and
 * WorkManager's foreground service isn't ours to hook. Back never calls [onTaskRemoved], so it doesn't pause anything.
 */
class SeparationTaskWatcher : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        (application as MPlayApp).container.taskRemoval.onTaskRemoved()
        stopSelf()
    }

    companion object {
        private const val TAG = "SeparationTaskWatcher"

        /** Call once the worker is in the foreground; before that a background start may be refused. */
        fun start(context: Context) {
            try {
                context.startService(Intent(context, SeparationTaskWatcher::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not start", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SeparationTaskWatcher::class.java))
        }
    }
}
