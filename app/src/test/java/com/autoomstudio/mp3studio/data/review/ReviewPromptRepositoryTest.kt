package com.autoomstudio.mp3studio.data.review

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptRepositoryTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var now = 1_700_000_000_000L

    @After
    fun tearDown() = scope.cancel()

    private class FakeSettings : ReviewPromptSettings {
        var useCount = 0
        var completed = false
        var dismissedAtMs = 0L
        var shownCount = 0

        override suspend fun reviewFeatureUseCount() = useCount
        override suspend fun setReviewFeatureUseCount(count: Int) {
            useCount = count
        }

        override suspend fun reviewPromptCompleted() = completed
        override suspend fun setReviewPromptCompleted() {
            completed = true
        }

        override suspend fun reviewPromptDismissedAtMs() = dismissedAtMs
        override suspend fun setReviewPromptDismissedAtMs(epochMs: Long) {
            dismissedAtMs = epochMs
        }

        override suspend fun incrementReviewSoftPromptShownCount() {
            shownCount += 1
        }
    }

    private fun repository(settings: FakeSettings = FakeSettings()) =
        ReviewPromptRepository(settings, scope, clock = { now }) to settings

    @Test
    fun featureUseShowsSoftPromptAtThreshold() = runTest(dispatcher) {
        val (repo, settings) = repository()
        repo.recordFeatureUse()
        repo.recordFeatureUse()
        assertFalse(repo.softPromptVisible.value)
        assertEquals(2, settings.useCount)

        repo.recordFeatureUse()
        assertTrue(repo.softPromptVisible.value)
        assertEquals(3, settings.useCount)
    }

    @Test
    fun dismissStartsTenDayCooldown() = runTest(dispatcher) {
        val (repo, settings) = repository()
        repeat(3) { repo.recordFeatureUse() }
        assertTrue(repo.softPromptVisible.value)

        repo.dismiss()
        assertFalse(repo.softPromptVisible.value)
        assertEquals(now, settings.dismissedAtMs)

        repo.recordFeatureUse()
        assertFalse(repo.softPromptVisible.value)

        now += ReviewPromptRepository.DISMISS_COOLDOWN_MS - 1
        repo.recordFeatureUse()
        assertFalse(repo.softPromptVisible.value)

        now += 1
        repo.recordFeatureUse()
        assertTrue(repo.softPromptVisible.value)
    }

    @Test
    fun completedNeverShowsAgain() = runTest(dispatcher) {
        val (repo, settings) = repository()
        repeat(3) { repo.recordFeatureUse() }
        repo.markCompleted()
        assertTrue(settings.completed)
        assertFalse(repo.softPromptVisible.value)

        now += ReviewPromptRepository.DISMISS_COOLDOWN_MS * 2
        repo.recordFeatureUse()
        repo.maybeShowForIdle()
        assertFalse(repo.softPromptVisible.value)
    }

    @Test
    fun idleShowsOncePerStretchWhenEligible() = runTest(dispatcher) {
        val (repo, _) = repository()
        repo.noteInteraction()
        now += ReviewPromptRepository.IDLE_MS - 1
        repo.maybeShowForIdle()
        assertFalse(repo.softPromptVisible.value)

        now += 1
        repo.maybeShowForIdle()
        assertTrue(repo.softPromptVisible.value)

        repo.clearSoftPrompt()
        repo.maybeShowForIdle()
        assertFalse(repo.softPromptVisible.value)

        repo.noteInteraction()
        now += ReviewPromptRepository.IDLE_MS
        repo.maybeShowForIdle()
        assertTrue(repo.softPromptVisible.value)
    }

    @Test
    fun idleDoesNotShowDuringCooldown() = runTest(dispatcher) {
        val (repo, _) = repository()
        repeat(3) { repo.recordFeatureUse() }
        repo.dismiss()

        now += ReviewPromptRepository.IDLE_MS
        repo.noteInteraction()
        now += ReviewPromptRepository.IDLE_MS
        repo.maybeShowForIdle()
        assertFalse(repo.softPromptVisible.value)
    }
}
