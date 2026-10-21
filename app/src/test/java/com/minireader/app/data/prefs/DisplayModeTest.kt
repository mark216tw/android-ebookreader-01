package com.minireader.app.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayModeTest {

    @Test
    fun systemModeFollowsSystemSetting() {
        assertFalse(DisplayMode.SYSTEM.resolveDark(systemDark = false))
        assertTrue(DisplayMode.SYSTEM.resolveDark(systemDark = true))
    }

    @Test
    fun explicitModesOverrideSystemSetting() {
        assertFalse(DisplayMode.LIGHT.resolveDark(systemDark = true))
        assertTrue(DisplayMode.DARK.resolveDark(systemDark = false))
    }

    @Test
    fun effectiveThemeUsesMatchingSelection() {
        val prefs = ReaderPrefs(
            theme = ReaderTheme.SEPIA,
            darkTheme = ReaderTheme.BLACK,
            displayMode = DisplayMode.SYSTEM,
        )

        assertEquals(ReaderTheme.SEPIA, prefs.effectiveTheme(systemDark = false))
        assertEquals(ReaderTheme.BLACK, prefs.effectiveTheme(systemDark = true))
        assertEquals(
            ReaderTheme.BLACK,
            prefs.copy(displayMode = DisplayMode.DARK).effectiveTheme(systemDark = false),
        )
        assertEquals(
            ReaderTheme.SEPIA,
            prefs.copy(displayMode = DisplayMode.LIGHT).effectiveTheme(systemDark = true),
        )
    }

    @Test
    fun themesAreSplitIntoLightAndDarkGroups() {
        assertFalse(ReaderTheme.LIGHT.isDark)
        assertFalse(ReaderTheme.SEPIA.isDark)
        assertFalse(ReaderTheme.GREEN.isDark)
        assertFalse(ReaderTheme.BLUE_FILTER.isDark)
        assertFalse(ReaderTheme.GRAY.isDark)
        assertTrue(ReaderTheme.BLACK.isDark)
        assertTrue(ReaderTheme.DARK.isDark)
    }
}
