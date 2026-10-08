package com.autoomstudio.mp3studio.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {

    private fun map(
        hasItem: Boolean = true,
        mediaId: String? = "42",
        title: CharSequence? = "Song",
        artist: CharSequence? = "Band",
        isPlaying: Boolean = true,
    ) = widgetStateOf(hasItem, mediaId, title, artist, isPlaying, unknownTitle = "Unknown title", unknownArtist = "Unknown Artist")

    @Test
    fun nothingQueuedIsIdle() {
        val state = map(hasItem = false)
        assertTrue(state.isIdle)
        assertEquals(WidgetState.Idle, state)
    }

    @Test
    fun queuedSongMapsTitleArtistAndPlayState() {
        val state = map()
        assertFalse(state.isIdle)
        assertEquals(WidgetState(songId = 42L, title = "Song", artist = "Band", isPlaying = true), state)
    }

    @Test
    fun blankTagsFallBackToUnknown() {
        val state = map(title = "  ", artist = null)
        assertEquals("Unknown title", state.title)
        assertEquals("Unknown Artist", state.artist)
    }

    @Test
    fun nonNumericMediaIdStillCountsAsQueued() {
        val state = map(mediaId = "not-a-store-id", isPlaying = false)
        assertFalse(state.isIdle)
        assertEquals(UNKNOWN_SONG_ID, state.songId)
        assertFalse(state.isPlaying)
    }
}
