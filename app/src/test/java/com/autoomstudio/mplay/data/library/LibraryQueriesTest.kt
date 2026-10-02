package com.autoomstudio.mplay.data.library

import android.net.Uri
import com.autoomstudio.mplay.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito.mock

class LibraryQueriesTest {

    private val uri: Uri = mock(Uri::class.java)

    private fun song(
        id: Long,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        albumId: Long = 1,
        durationMs: Long = 1_000,
        dateAdded: Long = 0,
        trackNumber: Int = 0,
    ) = Song(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        dateAdded = dateAdded,
        albumArtUri = uri,
        trackNumber = trackNumber,
    )

    private fun List<Song>.ids() = map { it.id }

    private val library = listOf(
        song(1, "Yellow", artist = "Coldplay", album = "Parachutes", albumId = 10, durationMs = 269_000, dateAdded = 300),
        song(2, "clocks", artist = "Coldplay", album = "A Rush of Blood", albumId = 11, durationMs = 307_000, dateAdded = 100),
        song(3, "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera", albumId = 12, durationMs = 354_000, dateAdded = 200),
    )

    @Test
    fun `blank query returns all songs`() {
        assertSame(library, LibraryQueries.filterSongs(library, "   "))
    }

    @Test
    fun `filter matches title artist and album ignoring case and partially`() {
        assertEquals(listOf(2L), LibraryQueries.filterSongs(library, "CLO").ids())
        assertEquals(listOf(1L, 2L), LibraryQueries.filterSongs(library, "coldplay").ids())
        assertEquals(listOf(3L), LibraryQueries.filterSongs(library, " opera ").ids())
        assertEquals(emptyList<Long>(), LibraryQueries.filterSongs(library, "zzz").ids())
    }

    @Test
    fun `title sort is case insensitive`() {
        assertEquals(listOf(3L, 2L, 1L), LibraryQueries.sortSongs(library, SongSortOrder.Title).ids())
    }

    @Test
    fun `artist sort breaks ties by title`() {
        assertEquals(listOf(2L, 1L, 3L), LibraryQueries.sortSongs(library, SongSortOrder.Artist).ids())
    }

    @Test
    fun `album sort orders by album name`() {
        assertEquals(listOf(3L, 2L, 1L), LibraryQueries.sortSongs(library, SongSortOrder.Album).ids())
    }

    @Test
    fun `date added sort is newest first`() {
        assertEquals(listOf(1L, 3L, 2L), LibraryQueries.sortSongs(library, SongSortOrder.DateAdded).ids())
    }

    @Test
    fun `duration sort is longest first`() {
        assertEquals(listOf(3L, 2L, 1L), LibraryQueries.sortSongs(library, SongSortOrder.Duration).ids())
    }

    @Test
    fun `albums group by id with songs in track order and untagged last`() {
        val songs = listOf(
            song(1, "Untagged", albumId = 5, album = "Beta", trackNumber = 0),
            song(2, "Second", albumId = 5, album = "Beta", trackNumber = 2),
            song(3, "First", albumId = 5, album = "Beta", trackNumber = 1),
            song(4, "Only", albumId = 6, album = "alpha", artist = "Other"),
        )
        val albums = LibraryQueries.groupAlbums(songs)
        assertEquals(listOf("alpha", "Beta"), albums.map { it.title })
        assertEquals(listOf(3L, 2L, 1L), albums[1].songs.ids())
        assertEquals("Artist", albums[1].artist)
    }

    @Test
    fun `album with several artists is various artists`() {
        val songs = listOf(
            song(1, "A", artist = "One", albumId = 7),
            song(2, "B", artist = "Two", albumId = 7),
        )
        assertEquals(LibraryQueries.VARIOUS_ARTISTS, LibraryQueries.groupAlbums(songs).single().artist)
    }

    @Test
    fun `artists are sorted by name with unknown artist last`() {
        val songs = listOf(
            song(1, "A", artist = SongMapper.UNKNOWN_ARTIST, albumId = 1),
            song(2, "B", artist = "zed", albumId = 2),
            song(3, "C", artist = "Abba", albumId = 3),
            song(4, "D", artist = "Abba", albumId = 4, album = "Another"),
        )
        val artists = LibraryQueries.groupArtists(songs)
        assertEquals(listOf("Abba", "zed", SongMapper.UNKNOWN_ARTIST), artists.map { it.name })
        assertEquals(2, artists[0].albums.size)
        assertEquals(setOf(3L, 4L), artists[0].songs.ids().toSet())
    }
}
