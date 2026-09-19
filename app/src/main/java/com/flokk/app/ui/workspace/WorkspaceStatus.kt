package com.flokk.app.ui.workspace

import androidx.compose.ui.graphics.Color
import com.flokk.app.herdr.AgentState
import com.flokk.app.ui.theme.Tui

/** The label a bracket status tag shows, e.g. `[needs input]`. */
fun AgentState?.statusLabel(): String = when (this) {
    AgentState.BLOCKED -> "needs input"
    AgentState.WORKING -> "running"
    AgentState.DONE -> "done"
    AgentState.IDLE -> "idle"
    else -> "unknown"
}

fun AgentState?.color(): Color = when (this) {
    AgentState.BLOCKED -> Tui.accentYellow
    AgentState.WORKING -> Tui.accentBlue
    AgentState.DONE -> Tui.accentGreen
    AgentState.IDLE -> Tui.textFaint
    else -> Tui.textFaint
}

/** Formats a device-local timestamp as a short relative duration, e.g. "4m", "1h12m". */
fun relativeTimeShort(sinceMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val elapsedMin = ((nowMillis - sinceMillis) / 60_000).coerceAtLeast(0)
    return when {
        elapsedMin < 1 -> "just now"
        elapsedMin < 60 -> "${elapsedMin}m"
        else -> "${elapsedMin / 60}h${elapsedMin % 60}m"
    }
}
