package com.autoomstudio.mp3studio.data.duplicates

import com.autoomstudio.mp3studio.data.model.Song
import kotlin.math.abs

/** Songs that are copies of each other. [songs] are ordered best quality first. */
data class DuplicateGroup(
    val songs: List<Song>,
    val keptId: Long,
    /** Extra copies the user chose to show again. */
    val restoredIds: Set<Long>,
) {
    val kept: Song get() = songs.first { it.id == keptId }
    val hiddenIds: Set<Long> get() = songs.mapTo(mutableSetOf()) { it.id } - keptId - restoredIds
}

/** Which songs to hide, and which kept copy stands in for each hidden one. */
class DuplicateIndex(val groups: List<DuplicateGroup>) {

    val hiddenIds: Set<Long> = groups.flatMapTo(mutableSetOf()) { it.hiddenIds }

    private val keptByHidden: Map<Long, Long> =
        groups.flatMap { group -> group.hiddenIds.map { it to group.keptId } }.toMap()

    private val groupBySong: Map<Long, DuplicateGroup> =
        groups.flatMap { group -> group.songs.map { it.id to group } }.toMap()

    fun visible(songs: List<Song>): List<Song> =
        if (hiddenIds.isEmpty()) songs else songs.filterNot { it.id in hiddenIds }

    /** The song shown in place of [songId]: its kept copy when hidden, otherwise itself. */
    fun canonicalId(songId: Long): Long = keptByHidden[songId] ?: songId

    fun groupOf(songId: Long): DuplicateGroup? = groupBySong[songId]

    companion object {
        val EMPTY = DuplicateIndex(emptyList())
    }
}

object DuplicateDetector {

    const val DURATION_TOLERANCE_MS = 2_000L

    private val LEADING_TRACK_NUMBER = Regex("""^\d{1,3}\s*[-._)]\s*""")
    private val PUNCTUATION = Regex("""[^\p{L}\p{N}\s]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Lowercases, drops punctuation and a leading track number like "01 - ", and collapses spaces.
     * Words such as "live" or "remix" are kept, so different versions never match.
     */
    fun normalize(value: String): String =
        value.trim()
            .lowercase()
            .replace(LEADING_TRACK_NUMBER, "")
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()

    fun isLossless(mimeType: String): Boolean {
        val mime = mimeType.lowercase()
        return "flac" in mime || "wav" in mime || "alac" in mime || "aiff" in mime
    }

    /** Lossless first, then higher bitrate, then the earliest added. */
    val qualityOrder: Comparator<Song> = compareByDescending<Song> { isLossless(it.mimeType) }
        .thenByDescending { it.bitrate }
        .thenBy { it.dateAdded }
        .thenBy { it.id }

    /**
     * Groups songs whose normalized title and artist match with durations within
     * [DURATION_TOLERANCE_MS], plus songs that share a content [fingerprints] entry.
     * Each returned group has at least two songs, ordered by [qualityOrder].
     */
    fun findGroups(songs: List<Song>, fingerprints: Map<Long, String> = emptyMap()): List<List<Song>> {
        if (songs.size < 2) return emptyList()
        val sets = DisjointSets(songs.size)

        songs.indices
            .groupBy { i -> metadataKey(songs[i]) }
            .forEach { (key, indices) ->
                if (key == null || indices.size < 2) return@forEach
                val byDuration = indices.sortedBy { songs[it].durationMs }
                for (k in 1 until byDuration.size) {
                    val previous = songs[byDuration[k - 1]]
                    val current = songs[byDuration[k]]
                    if (abs(current.durationMs - previous.durationMs) <= DURATION_TOLERANCE_MS) {
                        sets.union(byDuration[k - 1], byDuration[k])
                    }
                }
            }

        if (fingerprints.isNotEmpty()) {
            songs.indices
                .filter { fingerprints[songs[it].id] != null }
                .groupBy { fingerprints.getValue(songs[it].id) }
                .values
                .forEach { indices -> indices.drop(1).forEach { sets.union(indices.first(), it) } }
        }

        return songs.indices
            .groupBy(sets::find)
            .values
            .filter { it.size > 1 }
            .map { indices -> indices.map(songs::get).sortedWith(qualityOrder) }
            .sortedBy { normalize(it.first().title) }
    }

    /**
     * Picks the copy to keep in each group: the user's choice from [keepOverrides] when there is one,
     * otherwise the best quality. [restoredIds] stay visible next to the kept copy.
     */
    fun buildIndex(
        songs: List<Song>,
        fingerprints: Map<Long, String> = emptyMap(),
        keepOverrides: Set<Long> = emptySet(),
        restoredIds: Set<Long> = emptySet(),
    ): DuplicateIndex {
        val groups = findGroups(songs, fingerprints).map { group ->
            val kept = group.firstOrNull { it.id in keepOverrides } ?: group.first()
            DuplicateGroup(
                songs = group,
                keptId = kept.id,
                restoredIds = group.mapNotNullTo(mutableSetOf()) { song ->
                    song.id.takeIf { it != kept.id && it in restoredIds }
                },
            )
        }
        return DuplicateIndex(groups)
    }

    private fun metadataKey(song: Song): String? {
        val title = normalize(song.title)
        if (title.isEmpty()) return null
        return title + '\u0000' + normalize(song.artist)
    }

    private class DisjointSets(size: Int) {
        private val parent = IntArray(size) { it }

        fun find(i: Int): Int {
            var root = i
            while (parent[root] != root) root = parent[root]
            var node = i
            while (parent[node] != root) {
                val next = parent[node]
                parent[node] = root
                node = next
            }
            return root
        }

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA != rootB) parent[rootB] = rootA
        }
    }
}
