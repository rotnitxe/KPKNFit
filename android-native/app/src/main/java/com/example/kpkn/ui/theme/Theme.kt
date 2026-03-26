package com.example.kpkn.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeMode {
    LIGHT,
    DARK,
    HIGH_CONTRAST,
    COLOR_BLIND
}

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color.White.copy(alpha = 0.7f)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFEEEEEE),
    onSurfaceVariant = Color.Black.copy(alpha = 0.6f)
)

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

// Una paleta que evita rojos/verdes puros si es posible o usa contrastes marcados
private val ColorBlindColorScheme = lightColorScheme(
    primary = Color(0xFF0072B2), // Azul fuerte
    secondary = Color(0xFFE69F00), // Naranja
    tertiary = Color(0xFF56B4E9),
    background = Color(0xFFF0F0F0),
    surface = Color.White,
    onSurface = Color.Black
)

@Composable
fun KPKNTheme(
    themeMode: AppThemeMode = AppThemeMode.LIGHT,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.HIGH_CONTRAST -> HighContrastColorScheme
        AppThemeMode.COLOR_BLIND -> ColorBlindColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}