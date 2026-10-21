package com.flokk.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flokk.app.terminal.RemoteTerminalSession
import com.flokk.app.terminal.TermKeys
import com.flokk.app.ui.theme.Flokk

/**
 * Termux's own persistent extra-keys row: ESC/arrows/HOME-END/PGUP-PGDN plus CTRL/ALT as
 * "sticky" modifiers (tap to arm, the next real keystroke — typed on the system keyboard
 * below — goes through it). This is the one piece with no system-widget equivalent, so it's
 * the one place left custom-drawn; everything else defers to the real keyboard.
 *
 * Laid out as one grid of nine equal columns shared by both rows (↑ directly above ↓, PGUP
 * above PGDN, ...), the last holding attach over paste.
 */
@Composable
fun TermuxModifierRow(session: RemoteTerminalSession?, onAttach: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Flokk.background)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HKey("ESC") { session?.write(TermKeys.ESC, 0, TermKeys.ESC.size) }
            HKey("/") { session?.sendText("/") }
            HKey("-") { session?.sendText("-") }
            HKey("HOME") { session?.write(TermKeys.HOME, 0, TermKeys.HOME.size) }
            HKey("↑") { session?.write(TermKeys.UP, 0, TermKeys.UP.size) }
            HKey("END") { session?.write(TermKeys.END, 0, TermKeys.END.size) }
            HKey("PGUP") { session?.write(TermKeys.PAGE_UP, 0, TermKeys.PAGE_UP.size) }
            HKey("~") { session?.sendText("~") }
            // Uploads a file to the host and drops its path into the prompt (see
            // [RemoteTerminalSession.attachFile]).
            IconKey(Icons.Rounded.AttachFile, "Attach file", onClick = onAttach)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HKey("TAB") { session?.write(TermKeys.TAB, 0, TermKeys.TAB.size) }
            HKey("CTRL", armed = session?.ctrlArmed == true) {
                session?.let { it.ctrlArmed = !it.ctrlArmed; it.altArmed = false }
            }
            HKey("ALT", armed = session?.altArmed == true) {
                session?.let { it.altArmed = !it.altArmed; it.ctrlArmed = false }
            }
            HKey("←") { session?.write(TermKeys.LEFT, 0, TermKeys.LEFT.size) }
            HKey("↓") { session?.write(TermKeys.DOWN, 0, TermKeys.DOWN.size) }
            HKey("→") { session?.write(TermKeys.RIGHT, 0, TermKeys.RIGHT.size) }
            HKey("PGDN") { session?.write(TermKeys.PAGE_DOWN, 0, TermKeys.PAGE_DOWN.size) }
            HKey("|") { session?.sendText("|") }
            // The counterpart to long-press-drag copying on the canvas: the system keyboard's
            // own paste only reaches views that hold a text selection, which a terminal canvas
            // never does, so pasting needs a key of its own.
            IconKey(Icons.Rounded.ContentPaste, "Paste") {
                clipboard.getText()?.text?.let { session?.paste(it) }
            }
        }
    }
}

@Composable
private fun RowScope.HKey(
    label: String,
    armed: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .background(if (armed || pressed) Flokk.raised else Flokk.card, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Flokk.sans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = if (armed) Flokk.accent else Flokk.textSecondary,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun RowScope.IconKey(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .weight(1f)
            .height(30.dp)
            .background(if (pressed) Flokk.raised else Flokk.card, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Flokk.textSecondary, modifier = Modifier.size(20.dp))
    }
}
