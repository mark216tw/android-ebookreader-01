package com.minireader.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5C3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0E0CE),
    onPrimaryContainer = Color(0xFF2B1F14),
    secondary = Color(0xFF6B6257),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE5D8),
    onSecondaryContainer = Color(0xFF24211D),
    background = Color(0xFFF7F3EC),
    onBackground = Color(0xFF24211D),
    surface = Color(0xFFF7F3EC),
    onSurface = Color(0xFF24211D),
    surfaceVariant = Color(0xFFEDE5D8),
    onSurfaceVariant = Color(0xFF6B6257),
    outline = Color(0xFF9A8C7C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD8B48A),
    onPrimary = Color(0xFF2B1F14),
    primaryContainer = Color(0xFF5C452E),
    onPrimaryContainer = Color(0xFFF5E6D3),
    secondary = Color(0xFFB9AD9C),
    onSecondary = Color(0xFF24211D),
    secondaryContainer = Color(0xFF3A342C),
    onSecondaryContainer = Color(0xFFEDE5D8),
    background = Color(0xFF141210),
    onBackground = Color(0xFFEDE5D8),
    surface = Color(0xFF141210),
    onSurface = Color(0xFFEDE5D8),
    surfaceVariant = Color(0xFF2B2620),
    onSurfaceVariant = Color(0xFFB9AD9C),
    outline = Color(0xFF7A6A58),
)

@Composable
fun MiniReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
