package com.autoomstudio.mp3studio.di

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.autoomstudio.mp3studio.data.account.AuthState
import com.autoomstudio.mp3studio.data.admin.AdminActivityWorker
import com.autoomstudio.mp3studio.data.usage.UsageSyncWorker
import com.autoomstudio.mp3studio.widget.MPlayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * App-wide reactions to signing in and out that don't belong to a screen. [com.autoomstudio.mp3studio.playback.PlaybackService]
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
                    if (userId != null) onSignedIn(userId) else if (signedInBefore) onSignedOut()
                    signedInBefore = userId != null
                    try {
                        MPlayWidget().updateAll(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not refresh the widget", e)
                    }
                }
        }
        scope.launch {
            combine(
                container.authRepository.state.filterNot { it is AuthState.Loading },
                container.entitlementsRepository.entitlements,
                container.appSettings.adminNotificationsEnabled,
            ) { auth, entitlements, enabled ->
                val userId = (auth as? AuthState.SignedIn)?.user?.id ?: return@combine false
                // Signed in but the cached plan isn't read yet: wait rather than cancel and re-enqueue.
                val current = entitlements ?: return@combine null
                enabled && current.userId == userId && current.isAdmin
            }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { notify ->
                    if (notify) AdminActivityWorker.schedule(context) else AdminActivityWorker.cancel(context)
                }
        }
    }

    private suspend fun onSignedIn(userId: String) {
        scope.launch { container.entitlementsRepository.refresh(userId) }
        UsageSyncWorker.schedule(context)
        container.separationController.onSignedIn()
    }

    private suspend fun onSignedOut() {
        container.singAlongSession.cancel()
        container.metronomeController.stop()
        container.profileRepository.clear()
        container.entitlementsRepository.clear()
        container.billingRepository.clear()
        container.separationController.onSignedOut()
        container.appSettings.clearAdminActivityNotifiedAt()
    }

    private companion object {
        const val TAG = "AuthEffects"
    }
}
