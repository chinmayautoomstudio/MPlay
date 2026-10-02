package com.autoomstudio.mplay.data.library

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import com.autoomstudio.mplay.data.clip.ClipStore
import com.autoomstudio.mplay.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreSongSource(private val contentResolver: ContentResolver) {

    val collectionUri: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    suspend fun querySongs(): List<Song> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.TRACK,
        )
        // Saved clips can be shorter than the minimum, so their folder skips the length check.
        @Suppress("DEPRECATION")
        val (pathColumn, clipsPattern) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH to "${ClipStore.CLIPS_RELATIVE_PATH}%"
        } else {
            MediaStore.Audio.Media.DATA to "%/${ClipStore.CLIPS_RELATIVE_PATH}%"
        }
        // NULL means the scanner has not extracted metadata yet; show those rows rather than hide them.
        val selection =
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL)" +
                " AND (${MediaStore.Audio.Media.DURATION} >= ? OR ${MediaStore.Audio.Media.DURATION} IS NULL" +
                " OR $pathColumn LIKE ?)"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString(), clipsPattern)
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val songs = mutableListOf<Song>()
        contentResolver.query(collectionUri, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val albumId = cursor.getLong(albumIdCol)
                    songs += Song(
                        id = id,
                        uri = ContentUris.withAppendedId(collectionUri, id),
                        title = SongMapper.displayTitle(
                            cursor.getString(titleCol),
                            cursor.getString(nameCol),
                        ),
                        artist = SongMapper.displayArtist(cursor.getString(artistCol)),
                        album = SongMapper.displayAlbum(cursor.getString(albumCol)),
                        albumId = albumId,
                        durationMs = cursor.getLong(durationCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        albumArtUri = ContentUris.withAppendedId(ALBUM_ART_URI, albumId),
                        // MediaStore encodes disc and track as disc * 1000 + track.
                        trackNumber = cursor.getInt(trackCol) % 1000,
                    )
                }
            }
        songs
    }

    private companion object {
        const val MIN_DURATION_MS = 30_000L
        val ALBUM_ART_URI: Uri = "content://media/external/audio/albumart".toUri()
    }
}
