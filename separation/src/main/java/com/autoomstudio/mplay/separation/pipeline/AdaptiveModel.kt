package com.autoomstudio.mplay.separation.pipeline

import java.nio.FloatBuffer

/**
 * Runs the model on [HIGH_THREADS] while the phone is cool and [LOW_THREADS] once it warms, and waits before a
 * segment while it is hot. The thread count is fixed per ONNX session, so a change reloads the session; stepping
 * down happens at once, stepping back up only after a sustained cool spell, so reloads stay rare.
 */
class AdaptiveModel(
    private val create: (threads: Int) -> SeparationModel,
    private val level: () -> HeatLevel,
    private val isCancelled: () -> Boolean,
    private val onCooling: (Boolean) -> Unit = {},
    private val onSwitch: (threads: Int, heat: HeatLevel) -> Unit = { _, _ -> },
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: (Long) -> Unit = Thread::sleep,
) : SeparationModel {

    private var model: SeparationModel? = null
    private var threads = 0
    private var switchedAt = 0L
    private var coolSince: Long? = null

    /** Threads of the current session; 0 before the first segment. */
    val currentThreads: Int get() = threads

    /** Total time spent cooling down, for time-left estimates that shouldn't count the waits as work. */
    @Volatile
    var cooledMs: Long = 0L
        private set

    override fun run(mix: FloatBuffer, mag: FloatBuffer, spec: FloatBuffer, wave: FloatBuffer) {
        val heat = awaitBelowHot()
        val now = clock()
        val target = targetThreads(heat, now)
        val current = model
        val active = if (current != null && target == threads) {
            current
        } else {
            // Closing first keeps the native peak at one session; the weights are mapped, not copied.
            current?.close()
            model = null
            create(target).also {
                model = it
                threads = target
                switchedAt = now
                if (current != null) onSwitch(target, heat)
            }
        }
        active.run(mix, mag, spec, wave)
    }

    private fun awaitBelowHot(): HeatLevel {
        var heat = level()
        if (heat < HeatLevel.Hot) return heat
        onCooling(true)
        val started = clock()
        try {
            while (heat >= HeatLevel.Hot) {
                var waited = 0L
                while (waited < COOL_CHECK_MS) {
                    if (isCancelled()) throw SeparationCancelledException()
                    sleep(CANCEL_POLL_MS)
                    waited += CANCEL_POLL_MS
                }
                heat = level()
            }
        } finally {
            cooledMs += clock() - started
            onCooling(false)
        }
        coolSince = null
        return heat
    }

    private fun targetThreads(heat: HeatLevel, now: Long): Int {
        if (heat != HeatLevel.Cool) {
            coolSince = null
            return LOW_THREADS
        }
        val since = coolSince ?: now.also { coolSince = it }
        return when {
            threads == 0 || threads == HIGH_THREADS -> HIGH_THREADS
            now - since >= STEP_UP_AFTER_MS && now - switchedAt >= MIN_SWITCH_GAP_MS -> HIGH_THREADS
            else -> LOW_THREADS
        }
    }

    override fun close() {
        model?.close()
        model = null
        threads = 0
    }

    companion object {
        const val HIGH_THREADS = 4
        const val LOW_THREADS = 2
        const val STEP_UP_AFTER_MS = 120_000L
        const val MIN_SWITCH_GAP_MS = 60_000L
        const val COOL_CHECK_MS = 5_000L
        const val CANCEL_POLL_MS = 500L
    }
}
