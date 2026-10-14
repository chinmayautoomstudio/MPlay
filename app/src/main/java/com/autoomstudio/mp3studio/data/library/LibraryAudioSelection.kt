package com.autoomstudio.mp3studio.data.library

/**
 * Builds the MediaStore type-eligibility clause for library songs and exclude-folder listing.
 * Music rows, voice recordings (API 31+), and anything under the public `Recordings/` tree.
 */
object LibraryAudioSelection {

    /** Public Recordings tree (call / Voice Recorder files), trailing slash. */
    const val RECORDINGS_PREFIX = "Recordings/"

    fun recordingsLikePattern(scoped: Boolean): String =
        SavedFolders.likePatterns(paths = listOf(RECORDINGS_PREFIX), scoped = scoped).single()

    /**
     * SQL fragment and bind args for rows that count as library audio.
     * [pathColumn] is `RELATIVE_PATH` (scoped) or `DATA` (legacy).
     */
    fun typeEligibilitySql(
        pathColumn: String,
        scoped: Boolean,
        includeIsRecording: Boolean,
        isMusicColumn: String,
        isRecordingColumn: String,
    ): Pair<String, List<String>> {
        val clauses = mutableListOf(
            "$isMusicColumn != 0",
            "$isMusicColumn IS NULL",
        )
        if (includeIsRecording) {
            clauses += "$isRecordingColumn != 0"
        }
        clauses += "$pathColumn LIKE ?"
        return "(${clauses.joinToString(" OR ")})" to listOf(recordingsLikePattern(scoped))
    }
}
