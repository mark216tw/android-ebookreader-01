package com.minireader.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.minireader.app.data.prefs.ReaderTheme
import com.minireader.app.ui.reader.readerColors

/**
 * Material 3 colour scheme derived from the active eye-care palette so the
 * shelf, settings and their dialogs/sheets stay visually consistent with the
 * reader.
 */
fun readerColorScheme(theme: ReaderTheme): ColorScheme {
    val reader = readerColors(theme)
    val surfaceVariant = reader.secondary
        .copy(alpha = if (reader.isDark) 0.18f else 0.10f)
        .compositeOver(reader.background)
    val container = reader.secondary
        .copy(alpha = if (reader.isDark) 0.24f else 0.16f)
        .compositeOver(reader.background)
    val error = if (reader.isDark) Color(0xFFFFB4AB) else Color(0xFFB3261E)
    val onError = if (reader.isDark) Color(0xFF690005) else Color.White
    val errorContainer = if (reader.isDark) Color(0xFF93000A) else Color(0xFFF9DEDC)
    val onErrorContainer = if (reader.isDark) Color(0xFFFFDAD6) else Color(0xFF410E0B)

    val builder = if (reader.isDark) {
        darkColorScheme(
            primary = reader.accent,
            onPrimary = reader.background,
            primaryContainer = reader.accent,
            onPrimaryContainer = reader.background,
            secondary = reader.secondary,
            onSecondary = reader.background,
            secondaryContainer = container,
            onSecondaryContainer = reader.foreground,
            tertiary = reader.link,
            onTertiary = reader.background,
            background = reader.background,
            onBackground = reader.foreground,
            surface = reader.background,
            onSurface = reader.foreground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = reader.secondary,
            surfaceTint = reader.accent,
            inverseSurface = reader.foreground,
            inverseOnSurface = reader.background,
            inversePrimary = reader.accent,
            outline = reader.secondary,
            outlineVariant = reader.secondary.copy(alpha = 0.45f),
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            surfaceDim = reader.background,
            surfaceBright = surfaceVariant,
            surfaceContainerLowest = reader.background,
            surfaceContainerLow = surfaceVariant,
            surfaceContainer = surfaceVariant,
            surfaceContainerHigh = container,
            surfaceContainerHighest = container,
        )
    } else {
        lightColorScheme(
            primary = reader.accent,
            onPrimary = reader.background,
            primaryContainer = reader.accent,
            onPrimaryContainer = reader.background,
            secondary = reader.secondary,
            onSecondary = reader.background,
            secondaryContainer = container,
            onSecondaryContainer = reader.foreground,
            tertiary = reader.link,
            onTertiary = reader.background,
            background = reader.background,
            onBackground = reader.foreground,
            surface = reader.background,
            onSurface = reader.foreground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = reader.secondary,
            surfaceTint = reader.accent,
            inverseSurface = reader.foreground,
            inverseOnSurface = reader.background,
            inversePrimary = reader.accent,
            outline = reader.secondary,
            outlineVariant = reader.secondary.copy(alpha = 0.45f),
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            surfaceDim = reader.background,
            surfaceBright = surfaceVariant,
            surfaceContainerLowest = reader.background,
            surfaceContainerLow = surfaceVariant,
            surfaceContainer = surfaceVariant,
            surfaceContainerHigh = container,
            surfaceContainerHighest = container,
        )
    }
    return builder
}
