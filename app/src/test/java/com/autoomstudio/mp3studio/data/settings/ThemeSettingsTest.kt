package com.autoomstudio.mp3studio.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsTest {

    @Test
    fun nothingStoredUsesSystemThemeWithoutDynamicColor() {
        assertEquals(ThemeSettings(ThemeMode.System, dynamicColor = false), themeSettingsOf(null, null))
    }

    @Test
    fun storedValuesAreRead() {
        assertEquals(ThemeSettings(ThemeMode.Dark, dynamicColor = true), themeSettingsOf("Dark", true))
    }

    @Test
    fun unknownStoredModeFallsBackToSystem() {
        assertEquals(ThemeMode.System, themeSettingsOf("Sepia", false).mode)
    }
}
