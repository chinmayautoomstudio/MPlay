package com.autoomstudio.mp3studio.data.duplicates

import android.net.Uri
import com.autoomstudio.mp3studio.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class DuplicateDetectorTest {

    private val uri: Uri = mock(Uri::class.java)

    private fun song(
        id: Long,
        title: String = "Song",
        artist: String = "Artist",
        durationMs: Long = 200_000L,
        mimeType: String = "audio/mpeg",
        bitrate: Int = 192_000,
        dateAdded: Long = id,
        sizeBytes: Long = 1_000L + id,
    ) = Song(
        id = id,
        uri = uri,
        title = title,
        artist = artist,
        album = "Album",
        albumId = 1L,
        durationMs = durationMs,
        dateAdded = dateAdded,
        albumArtUri = uri,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
        bitrate = bitrate,
    )

    @Test
    fun normalizeIgnoresCasePunctuationSpacingAndTrackNumbers() {
        assertEquals("hello world", DuplicateDetector.normalize("  Hello,   World! "))
        assertEquals("hello world", DuplicateDetector.normalize("01 - Hello World"))
        assertEquals("hello world", DuplicateDetector.normalize("3. Hello-World"))
    }

    @Test
    fun normalizeKeepsNumbersThatArePartOfTheTitle() {
        assertEquals("7 rings", DuplicateDetector.normalize("7 Rings"))
    }

    @Test
    fun normalizeKeepsVersionWords() {
        assertEquals("song live", DuplicateDetector.normalize("Song (Live)"))
    }

    @Test
    fun matchesSameTitleAndArtistWithinTwoSeconds() {
        val groups = DuplicateDetector.findGroups(
            listOf(
                song(1, title = "Hello World", durationMs = 200_000L),
                song(2, title = "hello world!", artist = "ARTIST", durationMs = 201_900L),
            ),
        )
        assertEquals(1, groups.size)
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun durationsMoreThanTwoSecondsApartDoNotMatch() {
        val groups = DuplicateDetector.findGroups(
            listOf(song(1, durationMs = 200_000L), song(2, durationMs = 202_500L)),
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun differentArtistsDoNotMatch() {
        val groups = DuplicateDetector.findGroups(listOf(song(1, artist = "A"), song(2, artist = "B")))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun liveAndStudioVersionsDoNotMatch() {
        val groups = DuplicateDetector.findGroups(listOf(song(1, title = "Song"), song(2, title = "Song (Live)")))
        assertTrue(groups.isEmpty())
    }

    @Test
    fun identicalFingerprintsMatchEvenWithDifferentTags() {
        val groups = DuplicateDetector.findGroups(
            listOf(song(1, title = "Track 01"), song(2, title = "Real Name", durationMs = 10_000L)),
            fingerprints = mapOf(1L to "abc", 2L to "abc"),
        )
        assertEquals(setOf(1L, 2L), groups.single().map { it.id }.toSet())
    }

    @Test
    fun keepsLosslessThenHigherBitrateThenEarliest() {
        val mp3Early = song(1, bitrate = 320_000, dateAdded = 1)
        val mp3Late = song(2, bitrate = 320_000, dateAdded = 5)
        val mp3Low = song(3, bitrate = 128_000, dateAdded = 0)
        val flac = song(4, mimeType = "audio/flac", bitrate = 900_000, dateAdded = 9)
        val index = DuplicateDetector.buildIndex(listOf(mp3Late, mp3Low, flac, mp3Early))
        val group = index.groups.single()
        assertEquals(listOf(4L, 1L, 2L, 3L), group.songs.map { it.id })
        assertEquals(4L, group.keptId)
        assertEquals(setOf(1L, 2L, 3L), index.hiddenIds)
    }

    @Test
    fun userKeepChoiceWins() {
        val index = DuplicateDetector.buildIndex(
            listOf(song(1, bitrate = 320_000), song(2, bitrate = 128_000)),
            keepOverrides = setOf(2L),
        )
        assertEquals(2L, index.groups.single().keptId)
        assertEquals(setOf(1L), index.hiddenIds)
    }

    @Test
    fun restoredCopiesStayVisible() {
        val songs = listOf(song(1, bitrate = 320_000), song(2, bitrate = 192_000), song(3, bitrate = 128_000))
        val index = DuplicateDetector.buildIndex(songs, restoredIds = setOf(3L))
        assertEquals(setOf(2L), index.hiddenIds)
        assertEquals(listOf(1L, 3L), index.visible(songs).map { it.id })
    }

    @Test
    fun hiddenSongsResolveToTheKeptCopy() {
        val index = DuplicateDetector.buildIndex(listOf(song(1, bitrate = 320_000), song(2, bitrate = 128_000)))
        assertEquals(1L, index.canonicalId(2L))
        assertEquals(1L, index.canonicalId(1L))
        assertEquals(99L, index.canonicalId(99L))
    }

    @Test
    fun uniqueSongsProduceNoGroups() {
        val index = DuplicateDetector.buildIndex(listOf(song(1, title = "A"), song(2, title = "B")))
        assertTrue(index.groups.isEmpty())
        assertTrue(index.hiddenIds.isEmpty())
    }
}
