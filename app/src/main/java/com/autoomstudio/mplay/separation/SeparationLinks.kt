package com.autoomstudio.mplay.separation

import android.content.Context
import android.content.Intent
import com.autoomstudio.mplay.MainActivity

/** Opens the app on the vocal separation screen, for example from the progress notification. */
object SeparationLinks {
    const val ACTION_OPEN_QUEUE = "com.autoomstudio.mplay.action.OPEN_SEPARATION"

    fun openQueueIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_QUEUE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}
