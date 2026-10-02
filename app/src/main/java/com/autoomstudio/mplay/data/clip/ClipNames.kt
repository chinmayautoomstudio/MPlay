package com.autoomstudio.mplay.data.clip

object ClipNames {
    const val EXTENSION = ".m4a"
    private const val MAX_LENGTH = 100
    private val ILLEGAL = Regex("""[\\/:*?"<>|\u0000-\u001F]""")
    private val WHITESPACE = Regex("""\s+""")

    fun defaultName(title: String): String = "${clean(title).ifEmpty { "Clip" }} (clip)"

    /** Strips characters no filesystem accepts; blank input falls back to [fallback]. */
    fun clean(name: String, fallback: String = ""): String {
        val cleaned = name
            .replace(ILLEGAL, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trimStart('.')
            .removeSuffix(EXTENSION)
            .take(MAX_LENGTH)
            .trim()
        return cleaned.ifEmpty { fallback }
    }

    fun fileName(name: String, fallback: String): String = clean(name, clean(fallback, "Clip")) + EXTENSION
}
