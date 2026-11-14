package com.autoomstudio.mp3studio.data.library

import com.autoomstudio.mp3studio.data.model.Song

/** Stable hash of song id + size + mtime so unchanged MediaStore refreshes can skip heavy work. */
object LibraryContentSignature {
    fun of(songs: List<Song>): Long {
        var hash = songs.size.toLong()
        for (song in songs) {
            hash = 31 * hash + song.id
            hash = 31 * hash + song.sizeBytes
            hash = 31 * hash + song.dateModified
        }
        return hash
    }
}
