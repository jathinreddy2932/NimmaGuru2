package com.example.nimmaguru.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = HeritageTeal,
    onPrimary = Color.White,
    primaryContainer = HeritageSoftTeal,
    onPrimaryContainer = HeritageTeal,
    secondary = HeritageTeal,
    onSecondary = Color.White,
    secondaryContainer = HeritageSoftTeal,
    onSecondaryContainer = HeritageTeal,
    background = HeritageIvory,
    onBackground = HeritageCharcoal,
    surface = Color.White, // Main background is Ivory, cards will use SoftTeal or White with shadows
    onSurface = HeritageCharcoal,
    surfaceVariant = HeritageSoftTeal, // Used for softer sections
    onSurfaceVariant = HeritageTeal,
    outline = HeritageTeal,
    tertiary = HeritageGold,
    onTertiary = Color.White
)


private val DarkColorScheme = darkColorScheme(
    primary = HeritageSoftTeal,
    onPrimary = HeritageTeal,
    primaryContainer = HeritageTeal,
    onPrimaryContainer = HeritageSoftTeal,
    secondary = HeritageSoftTeal,
    onSecondary = HeritageTeal,
    secondaryContainer = HeritageTeal,
    onSecondaryContainer = HeritageSoftTeal,
    background = HeritageCharcoal,
    onBackground = HeritageIvory,
    surface = HeritageTeal,
    onSurface = HeritageSoftTeal,
    surfaceVariant = HeritageCharcoal,
    onSurfaceVariant = HeritageIvory,
    outline = HeritageSoftTeal
)

@Composable
fun NimmaGuruTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}