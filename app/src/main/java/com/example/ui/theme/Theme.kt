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

import com.example.ui.AppThemeMode
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

private val TitaniumColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    secondary = Color(0xFF475569),
    tertiary = Color(0xFF0F172A),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    onBackground = Color(0xFF000000),
    onSurface = Color(0xFF000000)
)

@Composable
fun MyApplicationTheme(
    themeMode: AppThemeMode = AppThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val isSystemDark = isSystemInDarkTheme()
    val resolved = when (themeMode) {
        AppThemeMode.AUTO -> {
            if (isSystemDark) AppThemeMode.DARK else AppThemeMode.LIGHT
        }
        else -> themeMode
    }
    
    val colorScheme = when (resolved) {
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.TITANIUM -> TitaniumColorScheme
        else -> DarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
