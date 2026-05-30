package com.ufi.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF191926)
val DarkSurface = Color(0xFF262636)
val DarkSurfaceVariant = Color(0xFF1E1E2C)
val DarkBorder = Color(0xFF36364A)
val Accent = Color(0xFF6C5CE7)
val AccentHover = Color(0xFF7D6FF0)
val Success = Color(0xFF00B894)
val ErrorRed = Color(0xFFFF6B6B)
val TextPrimary = Color(0xFFEDEDF5)
val TextSecondary = Color(0xFFB8B8CC)
val TextMuted = Color(0xFF808098)

private val UfiDarkColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = AccentHover,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed,
    onError = Color.White,
    outline = DarkBorder,
)

@Composable
fun UfiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UfiDarkColorScheme,
        content = content,
    )
}
