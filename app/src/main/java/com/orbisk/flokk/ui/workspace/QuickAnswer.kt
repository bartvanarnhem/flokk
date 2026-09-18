package com.orbisk.flokk.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbisk.flokk.terminal.RemoteTerminalSession
import com.orbisk.flokk.ui.theme.Tui
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * When the live terminal screen shows a recognizable question — a numbered menu, "(Y)es/(N)o"
 * shortcuts, a bare "(y/n)" — surfaces it as real tappable buttons right above the keyboard,
 * so answering never requires the keyboard at all. Agent-agnostic: it reacts to whatever
 * text is actually on screen (see [detectPrompt]), not any one CLI's own status field, so it
 * works the same for Claude, Cursor, or anything else printing a normal prompt.
 */
@Composable
fun QuickAnswerRow(session: RemoteTerminalSession?) {
    var prompt by remember(session) { mutableStateOf<DetectedPrompt?>(null) }

    LaunchedEffect(session) {
        if (session == null) {
            prompt = null
            return@LaunchedEffect
        }
        while (isActive) {
            prompt = session.withEmulator { it.currentScreenLines() }?.let(::detectPrompt)
            delay(300)
        }
    }

    val current = prompt ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Tui.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        current.choices.forEachIndexed { index, choice ->
            QuickAnswerButton(
                choice = choice,
                accent = if (index == 0) Tui.accentGreen else Tui.accentBlue,
                modifier = Modifier.weight(1f),
                onClick = { session?.sendText(choice.send) },
            )
        }
    }
}

@Composable
private fun QuickAnswerButton(
    choice: DetectedChoice,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(40.dp)
            .background(if (pressed) accent.copy(alpha = 0.25f) else Color.Transparent, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, accent), RoundedCornerShape(6.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "[${choice.send}] ${choice.label}",
            style = Tui.label.copy(color = accent, fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
