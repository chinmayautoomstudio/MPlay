package com.autoomstudio.mplay.separation

/**
 * The edition-specific half of vocal separation. Standard MPlay ships a stub; MPlay AI runs the model.
 * Everything else (cache, playback modes, settings) lives in the shared code so both editions read the same data.
 */
interface SeparationBackend {
    /** Computed once; cheap to call repeatedly. */
    val availability: SeparationAvailability

    /**
     * Makes sure queued jobs get processed with the current charging and battery settings.
     * Safe to call at any time, including while a job is running.
     */
    suspend fun schedule()
}
