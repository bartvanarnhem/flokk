package com.flokk.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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
    Column(modifier = Modifier.fillMaxWidth().background(Tui.keycapBackground)) {
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
        HorizontalDivider(color = Tui.border, thickness = 0.5.dp)
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
            HKey("|", Modifier.weight(1f)) { session?.sendText("|") }
            HKey("PGDN", Modifier.weight(1f)) { session?.write(TermKeys.PAGE_DOWN, 0, TermKeys.PAGE_DOWN.size) }
            // The counterpart to long-press-drag copying on the canvas: the system keyboard's
            // own paste only reaches views that hold a text selection, which a terminal canvas
            // never does, so pasting needs a key of its own.
            HKey("PASTE", Modifier.weight(1f)) {
                clipboard.getText()?.text?.let { session?.paste(it) }
            }
        }
    }
}

@Composable
private fun HKey(
    label: String,
    modifier: Modifier = Modifier,
    armed: Boolean = false,
    onClick: () -> Unit,
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
            .height(36.dp)
            .padding(1.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = Tui.label.copy(color = if (armed) Tui.accentBlue else Tui.textDim, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}
