package com.autoomstudio.mp3studio.data.library

/** Normalizes MediaStore folder paths for exclusion prefs and LIKE matching. */
object FolderPaths {

    /** Trailing slash, forward slashes, no leading slash. Empty input stays empty. */
    fun normalize(path: String): String {
        val trimmed = path.replace('\\', '/').trim('/')
        return if (trimmed.isEmpty()) "" else "$trimmed/"
    }

    /**
     * Parent folder of an absolute `DATA` path as a relative key (e.g. `Music/Foo/`),
     * or null when the path has no parent.
     */
    fun fromAbsoluteDataPath(absolutePath: String): String? {
        val normalized = absolutePath.replace('\\', '/')
        val lastSlash = normalized.lastIndexOf('/')
        if (lastSlash <= 0) return null
        var parent = normalized.substring(0, lastSlash)
        for (root in STORAGE_ROOTS) {
            val idx = parent.indexOf(root)
            if (idx >= 0) {
                parent = parent.substring(idx + root.length).trimStart('/')
                break
            }
        }
        return normalize(parent).takeIf { it.isNotEmpty() }
    }

    private val STORAGE_ROOTS = listOf(
        "/storage/emulated/0",
        "/storage/sdcard0",
        "/sdcard",
        "/mnt/sdcard",
    )
}
