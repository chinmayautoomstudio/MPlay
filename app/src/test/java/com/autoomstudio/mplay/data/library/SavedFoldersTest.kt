package com.autoomstudio.mplay.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SavedFoldersTest {

    private val paths = listOf("Music/MP3 Studio Clips/", "Music/MPlay Clips/")

    @Test
    fun scopedPatternsMatchRelativePathPrefix() {
        assertEquals(
            listOf("Music/MP3 Studio Clips/%", "Music/MPlay Clips/%"),
            SavedFolders.likePatterns(paths, scoped = true),
        )
    }

    @Test
    fun legacyPatternsMatchAnywhereInTheAbsolutePath() {
        assertEquals(
            listOf("%/Music/MP3 Studio Clips/%", "%/Music/MPlay Clips/%"),
            SavedFolders.likePatterns(paths, scoped = false),
        )
    }
}
