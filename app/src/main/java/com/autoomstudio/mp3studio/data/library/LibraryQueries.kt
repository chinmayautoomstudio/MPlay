package com.autoomstudio.mp3studio.data.library

import com.autoomstudio.mp3studio.data.model.Album
import com.autoomstudio.mp3studio.data.model.Artist
import com.autoomstudio.mp3studio.data.model.Song

enum class SongSortOrder { Title, Artist, Album, DateAdded, Duration }

object LibraryQueries {

    const val VARIOUS_ARTISTS = "Various Artists"

    private val byTitle = compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
    private val inAlbumOrder = compareBy<Song> { it.trackNumber == 0 }
        .thenBy { it.trackNumber }
        .then(byTitle)

    /** Songs whose title, artist or album contains [query], ignoring case; all songs when blank. */
    fun filterSongs(songs: List<Song>, query: String): List<Song> {
        val needle = query.trim()
        if (needle.isEmpty()) return songs
        return songs.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.artist.contains(needle, ignoreCase = true) ||
                it.album.contains(needle, ignoreCase = true)
        }
    }

    /** Text orders are A to Z; date added is newest first and duration is longest first. */
    fun sortSongs(songs: List<Song>, order: SongSortOrder): List<Song> {
        val comparator: Comparator<Song> = when (order) {
            SongSortOrder.Title -> byTitle
            SongSortOrder.Artist ->
                compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.then(byTitle)
            SongSortOrder.Album ->
                compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.then(inAlbumOrder)
            SongSortOrder.DateAdded -> compareByDescending<Song> { it.dateAdded }.then(byTitle)
            SongSortOrder.Duration -> compareByDescending<Song> { it.durationMs }.then(byTitle)
        }
        return songs.sortedWith(comparator)
    }

    /** One album per MediaStore album ID, sorted by title, songs in track order. */
    fun groupAlbums(songs: List<Song>): List<Album> =
        songs.groupBy { it.albumId }
            .map { (albumId, albumSongs) ->
                val ordered = albumSongs.sortedWith(inAlbumOrder)
                val first = ordered.first()
                val artists = ordered.mapTo(mutableSetOf()) { it.artist }
                Album(
                    id = albumId,
                    title = first.album,
                    artist = artists.singleOrNull() ?: VARIOUS_ARTISTS,
                    artUri = first.albumArtUri,
                    songs = ordered,
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    /** One artist per name, sorted by name with [SongMapper.UNKNOWN_ARTIST] last. */
    fun groupArtists(songs: List<Song>): List<Artist> =
        songs.groupBy { it.artist }
            .map { (name, artistSongs) ->
                val albums = groupAlbums(artistSongs)
                Artist(name = name, albums = albums, songs = albums.flatMap { it.songs })
            }
            .sortedWith(
                compareBy<Artist> { it.name == SongMapper.UNKNOWN_ARTIST }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
}
