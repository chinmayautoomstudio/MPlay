package com.autoomstudio.mp3studio.separation.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.FloatBuffer

class AdaptiveModelTest {

    private val buffer = FloatBuffer.allocate(1)
    private var now = 0L
    private var heat = HeatLevel.Cool
    private var cancelled = false
    private val events = mutableListOf<String>()
    private val cooling = mutableListOf<Boolean>()

    private inner class FakeModel(val threads: Int) : SeparationModel {
        override fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer) {
            events += "run $threads"
        }

        override fun close() {
            events += "close $threads"
        }
    }

    private val model = AdaptiveModel(
        create = { threads -> events += "create $threads"; FakeModel(threads) },
        level = { heat },
        isCancelled = { cancelled },
        onCooling = { cooling += it },
        clock = { now },
        sleep = { now += it },
    )

    private fun runSegment() = model.run(buffer, buffer, buffer, buffer)

    @Test
    fun startsAtFullSpeedWhenCool() {
        runSegment()
        assertEquals(listOf("create 4", "run 4"), events)
    }

    @Test
    fun startsOnFewerThreadsWhenAlreadyWarm() {
        heat = HeatLevel.Warm
        runSegment()
        assertEquals(AdaptiveModel.LOW_THREADS, model.currentThreads)
    }

    @Test
    fun stepsDownAtOnceAndClosesTheOldSessionFirst() {
        runSegment()
        heat = HeatLevel.Warm
        now += 1_000
        runSegment()
        assertEquals(listOf("create 4", "run 4", "close 4", "create 2", "run 2"), events)
    }

    @Test
    fun stepsUpOnlyAfterASustainedCoolSpell() {
        heat = HeatLevel.Warm
        runSegment()
        heat = HeatLevel.Cool
        now += 1_000
        runSegment()
        assertEquals(AdaptiveModel.LOW_THREADS, model.currentThreads)
        now += AdaptiveModel.STEP_UP_AFTER_MS - 1
        runSegment()
        assertEquals(AdaptiveModel.LOW_THREADS, model.currentThreads)
        now += 1
        runSegment()
        assertEquals(AdaptiveModel.HIGH_THREADS, model.currentThreads)
    }

    @Test
    fun aWarmSpellRestartsTheCoolTimer() {
        heat = HeatLevel.Warm
        runSegment()
        heat = HeatLevel.Cool
        runSegment()
        now += AdaptiveModel.STEP_UP_AFTER_MS / 2
        heat = HeatLevel.Warm
        runSegment()
        heat = HeatLevel.Cool
        runSegment()
        now += AdaptiveModel.STEP_UP_AFTER_MS / 2 + 1
        runSegment()
        assertEquals(AdaptiveModel.LOW_THREADS, model.currentThreads)
    }

    @Test
    fun waitsWhileHotThenResumesOnFewerThreads() {
        runSegment()
        heat = HeatLevel.Hot
        var checks = 0
        val cooling = AdaptiveModel(
            create = { FakeModel(it) },
            level = { if (heat == HeatLevel.Hot && ++checks > 3) HeatLevel.Warm else heat },
            isCancelled = { false },
            onCooling = { this.cooling += it },
            clock = { now },
            sleep = { now += it },
        )
        cooling.run(buffer, buffer, buffer, buffer)
        assertEquals(listOf(true, false), this.cooling)
        assertEquals(AdaptiveModel.LOW_THREADS, cooling.currentThreads)
        assertTrue(now >= 3 * AdaptiveModel.COOL_CHECK_MS)
        assertEquals(3 * AdaptiveModel.COOL_CHECK_MS, cooling.cooledMs)
    }

    @Test(expected = SeparationCancelledException::class)
    fun cancellingDuringTheWaitStopsTheSong() {
        heat = HeatLevel.Hot
        val waiting = AdaptiveModel(
            create = { FakeModel(it) },
            level = { heat },
            isCancelled = { cancelled },
            clock = { now },
            sleep = { now += it; if (now >= 2_000) cancelled = true },
        )
        waiting.run(buffer, buffer, buffer, buffer)
    }

    @Test
    fun coolingEndsEvenWhenCancelled() {
        heat = HeatLevel.Hot
        cancelled = true
        try {
            runSegment()
        } catch (_: SeparationCancelledException) {
        }
        assertEquals(listOf(true, false), cooling)
        assertFalse(events.any { it.startsWith("run") })
    }

    @Test
    fun closeReleasesTheSession() {
        runSegment()
        model.close()
        assertEquals("close 4", events.last())
        assertEquals(0, model.currentThreads)
    }
}
