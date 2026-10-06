package com.autoomstudio.mplay.singalong

/** File names for saved sing-alongs: "Song title - Sing along", with " (2)", " (3)" ... when taken (SA13). */
object RecordingNames {
    const val EXTENSION = ".m4a"
    private const val SUFFIX = " - Sing along"
    private const val FALLBACK = "Sing along"
    const val MAX_LENGTH = 100
    private val forbidden = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

    fun suggested(songTitle: String): String = if (songTitle.isBlank()) FALLBACK else clean(songTitle + SUFFIX)

    /** Drops characters file systems reject and trims to a sane length. */
    fun clean(name: String): String =
        name.replace(forbidden, " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(MAX_LENGTH).trim()
            .ifEmpty { FALLBACK }

    /** The first of `name`, `name (2)`, `name (3)` ... for which [taken] is false, as a file name. */
    fun unique(name: String, taken: (fileName: String) -> Boolean): String {
        val base = clean(name)
        var candidate = base + EXTENSION
        var n = 2
        while (taken(candidate)) candidate = "$base ($n)$EXTENSION".also { n++ }
        return candidate
    }
}
