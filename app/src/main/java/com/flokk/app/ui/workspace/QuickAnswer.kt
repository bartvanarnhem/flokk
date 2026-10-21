package com.flokk.app.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flokk.app.terminal.RemoteTerminalSession
import com.flokk.app.ui.theme.Flokk
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
            .background(Flokk.background)
            .padding(start = 6.dp, end = 6.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        current.choices.forEachIndexed { index, choice ->
            QuickAnswerButton(
                choice = choice,
                primary = index == 0,
                modifier = Modifier.weight(1f),
                onClick = { session?.sendText(choice.send) },
            )
        }
    }
}

/** The first choice (usually the affirmative) is tinted green; the rest are plain cards. */
@Composable
private fun QuickAnswerButton(
    choice: DetectedChoice,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (primary) Flokk.greenBackground else Flokk.card
    val content = if (primary) Flokk.green else Flokk.text
    Row(
        modifier = modifier
            .height(36.dp)
            .background(if (pressed) Flokk.raised else background, RoundedCornerShape(8.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = choice.send,
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = if (primary) Flokk.background else Flokk.text),
            maxLines = 1,
            modifier = Modifier
                .background(if (primary) Flokk.green else Flokk.border, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        Text(
            text = choice.label,
            style = Flokk.bodyMedium.copy(color = content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
