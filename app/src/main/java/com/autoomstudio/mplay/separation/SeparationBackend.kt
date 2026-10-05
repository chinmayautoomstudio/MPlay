package com.autoomstudio.mplay.separation

/**
 * The edition-specific half of vocal separation. Standard MPlay ships a stub; MPlay AI runs the model.
 * Everything else (cache, playback modes, settings) lives in the shared code so both editions read the same data.
 */
interface SeparationBackend {
    /** Device limits are read once; the model check runs on every call so an imported model is picked up. */
    fun checkAvailability(): SeparationAvailability

    /**
     * Makes sure queued jobs get processed with the current charging and battery settings.
     * Safe to call at any time, including while a job is running.
     */
    suspend fun schedule()
}
