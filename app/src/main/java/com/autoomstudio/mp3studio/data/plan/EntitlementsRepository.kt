package com.autoomstudio.mp3studio.data.plan

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The signed-in user's plan as the server last reported it, cached on the phone. Feature gates ask [canUse]; screens
 * show [entitlements] through [EntitlementPolicy].
 */
class EntitlementsRepository(
    private val backend: EntitlementsBackend,
    private val cache: EntitlementsCache,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onRefreshFailed: (Exception) -> Unit = {},
) {
    private val _entitlements = MutableStateFlow<Entitlements?>(null)
    val entitlements: StateFlow<Entitlements?> = _entitlements.asStateFlow()

    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                if (_entitlements.value == null) _entitlements.value = cache.read()
            } finally {
                loaded.complete(Unit)
            }
        }
    }

    fun now(): Long = clock()

    /** Fetches from the server for [userId]. Offline or failing calls keep the cached answer; returns success. */
    suspend fun refresh(userId: String): Boolean {
        loaded.await()
        return try {
            val fresh = backend.fetch().toEntitlements(userId, clock())
            _entitlements.value = fresh
            cache.write(fresh)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onRefreshFailed(e)
            false
        }
    }

    /** Whether [feature] is unlocked for [userId] right now; a cache for another account counts as none. */
    suspend fun canUse(feature: Feature, userId: String?): Boolean {
        loaded.await()
        val current = _entitlements.value?.takeIf { it.userId == userId }
        return EntitlementPolicy.canUse(feature, current, clock())
    }

    /** Takes the usage a reserve or finish answer carried, so the count on screen follows without a full refresh. */
    suspend fun updateUsage(userId: String, usage: SeparatorUsage) {
        loaded.await()
        val current = _entitlements.value?.takeIf { it.userId == userId } ?: return
        val updated = current.copy(usage = usage)
        _entitlements.value = updated
        cache.write(updated)
    }

    /** On sign-out (PRD AU6). */
    suspend fun clear() {
        loaded.await()
        _entitlements.value = null
        cache.write(null)
    }
}
