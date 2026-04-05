package com.example.kpkn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppThemeMode {
    HIGH_CONTRAST
}

private val HighContrastColorScheme = darkColorScheme(
    primary = Color.Yellow,
    secondary = Color.Cyan,
    tertiary = Color.Magenta,
    background = Color.Black,
    surface = Color.Black,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    outline = Color.White,
    surfaceVariant = Color(0xFF333333)
)

@Composable
fun KPKNTheme(
    themeMode: AppThemeMode = AppThemeMode.HIGH_CONTRAST,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HighContrastColorScheme,
        typography = Typography,
        content = content
    )
}
