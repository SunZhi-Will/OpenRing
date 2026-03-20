package com.openring.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Primary palette - 主色調
private val Primary = Color(0xFF1976D2)
private val Secondary = Color(0xFF03DAC6)
private val Error = Color(0xFFB00020)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    error = Error,
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF2D2D2D)
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F2FD),
    secondary = Secondary,
    error = Error,
    surface = Color(0xFFFAFAFA),
    surfaceVariant = Color(0xFFF5F5F5)
)

@Composable
fun OpenRingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
