package com.autoomstudio.mp3studio.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryAudioSelectionTest {

    @Test
    fun recordingsLikePatternScopedAndLegacy() {
        assertEquals("Recordings/%", LibraryAudioSelection.recordingsLikePattern(scoped = true))
        assertEquals("%/Recordings/%", LibraryAudioSelection.recordingsLikePattern(scoped = false))
    }

    @Test
    fun typeEligibilityIncludesMusicNullAndRecordingsPath() {
        val (sql, args) = LibraryAudioSelection.typeEligibilitySql(
            pathColumn = "relative_path",
            scoped = true,
            includeIsRecording = false,
            isMusicColumn = "is_music",
            isRecordingColumn = "is_recording",
        )
        assertEquals(
            "(is_music != 0 OR is_music IS NULL OR relative_path LIKE ?)",
            sql,
        )
        assertEquals(listOf("Recordings/%"), args)
        assertFalse(sql.contains("is_recording"))
    }

    @Test
    fun typeEligibilityIncludesIsRecordingWhenRequested() {
        val (sql, args) = LibraryAudioSelection.typeEligibilitySql(
            pathColumn = "_data",
            scoped = false,
            includeIsRecording = true,
            isMusicColumn = "is_music",
            isRecordingColumn = "is_recording",
        )
        assertEquals(
            "(is_music != 0 OR is_music IS NULL OR is_recording != 0 OR _data LIKE ?)",
            sql,
        )
        assertEquals(listOf("%/Recordings/%"), args)
        assertTrue(sql.contains("is_recording != 0"))
    }
}
