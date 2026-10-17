package com.flokk.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flokk.app.terminal.RemoteTerminalSession
import com.flokk.app.terminal.TermKeys
import com.flokk.app.ui.theme.Tui

/**
 * Termux's own persistent extra-keys row: ESC/arrows/HOME-END/PGUP-PGDN plus CTRL/ALT as
 * "sticky" modifiers (tap to arm, the next real keystroke — typed on the system keyboard
 * below — goes through it). This is the one piece with no system-widget equivalent, so it's
 * the one place left custom-drawn; everything else defers to the real keyboard.
 */
@Composable
fun TermuxModifierRow(session: RemoteTerminalSession?) {
    val clipboard = LocalClipboardManager.current
    // Laid out like Termux's own bar: both rows share one column grid (↑ directly above ↓,
    // PGUP above PGDN, ...) so every key has an obvious column, with no divider between rows.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Tui.background)
            .padding(vertical = 1.dp),
    ) {
        Column(Modifier.weight(8f)) {
            Row(Modifier.fillMaxWidth()) {
                HKey("ESC", Modifier.weight(1f)) { session?.write(TermKeys.ESC, 0, TermKeys.ESC.size) }
                HKey("/", Modifier.weight(1f)) { session?.sendText("/") }
                HKey("-", Modifier.weight(1f)) { session?.sendText("-") }
                HKey("HOME", Modifier.weight(1f)) { session?.write(TermKeys.HOME, 0, TermKeys.HOME.size) }
                HKey("↑", Modifier.weight(1f)) { session?.write(TermKeys.UP, 0, TermKeys.UP.size) }
                HKey("END", Modifier.weight(1f)) { session?.write(TermKeys.END, 0, TermKeys.END.size) }
                HKey("PGUP", Modifier.weight(1f)) { session?.write(TermKeys.PAGE_UP, 0, TermKeys.PAGE_UP.size) }
                HKey("~", Modifier.weight(1f)) { session?.sendText("~") }
            }
            Row(Modifier.fillMaxWidth()) {
                HKey("TAB", Modifier.weight(1f)) { session?.write(TermKeys.TAB, 0, TermKeys.TAB.size) }
                HKey("CTRL", Modifier.weight(1f), armed = session?.ctrlArmed == true) {
                    session?.let { it.ctrlArmed = !it.ctrlArmed; it.altArmed = false }
                }
                HKey("ALT", Modifier.weight(1f), armed = session?.altArmed == true) {
                    session?.let { it.altArmed = !it.altArmed; it.ctrlArmed = false }
                }
                HKey("←", Modifier.weight(1f)) { session?.write(TermKeys.LEFT, 0, TermKeys.LEFT.size) }
                HKey("↓", Modifier.weight(1f)) { session?.write(TermKeys.DOWN, 0, TermKeys.DOWN.size) }
                HKey("→", Modifier.weight(1f)) { session?.write(TermKeys.RIGHT, 0, TermKeys.RIGHT.size) }
                HKey("PGDN", Modifier.weight(1f)) { session?.write(TermKeys.PAGE_DOWN, 0, TermKeys.PAGE_DOWN.size) }
                HKey("|", Modifier.weight(1f)) { session?.sendText("|") }
            }
        }
        // The counterpart to long-press-drag copying on the canvas: the system keyboard's
        // own paste only reaches views that hold a text selection, which a terminal canvas
        // never does, so pasting needs a key of its own. Spans both rows so the 8-column
        // grid to its left stays aligned.
        KeyBox(Modifier.weight(1f).fillMaxHeight(), onClick = {
            clipboard.getText()?.text?.let { session?.paste(it) }
        }) {
            Icon(
                imageVector = Icons.Outlined.ContentPaste,
                contentDescription = "Paste",
                tint = KeyLabelColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val KeyLabelColor = Color(0xFFF2F2F2)

@Composable
private fun HKey(
    label: String,
    modifier: Modifier = Modifier,
    armed: Boolean = false,
    onClick: () -> Unit,
) {
    KeyBox(modifier.height(34.dp), armed = armed, onClick = onClick) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 0.3.sp,
                color = if (armed) Tui.accentBlue else KeyLabelColor,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun KeyBox(
    modifier: Modifier,
    armed: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bg = when {
        armed -> Tui.accentBlue.copy(alpha = 0.3f)
        pressed -> Tui.border
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .padding(1.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
