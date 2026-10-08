package com.autoomstudio.mp3studio.separation.pipeline

/** How warm the phone is, from Android's thermal status and headroom; ordered from coolest to hottest. */
enum class HeatLevel {
    /** Full speed. */
    Cool,

    /** Fewer threads, to stop the phone heating further. */
    Warm,

    /** Waits between segments until the phone is back to [Warm]; progress is kept. */
    Hot,

    /** The worker stops the song and retries later. */
    Critical,
}
