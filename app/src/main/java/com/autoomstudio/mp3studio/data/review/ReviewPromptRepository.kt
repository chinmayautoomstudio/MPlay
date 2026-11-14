package com.autoomstudio.mp3studio.data.review

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persistence used by [ReviewPromptRepository]; [com.autoomstudio.mp3studio.data.settings.AppSettings] implements this. */
interface ReviewPromptSettings {
    suspend fun reviewFeatureUseCount(): Int
    suspend fun setReviewFeatureUseCount(count: Int)
    suspend fun reviewPromptCompleted(): Boolean
    suspend fun setReviewPromptCompleted()
    suspend fun reviewPromptDismissedAtMs(): Long
    suspend fun setReviewPromptDismissedAtMs(epochMs: Long)
    suspend fun incrementReviewSoftPromptShownCount()
}

/**
 * Counts studio feature uses and idle time, and decides when to show the soft Play Store rating prompt.
 * UI observes [softPromptVisible]; hooks only call [recordFeatureUse] / [noteInteraction] / [maybeShowForIdle].
 */
class ReviewPromptRepository(
    private val settings: ReviewPromptSettings,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _softPromptVisible = MutableStateFlow(false)
    val softPromptVisible: StateFlow<Boolean> = _softPromptVisible.asStateFlow()

    @Volatile
    private var lastInteractionMs: Long = clock()

    /** True after an idle trigger has already asked once for the current idle stretch. */
    @Volatile
    private var idlePromptConsumed: Boolean = false

    fun noteInteraction() {
        lastInteractionMs = clock()
        idlePromptConsumed = false
    }

    /** Increments the feature-use counter; requests the soft prompt when the count reaches [FEATURE_USE_THRESHOLD]. */
    fun recordFeatureUse() {
        scope.launch {
            mutex.withLock {
                if (!isEligible(clock())) return@withLock
                val next = settings.reviewFeatureUseCount() + 1
                settings.setReviewFeatureUseCount(next)
                if (next >= FEATURE_USE_THRESHOLD) requestShowLocked()
            }
        }
    }

    /**
     * If the app has been idle for [IDLE_MS] since the last [noteInteraction], requests the soft prompt once
     * for this idle stretch (when eligible).
     */
    fun maybeShowForIdle() {
        scope.launch {
            mutex.withLock {
                val now = clock()
                if (idlePromptConsumed) return@withLock
                if (now - lastInteractionMs < IDLE_MS) return@withLock
                if (!isEligible(now)) return@withLock
                idlePromptConsumed = true
                requestShowLocked()
            }
        }
    }

    fun onSoftPromptShown() {
        scope.launch {
            mutex.withLock { settings.incrementReviewSoftPromptShownCount() }
        }
    }

    suspend fun dismiss() = mutex.withLock {
        settings.setReviewPromptDismissedAtMs(clock())
        _softPromptVisible.value = false
    }

    suspend fun markCompleted() = mutex.withLock {
        settings.setReviewPromptCompleted()
        _softPromptVisible.value = false
    }

    fun clearSoftPrompt() {
        _softPromptVisible.value = false
    }

    private suspend fun requestShowLocked() {
        if (!isEligible(clock())) return
        if (_softPromptVisible.value) return
        _softPromptVisible.value = true
    }

    private suspend fun isEligible(nowMs: Long): Boolean {
        if (settings.reviewPromptCompleted()) return false
        val dismissedAt = settings.reviewPromptDismissedAtMs()
        if (dismissedAt == 0L) return true
        return nowMs - dismissedAt >= DISMISS_COOLDOWN_MS
    }

    companion object {
        const val FEATURE_USE_THRESHOLD = 3
        val IDLE_MS: Long = TimeUnit.MINUTES.toMillis(10)
        val DISMISS_COOLDOWN_MS: Long = TimeUnit.DAYS.toMillis(10)
    }
}
