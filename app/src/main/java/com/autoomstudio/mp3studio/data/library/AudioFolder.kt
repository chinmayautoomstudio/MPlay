package com.autoomstudio.mp3studio.data.library

/** A MediaStore folder that contains library-eligible audio. */
data class AudioFolder(
    /** Relative path with trailing slash, e.g. `Music/Downloads/`. */
    val relativePath: String,
    val songCount: Int,
)
