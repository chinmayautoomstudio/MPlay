package com.autoomstudio.mp3studio.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongSelectionTest {

    @Test
    fun `toggle adds then removes an id`() {
        val added = SelectionQueries.toggle(setOf(1L), 2L)
        assertEquals(setOf(1L, 2L), added)
        assertEquals(setOf(1L), SelectionQueries.toggle(added, 2L))
    }

    @Test
    fun `toggle keeps the order songs were picked in`() {
        val picked = listOf(5L, 1L, 3L).fold(emptySet<Long>(), SelectionQueries::toggle)
        assertEquals(listOf(5L, 1L, 3L), picked.toList())
    }

    @Test
    fun `retain drops ids that are no longer available and keeps order`() {
        val kept = SelectionQueries.retain(linkedSetOf(4L, 2L, 9L), available = setOf(2L, 4L))
        assertEquals(listOf(4L, 2L), kept.toList())
    }

    @Test
    fun `selection is active only while something is selected`() {
        val selection = SongSelection()
        assertFalse(selection.isActive)
        selection.toggle(7L)
        assertTrue(selection.isActive)
        assertTrue(selection.isSelected(7L))
        selection.toggle(7L)
        assertFalse(selection.isActive)
    }

    @Test
    fun `clear and retainOnly update the selection`() {
        val selection = SongSelection(setOf(1L, 2L, 3L))
        selection.retainOnly(setOf(1L, 3L))
        assertEquals(setOf(1L, 3L), selection.selectedIds)
        selection.clear()
        assertFalse(selection.isActive)
    }
}
