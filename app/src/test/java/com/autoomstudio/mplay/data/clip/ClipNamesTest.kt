package com.autoomstudio.mplay.data.clip

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipNamesTest {

    @Test
    fun defaultName_appendsClip() {
        assertEquals("Euphoria (clip)", ClipNames.defaultName("Euphoria"))
    }

    @Test
    fun defaultName_cleansTitleAndFallsBack() {
        assertEquals("AC DC (clip)", ClipNames.defaultName("AC/DC"))
        assertEquals("Clip (clip)", ClipNames.defaultName("  "))
    }

    @Test
    fun clean_replacesIllegalCharactersAndCollapsesSpaces() {
        assertEquals("a b c d", ClipNames.clean("a/b\\c:*?\"<>|d"))
        assertEquals("Song Name", ClipNames.clean("  Song \t  Name  "))
    }

    @Test
    fun clean_dropsLeadingDotsAndExtension() {
        assertEquals("hidden", ClipNames.clean("..hidden"))
        assertEquals("Ring", ClipNames.clean("Ring.m4a"))
    }

    @Test
    fun clean_usesFallbackForBlankNames() {
        assertEquals("Fallback", ClipNames.clean(" / ", "Fallback"))
    }

    @Test
    fun clean_limitsLength() {
        assertEquals(100, ClipNames.clean("x".repeat(300)).length)
    }

    @Test
    fun fileName_addsExtension() {
        assertEquals("My clip.m4a", ClipNames.fileName("My clip", "Fallback"))
        assertEquals("Fallback.m4a", ClipNames.fileName("", "Fallback"))
    }
}
