package com.minireader.app.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.minireader.app.data.prefs.ReaderTheme

data class ReaderColors(
    val background: Color,
    val foreground: Color,
    val secondary: Color,
    val accent: Color,
    val link: Color,
    val isDark: Boolean,
)

fun readerColors(theme: ReaderTheme): ReaderColors = when (theme) {
    ReaderTheme.LIGHT -> ReaderColors(
        background = Color(0xFFFFFFFF),
        foreground = Color(0xFF24211D),
        secondary = Color(0xFF5E564B),
        accent = Color(0xFF7A5C3E),
        link = Color(0xFF7E4E22),
        isDark = false,
    )
    ReaderTheme.SEPIA -> ReaderColors(
        background = Color(0xFFF5ECD8),
        foreground = Color(0xFF3B3126),
        secondary = Color(0xFF6F5F49),
        accent = Color(0xFF8B5E34),
        link = Color(0xFF7A4F27),
        isDark = false,
    )
    ReaderTheme.GREEN -> ReaderColors(
        background = Color(0xFFCCE8CF),
        foreground = Color(0xFF1F3A24),
        secondary = Color(0xFF405C46),
        accent = Color(0xFF2F6B3B),
        link = Color(0xFF1F6333),
        isDark = false,
    )
    ReaderTheme.BLUE_FILTER -> ReaderColors(
        background = Color(0xFFF4E3C1),
        foreground = Color(0xFF2A2118),
        secondary = Color(0xFF665538),
        accent = Color(0xFF8A5A2B),
        link = Color(0xFF6E4418),
        isDark = false,
    )
    ReaderTheme.BLACK -> ReaderColors(
        background = Color(0xFF000000),
        foreground = Color(0xFFE6E2DA),
        secondary = Color(0xFF9A9388),
        accent = Color(0xFFB08A57),
        link = Color(0xFFD3AC72),
        isDark = true,
    )
    ReaderTheme.DARK -> ReaderColors(
        background = Color(0xFF121212),
        foreground = Color(0xFFE3DED5),
        secondary = Color(0xFFA39B8F),
        accent = Color(0xFFC9A36B),
        link = Color(0xFFE0B97D),
        isDark = true,
    )
    ReaderTheme.GRAY -> ReaderColors(
        background = Color(0xFFE8E8E8),
        foreground = Color(0xFF1C1C1C),
        secondary = Color(0xFF545454),
        accent = Color(0xFF555555),
        link = Color(0xFF3B3B3B),
        isDark = false,
    )
}

fun Color.toCssHex(): String = "#%06X".format(toArgb() and 0xFFFFFF)
