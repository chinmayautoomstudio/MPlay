package com.autoomstudio.mplay.data.playlist

import android.net.Uri
import com.autoomstudio.mplay.data.model.Song
import com.autoomstudio.mplay.data.model.StoredPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class PlaylistQueriesTest {

    private val uri: Uri = mock(Uri::class.java)

    private fun song(id: Long) = Song(
        id = id,
        uri = uri,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1,
        durationMs = 1_000,
        dateAdded = 0,
        albumArtUri = uri,
    )

    private val library = listOf(1L, 2L, 3L, 4L).associateWith { song(it) }

    @Test
    fun `resolve keeps stored order`() {
        val playlist = PlaylistQueries.resolvePlaylist(StoredPlaylist(7, "Mix", listOf(3, 1, 4)), library)
        assertEquals(listOf(3L, 1L, 4L), playlist.songs.map { it.id })
        assertEquals(0, playlist.unavailableCount)
        assertEquals(7L, playlist.id)
        assertEquals("Mix", playlist.name)
    }

    @Test
    fun `resolve skips and counts missing songs`() {
        val playlist = PlaylistQueries.resolvePlaylist(StoredPlaylist(1, "Mix", listOf(9, 2, 8, 1)), library)
        assertEquals(listOf(2L, 1L), playlist.songs.map { it.id })
        assertEquals(2, playlist.unavailableCount)
    }

    @Test
    fun `resolve plays the kept copy for a hidden duplicate`() {
        val visible = library - 9L
        val canonical = { id: Long -> if (id == 9L) 2L else id }
        val playlist = PlaylistQueries.resolvePlaylist(StoredPlaylist(1, "Mix", listOf(9, 1)), visible, canonical)
        assertEquals(listOf(2L, 1L), playlist.songs.map { it.id })
        assertEquals(0, playlist.unavailableCount)
        assertEquals(listOf(9L, 1L), playlist.storedIdsOf(playlist.songs))
    }

    @Test
    fun `resolve shows a song once when both copies are in the playlist`() {
        val canonical = { id: Long -> if (id == 9L) 2L else id }
        val playlist = PlaylistQueries.resolvePlaylist(StoredPlaylist(1, "Mix", listOf(2, 3, 9)), library, canonical)
        assertEquals(listOf(2L, 3L), playlist.songs.map { it.id })
        assertEquals(listOf(2L, 9L), playlist.storedIdsOf(listOf(song(2))))
    }

    @Test
    fun `resolve empty playlist`() {
        val playlist = PlaylistQueries.resolvePlaylist(StoredPlaylist(1, "Empty", emptyList()), library)
        assertEquals(emptyList<Song>(), playlist.songs)
        assertEquals(0, playlist.unavailableCount)
    }

    @Test
    fun `move item down and up`() {
        assertEquals(listOf("b", "c", "a", "d"), PlaylistQueries.moveItem(listOf("a", "b", "c", "d"), 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), PlaylistQueries.moveItem(listOf("a", "b", "c", "d"), 3, 0))
    }

    @Test
    fun `move item ignores same or out of range indices`() {
        val list = listOf("a", "b")
        assertSame(list, PlaylistQueries.moveItem(list, 1, 1))
        assertSame(list, PlaylistQueries.moveItem(list, -1, 0))
        assertSame(list, PlaylistQueries.moveItem(list, 0, 5))
    }

    @Test
    fun `visible order keeps unavailable songs in their slots`() {
        // 9 is unavailable; the visible songs 1, 2, 3 are reversed.
        assertEquals(
            listOf(3L, 9L, 2L, 1L),
            PlaylistQueries.applyVisibleOrder(listOf(1, 9, 2, 3), listOf(3, 2, 1)),
        )
    }

    @Test
    fun `visible order appends ids not yet stored`() {
        assertEquals(listOf(2L, 1L, 5L), PlaylistQueries.applyVisibleOrder(listOf(1, 2), listOf(2, 1, 5)))
    }
}
