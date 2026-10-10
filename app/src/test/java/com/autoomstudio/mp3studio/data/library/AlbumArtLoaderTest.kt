package com.autoomstudio.mp3studio.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumArtLoaderTest {

    @Test
    fun albumArtUriGivesItsAlbumId() {
        assertEquals(42L, AlbumArtLoader.albumIdOf("content://media/external/audio/albumart/42"))
        assertEquals(0L, AlbumArtLoader.albumIdOf("content://media/external/audio/albumart/0"))
    }

    @Test
    fun otherUrisAreIgnored() {
        assertNull(AlbumArtLoader.albumIdOf("content://media/external/audio/media/42"))
        assertNull(AlbumArtLoader.albumIdOf("content://media/external/audio/albumart"))
        assertNull(AlbumArtLoader.albumIdOf("file:///sdcard/Download/cover.jpg"))
        assertNull(AlbumArtLoader.albumIdOf("https://example.com/content://media/external/audio/albumart/42"))
    }

    @Test
    fun malformedIdsAreIgnored() {
        assertNull(AlbumArtLoader.albumIdOf("content://media/external/audio/albumart/"))
        assertNull(AlbumArtLoader.albumIdOf("content://media/external/audio/albumart/abc"))
        assertNull(AlbumArtLoader.albumIdOf("content://media/external/audio/albumart/-1"))
        assertNull(AlbumArtLoader.albumIdOf("content://media/external/audio/albumart/42/extra"))
    }
}
