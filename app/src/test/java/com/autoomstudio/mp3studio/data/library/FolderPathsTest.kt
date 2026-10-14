package com.autoomstudio.mp3studio.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderPathsTest {

    @Test
    fun normalizeAddsTrailingSlashAndForwardSlashes() {
        assertEquals("Music/Foo/", FolderPaths.normalize("Music\\Foo"))
        assertEquals("Music/Foo/", FolderPaths.normalize("/Music/Foo/"))
        assertEquals("", FolderPaths.normalize("///"))
    }

    @Test
    fun fromAbsoluteDataPathStripsEmulatedStorageRoot() {
        assertEquals(
            "Music/Downloads/",
            FolderPaths.fromAbsoluteDataPath("/storage/emulated/0/Music/Downloads/track.mp3"),
        )
    }

    @Test
    fun fromAbsoluteDataPathReturnsNullWithoutParent() {
        assertNull(FolderPaths.fromAbsoluteDataPath("track.mp3"))
    }

    @Test
    fun exclusionLikePatternsMatchScopedAndLegacy() {
        val paths = listOf("Download/WhatsApp/", "Music/Ringtones/")
        assertEquals(
            listOf("Download/WhatsApp/%", "Music/Ringtones/%"),
            SavedFolders.likePatterns(paths, scoped = true),
        )
        assertEquals(
            listOf("%/Download/WhatsApp/%", "%/Music/Ringtones/%"),
            SavedFolders.likePatterns(paths, scoped = false),
        )
    }
}
