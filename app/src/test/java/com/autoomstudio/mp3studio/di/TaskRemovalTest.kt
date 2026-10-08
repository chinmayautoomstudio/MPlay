package com.autoomstudio.mp3studio.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TaskRemovalTest {

    private var singAlongStops = 0
    private var metronomeStops = 0
    private var separationPauses = 0
    private var separationResumes = 0

    private val taskRemoval = TaskRemoval(
        stopSingAlong = { singAlongStops++ },
        stopMetronome = { metronomeStops++ },
        pauseSeparation = { separationPauses++ },
        resumeSeparation = { separationResumes++ },
        scope = CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test
    fun removalStopsEverythingOnceEvenWhenReportedByEveryHook() {
        repeat(4) { taskRemoval.onTaskRemoved() }

        assertEquals(1, singAlongStops)
        assertEquals(1, metronomeStops)
        assertEquals(1, separationPauses)
        assertEquals(0, separationResumes)
    }

    @Test
    fun openingWithoutARemovalResumesNothing() {
        taskRemoval.onAppOpened()

        assertEquals(0, separationResumes)
    }

    @Test
    fun openingAfterARemovalResumesSeparationOnce() {
        taskRemoval.onTaskRemoved()
        taskRemoval.onAppOpened()
        taskRemoval.onAppOpened()

        assertEquals(1, separationResumes)
    }

    @Test
    fun aLaterRemovalStopsEverythingAgain() {
        taskRemoval.onTaskRemoved()
        taskRemoval.onAppOpened()
        taskRemoval.onTaskRemoved()

        assertEquals(2, metronomeStops)
        assertEquals(2, separationPauses)
    }
}
