package com.flokk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/**
 * flokk's one and only visual language: a warm-dark, monospace, terminal-native look
 * (bracketed status tags, hairline borders, no elevation/ripple) used across the whole
 * app — this is a terminal client, not a document editor, so there's no separate "app
 * chrome" Material theme and no light mode.
 */
object Tui {
    val background = Color(0xFF000000)
    val surface = Color(0xFF191917)
    val inputBackground = Color(0xFF111110)
    val keycapBackground = Color(0xFF232320)

    val border = Color(0xFF33332F)
    val borderStrong = Color(0xFF444441)
    val borderFocused = Color(0xFF85B7EB)

    val text = Color(0xFFD3D1C7)
    val textDim = Color(0xFF888780)
    val textFaint = Color(0xFF5F5E5A)

    val accentGreen = Color(0xFF5DCAA5)
    val accentYellow = Color(0xFFFAC775)
    val accentOrange = Color(0xFFF0A05A)
    val accentRed = Color(0xFFF09595)
    val accentBlue = Color(0xFF85B7EB)
    val accentPurple = Color(0xFFAFA9EC)

    val mono = FontFamily.Monospace

    val body = TextStyle(fontFamily = mono, fontSize = 13.sp, color = text)
    val label = TextStyle(fontFamily = mono, fontSize = 11.sp, color = textDim)
    val title = TextStyle(fontFamily = mono, fontSize = 14.sp, color = text)
}

private val tuiScheme = darkColorScheme(
    background = Tui.background,
    surface = Tui.surface,
    primary = Tui.accentBlue,
    secondary = Tui.accentPurple,
    error = Tui.accentRed,
    onBackground = Tui.text,
    onSurface = Tui.text,
    onPrimary = Tui.background,
)

/** The app's one theme, applied once at the root in `MainActivity`. Always dark. */
@Composable
fun FlokkTuiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = tuiScheme,
        typography = MaterialTheme.typography.copy(
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = Tui.mono),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = Tui.mono),
            labelSmall = MaterialTheme.typography.labelSmall.copy(fontFamily = Tui.mono),
        ),
        content = content,
    )
}
