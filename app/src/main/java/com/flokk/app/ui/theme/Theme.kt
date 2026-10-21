package com.flokk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.flokk.app.R

/**
 * flokk's visual language, the "Auto Minimal" style: warm dark cards on a near-black page,
 * Instrument Sans for the chrome, IBM Plex Mono for addresses and keys, one amber accent and
 * a light-filled segmented control. The terminal itself keeps its own pure-black canvas and
 * font; only the app around it follows this. Always dark.
 */
object Flokk {
    val background = Color(0xFF0F0F0E)
    val terminalBackground = Color(0xFF000000)
    /** Cards, inputs and keycaps. */
    val card = Color(0xFF1B1A18)
    /** A step up from [card]: active tabs and icon buttons, nested buttons, info boxes. */
    val raised = Color(0xFF262522)

    val border = Color(0xFF3A3935)
    val divider = Color(0xFF1F1E1C)
    val dividerOnCard = Color(0xFF2A2926)

    val text = Color(0xFFEDEAE3)
    val textSecondary = Color(0xFFBDB8AE)
    val textMuted = Color(0xFF9A968D)
    val textFaint = Color(0xFF8A867E)
    val dotUnknown = Color(0xFF6E6A63)
    val dim = Color(0xFF55524C)

    val accent = Color(0xFFE8B27A)
    val onAccent = Color(0xFF1B1A18)
    val green = Color(0xFF9FC2A8)
    val greenBackground = Color(0xFF26332B)
    val red = Color(0xFFF08C78)
    val redBackground = Color(0xFF3A2420)
    /** Links and attachment spans drawn inside the terminal, unchanged from before. */
    val terminalLink = Color(0xFF85B7EB)

    /** The light fill of a selected segment, with [background] text on it. */
    val selectedFill = text

    val sans = FontFamily(
        Font(R.font.instrument_sans_regular, FontWeight.Normal),
        Font(R.font.instrument_sans_medium, FontWeight.Medium),
        Font(R.font.instrument_sans_semibold, FontWeight.SemiBold),
    )
    val mono = FontFamily(
        Font(R.font.plex_mono_regular, FontWeight.Normal),
        Font(R.font.plex_mono_medium, FontWeight.Medium),
    )

    val screenTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 20.sp, color = text)
    val cardTitle = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, color = text)
    val body = TextStyle(fontFamily = sans, fontSize = 15.sp, color = text)
    val bodyMedium = body.copy(fontWeight = FontWeight.Medium)
    val small = TextStyle(fontFamily = sans, fontSize = 13.sp, lineHeight = 18.sp, color = textMuted)
    val button = TextStyle(fontFamily = sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = text)
    val fieldLabel = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = textMuted)
    val input = TextStyle(fontFamily = sans, fontSize = 16.sp, color = text)
    val monoSmall = TextStyle(fontFamily = mono, fontSize = 12.5.sp, color = textMuted)
}

private val flokkScheme = darkColorScheme(
    background = Flokk.background,
    surface = Flokk.card,
    surfaceContainer = Flokk.card,
    primary = Flokk.accent,
    onPrimary = Flokk.onAccent,
    secondary = Flokk.green,
    error = Flokk.red,
    onBackground = Flokk.text,
    onSurface = Flokk.text,
)

/** The app's one theme, applied once at the root in `MainActivity`. */
@Composable
fun FlokkTheme(content: @Composable () -> Unit) {
    val base = MaterialTheme.typography
    MaterialTheme(
        colorScheme = flokkScheme,
        typography = base.copy(
            bodyLarge = base.bodyLarge.copy(fontFamily = Flokk.sans),
            bodyMedium = base.bodyMedium.copy(fontFamily = Flokk.sans),
            labelLarge = base.labelLarge.copy(fontFamily = Flokk.sans),
            labelSmall = base.labelSmall.copy(fontFamily = Flokk.sans),
        ),
        content = content,
    )
}
