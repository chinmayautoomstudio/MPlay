package com.autoomstudio.mplay.di

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.autoomstudio.mplay.data.account.AuthState
import com.autoomstudio.mplay.widget.MPlayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-wide reactions to signing in and out that don't belong to a screen. [com.autoomstudio.mplay.playback.PlaybackService]
 * stops itself on sign-out; everything else that can run in the background is stopped or resumed here.
 */
class AuthEffects(private val context: Context, private val container: AppContainer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Call once, in the main process. */
    fun start() {
        scope.launch {
            var signedInBefore = false
            container.authRepository.state
                .filterNot { it is AuthState.Loading }
                .map { (it as? AuthState.SignedIn)?.user?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId != null) onSignedIn() else if (signedInBefore) onSignedOut()
                    signedInBefore = userId != null
                    try {
                        MPlayWidget().updateAll(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not refresh the widget", e)
                    }
                }
        }
    }

    private suspend fun onSignedIn() {
        container.separationController.onSignedIn()
    }

    private suspend fun onSignedOut() {
        container.singAlongSession.cancel()
        container.metronomeController.stop()
        container.profileRepository.clear()
        container.separationController.onSignedOut()
    }

    private companion object {
        const val TAG = "AuthEffects"
    }
}
