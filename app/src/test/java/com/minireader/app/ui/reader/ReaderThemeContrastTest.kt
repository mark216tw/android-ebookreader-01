package com.minireader.app.ui.reader

import androidx.compose.ui.graphics.Color
import com.minireader.app.data.prefs.ReaderTheme
import com.minireader.app.ui.theme.readerColorScheme
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderThemeContrastTest {

    @Test
    fun allThemesMeetWcagAaForTextColors() {
        ReaderTheme.entries.forEach { theme ->
            val colors = readerColors(theme)
            assertContrast(theme, "foreground", colors.foreground, colors.background)
            assertContrast(theme, "secondary", colors.secondary, colors.background)
            assertContrast(theme, "link", colors.link, colors.background)

            val homeScheme = readerColorScheme(theme)
            assertContrast(
                theme,
                "onSurfaceVariant",
                homeScheme.onSurfaceVariant,
                homeScheme.surfaceVariant,
            )
        }
    }

    private fun assertContrast(
        theme: ReaderTheme,
        label: String,
        foreground: Color,
        background: Color,
    ) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$theme $label contrast ${"%.2f".format(ratio)} is below WCAG AA 4.5:1",
            ratio >= 4.5,
        )
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = maxOf(relativeLuminance(foreground), relativeLuminance(background))
        val darker = minOf(relativeLuminance(foreground), relativeLuminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val v = value.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
