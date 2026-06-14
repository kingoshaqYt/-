package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import com.example.ui.AppThemeStyle
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ReclaimPrimary,
    secondary = ReclaimSecondary,
    tertiary = ReclaimTertiary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = ReclaimSecondary,
    secondary = ReclaimPrimary,
    tertiary = ReclaimTertiary,
    background = LightBackground,
    surface = LightSurface,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface
)

private val GrayColorScheme = darkColorScheme(
    primary = Color(0xFFFFD600), // Premium Amber Yellow highlights for titanium gray setup
    secondary = Color(0xFF94A3B8), // Slates
    tertiary = Color(0xFF64748B),
    background = Color(0xFF121416), // Titanium Slate Dark
    surface = Color(0xFF1E2126),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFFFFFFF)
)

@Composable
fun MyApplicationTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.DARK,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeStyle) {
        AppThemeStyle.DARK -> DarkColorScheme
        AppThemeStyle.GRAY -> GrayColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
