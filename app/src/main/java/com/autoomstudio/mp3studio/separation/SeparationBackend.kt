package com.autoomstudio.mp3studio.separation

/** Runs queued separations in the background. Everything else (cache, playback modes, settings) lives in shared code. */
interface SeparationBackend {
    /** What keeps this phone from separating, whatever the model; read once. Empty when the phone qualifies. */
    val deviceReasons: List<UnsupportedReason>

    /**
     * Makes sure queued jobs get processed with the current charging and battery settings.
     * Safe to call at any time, including while a job is running.
     */
    suspend fun schedule()

    /** Stops the running job (it goes back to the queue) and drops waiting work, for example on sign-out. */
    suspend fun cancel()
}
