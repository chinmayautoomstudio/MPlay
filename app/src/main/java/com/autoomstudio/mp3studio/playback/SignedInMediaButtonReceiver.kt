package com.autoomstudio.mp3studio.playback

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.autoomstudio.mp3studio.MPlayApp
import com.autoomstudio.mp3studio.data.account.AuthState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Headset and widget play buttons while the app isn't running. Signed out (PRD AU4) the service isn't started:
 * once started in the foreground it must start playing, which [PlaybackService] refuses to do.
 */
@OptIn(UnstableApi::class)
class SignedInMediaButtonReceiver : MediaButtonReceiver() {

    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean {
        val auth = (context.applicationContext as MPlayApp).container.authRepository
        // In a fresh process the saved session is still being read; that takes well under the receiver's limit.
        val state = runBlocking { withTimeoutOrNull(SESSION_LOAD_TIMEOUT_MS) { auth.awaitReady() } }
        return state is AuthState.SignedIn
    }

    private companion object {
        const val SESSION_LOAD_TIMEOUT_MS = 3_000L
    }
}
