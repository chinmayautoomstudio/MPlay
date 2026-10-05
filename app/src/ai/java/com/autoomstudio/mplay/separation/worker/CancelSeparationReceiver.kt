package com.autoomstudio.mplay.separation.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoomstudio.mplay.MPlayApp
import kotlinx.coroutines.launch

/** The notification's Cancel button. The worker notices the state change and stops the song. */
class CancelSeparationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val jobId = intent.getLongExtra(EXTRA_JOB_ID, -1)
        if (jobId < 0) return
        val container = (context.applicationContext as MPlayApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.stemRepository.cancel(jobId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_JOB_ID = "job_id"
    }
}
