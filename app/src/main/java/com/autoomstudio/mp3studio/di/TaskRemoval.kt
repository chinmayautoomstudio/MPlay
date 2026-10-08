package com.autoomstudio.mp3studio.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Swiping MP3 Studio away from recents ends everything it does in the background: the metronome stops, a
 * sing-along take in progress is discarded and separation pauses until the app is opened again. Music is stopped
 * by [com.autoomstudio.mp3studio.playback.PlaybackService] itself.
 */
class TaskRemoval(
    private val stopSingAlong: () -> Unit,
    private val stopMetronome: () -> Unit,
    private val pauseSeparation: suspend () -> Unit,
    private val resumeSeparation: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private var removed = false

    /** Main thread. Several hooks may report the same swipe; only the first one acts. */
    fun onTaskRemoved() {
        if (removed) return
        removed = true
        stopSingAlong()
        stopMetronome()
        scope.launch { pauseSeparation() }
    }

    /** Main thread. The process outlived the removal, so the sign-in that normally resumes the queue won't run. */
    fun onAppOpened() {
        if (!removed) return
        removed = false
        scope.launch { resumeSeparation() }
    }
}
