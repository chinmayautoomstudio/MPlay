package com.autoomstudio.mplay.data.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SongMapperTest {

    @Test
    fun `title is used when present`() {
        assertEquals("Song", SongMapper.displayTitle("  Song ", "file.mp3"))
    }

    @Test
    fun `missing title falls back to file name without extension`() {
        assertEquals("My Track", SongMapper.displayTitle(null, "My Track.mp3"))
        assertEquals("My Track", SongMapper.displayTitle("", "My Track.flac"))
        assertEquals("My Track", SongMapper.displayTitle("<unknown>", "My Track.m4a"))
    }

    @Test
    fun `file name keeps inner dots and handles no extension`() {
        assertEquals("a.b.c", SongMapper.displayTitle(null, "a.b.c.ogg"))
        assertEquals("noext", SongMapper.displayTitle(null, "noext"))
    }

    @Test
    fun `missing artist becomes Unknown Artist`() {
        assertEquals(SongMapper.UNKNOWN_ARTIST, SongMapper.displayArtist(null))
        assertEquals(SongMapper.UNKNOWN_ARTIST, SongMapper.displayArtist("   "))
        assertEquals(SongMapper.UNKNOWN_ARTIST, SongMapper.displayArtist("<unknown>"))
        assertEquals("Artist", SongMapper.displayArtist("Artist"))
    }

    @Test
    fun `missing album becomes Unknown Album`() {
        assertEquals(SongMapper.UNKNOWN_ALBUM, SongMapper.displayAlbum(null))
        assertEquals("Album", SongMapper.displayAlbum("Album"))
    }
}
