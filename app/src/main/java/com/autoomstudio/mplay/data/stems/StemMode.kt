package com.autoomstudio.mplay.data.stems

/** Which version of a processed song plays. Session-wide; songs without stems always play the original. */
enum class StemMode {
    Original,
    Instrumental,
    Vocals,
    ;

    companion object {
        /** Unknown or missing stored values fall back to [Original]. */
        fun fromName(name: String?): StemMode = entries.firstOrNull { it.name == name } ?: Original
    }
}
