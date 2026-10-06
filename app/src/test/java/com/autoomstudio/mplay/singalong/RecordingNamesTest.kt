package com.autoomstudio.mplay.singalong

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNamesTest {

    @Test
    fun suggestsTitleWithSingAlong() {
        assertEquals("Yesterday - Sing along", RecordingNames.suggested("Yesterday"))
        assertEquals("Sing along", RecordingNames.suggested("  "))
    }

    @Test
    fun removesCharactersFileSystemsReject() {
        assertEquals("AC DC Back In Black", RecordingNames.clean("AC/DC: Back <In> Black?"))
        assertEquals("Song", RecordingNames.clean("Song..."))
        assertEquals("Sing along", RecordingNames.clean("///"))
    }

    @Test
    fun limitsTheLength() {
        assertEquals(100, RecordingNames.clean("a".repeat(300)).length)
    }

    @Test
    fun addsASuffixWhenTheNameIsTaken() {
        val existing = setOf("Take.m4a", "Take (2).m4a")
        assertEquals("Take (3).m4a", RecordingNames.unique("Take") { it in existing })
        assertEquals("Other.m4a", RecordingNames.unique("Other") { it in existing })
    }
}
