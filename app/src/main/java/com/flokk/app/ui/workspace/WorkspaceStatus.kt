package com.flokk.app.ui.workspace

import androidx.compose.ui.graphics.Color
import com.flokk.app.herdr.AgentState
import com.flokk.app.ui.theme.Flokk

/** The agent state as the workspace header spells it out, e.g. "needs input". */
fun AgentState?.statusLabel(): String = when (this) {
    AgentState.BLOCKED -> "needs input"
    AgentState.WORKING -> "running"
    AgentState.DONE -> "done"
    AgentState.IDLE -> "idle"
    else -> "status unknown"
}

/** The header's status dot: the exact state, with idle and unknown both a quiet grey. */
fun AgentState?.color(): Color = when (this) {
    AgentState.BLOCKED -> Flokk.accent
    AgentState.WORKING, AgentState.DONE -> Flokk.green
    else -> Flokk.dotUnknown
}

/** Formats a device-local timestamp as a short relative duration, e.g. "12 m", "181 h". */
fun relativeTimeShort(sinceMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val elapsedMin = ((nowMillis - sinceMillis) / 60_000).coerceAtLeast(0)
    return when {
        elapsedMin < 1 -> "just now"
        elapsedMin < 60 -> "$elapsedMin m"
        else -> "${elapsedMin / 60} h"
    }
}
