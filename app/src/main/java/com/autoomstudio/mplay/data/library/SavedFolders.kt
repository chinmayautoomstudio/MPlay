package com.autoomstudio.mplay.data.library

import com.autoomstudio.mplay.data.clip.ClipStore
import com.autoomstudio.mplay.singalong.RecordingStore

/** Folders the app saves short audio into; their files skip the library's minimum-length filter. */
object SavedFolders {

    val relativePaths: List<String>
        get() = listOf(
            ClipStore.CLIPS_RELATIVE_PATH,
            RecordingStore.RELATIVE_PATH,
            ClipStore.LEGACY_CLIPS_RELATIVE_PATH,
            RecordingStore.LEGACY_RELATIVE_PATH,
        )

    /**
     * SQL LIKE patterns for [relativePaths]: matched against `RELATIVE_PATH` on Android 10+ ([scoped]),
     * otherwise against the absolute `DATA` path.
     */
    fun likePatterns(paths: List<String> = relativePaths, scoped: Boolean): List<String> =
        paths.map { if (scoped) "$it%" else "%/$it%" }
}
