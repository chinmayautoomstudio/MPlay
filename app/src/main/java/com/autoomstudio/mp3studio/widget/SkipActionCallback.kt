package com.autoomstudio.mp3studio.widget

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.autoomstudio.mp3studio.playback.PlaybackService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

/**
 * Next and previous talk to the session directly: Media3's media button receiver only forwards
 * play commands on API 26+, and skipping is meaningless when nothing is queued anyway.
 */
class SkipActionCallback : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val forward = parameters[KEY_FORWARD] ?: true
        // Glance passes a receiver context, which may not bind to services.
        val appContext = context.applicationContext
        // MediaController is bound to the looper it was built on.
        withContext(Dispatchers.Main) {
            val future = MediaController.Builder(
                appContext,
                SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java)),
            ).buildAsync()
            try {
                val controller = future.await()
                if (controller.mediaItemCount > 0) {
                    if (forward) controller.seekToNext() else controller.seekToPrevious()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not reach PlaybackService", e)
            } finally {
                MediaController.releaseFuture(future)
            }
        }
    }

    companion object {
        val KEY_FORWARD = ActionParameters.Key<Boolean>("forward")
        private const val TAG = "SkipActionCallback"
    }
}
