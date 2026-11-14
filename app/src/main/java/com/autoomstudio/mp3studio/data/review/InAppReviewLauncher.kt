package com.autoomstudio.mp3studio.data.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Tries Google Play's In-App Review flow (may show nothing under quota / on emulators), then always
 * opens the store listing for [PLAY_STORE_PACKAGE] so Rate has a visible destination.
 */
class InAppReviewLauncher {

    suspend fun launch(activity: Activity) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow().awaitTask()
            manager.launchReviewFlow(activity, request).awaitTask()
        } catch (e: Exception) {
            Log.w(TAG, "In-App Review unavailable", e)
        }
        openPlayStoreListing(activity)
    }

    private fun openPlayStoreListing(activity: Activity) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PLAY_STORE_PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val https = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activity.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(https)
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "No browser or Play Store to open the listing", e)
            }
        }
    }

    companion object {
        private const val TAG = "InAppReview"
        const val PLAY_STORE_PACKAGE = "com.autoomstudio.mp3studio"
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            @Suppress("UNCHECKED_CAST")
            cont.resume(task.result as T)
        } else {
            cont.resumeWithException(task.exception ?: RuntimeException("Play Task failed"))
        }
    }
}
