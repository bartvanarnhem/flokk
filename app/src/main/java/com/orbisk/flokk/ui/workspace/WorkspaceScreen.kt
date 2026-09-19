package com.orbisk.flokk.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.orbisk.flokk.herdr.AgentState
import com.orbisk.flokk.herdr.RepoState
import com.orbisk.flokk.herdr.WorkspaceOverview
import com.orbisk.flokk.terminal.TermKeys
import com.orbisk.flokk.terminal.TerminalCanvasView
import com.orbisk.flokk.terminal.TerminalInputView
import com.orbisk.flokk.ui.LocalFlokk
import com.orbisk.flokk.ui.theme.Tui
import com.orbisk.flokk.ui.theme.TuiConfirmDialog
import com.orbisk.flokk.ui.theme.TuiIconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(onBack: () -> Unit) {
    val app = LocalFlokk.current
    val scope = rememberCoroutineScope()
    val repoState by app.repository.state.collectAsState()
    val connectionGeneration by app.repository.connectionGeneration.collectAsState()
    val connected = repoState as? RepoState.Connected
    val workspaces = connected?.workspaces.orEmpty()

    // Nothing is selected until the user picks a tab; until then we follow the first workspace
    // the poll reports, which is also what makes this screen land on something right after connect.
    var selectedWorkspaceId by remember { mutableStateOf<String?>(null) }
    val currentWorkspaceId = selectedWorkspaceId ?: workspaces.firstOrNull()?.workspace?.id
    var fontSize by remember { mutableFloatStateOf(13f) }

    val sessions = rememberWorkspaceSessions(app.repository, currentWorkspaceId, connectionGeneration)
    val session = sessions.get(currentWorkspaceId)

    var inputView by remember { mutableStateOf<TerminalInputView?>(null) }
    val keyboardVisible = WindowInsets.isImeVisible
    var pendingClose by remember { mutableStateOf<WorkspaceOverview?>(null) }

    // Visible by default, as soon as a pane is attached — matching a real terminal app.
    LaunchedEffect(session, inputView) {
        if (session != null) inputView?.showKeyboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tui.background)
            .imePadding(),
    ) {
        val current = workspaces.firstOrNull { it.workspace.id == currentWorkspaceId }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            Text(text = clock.format(System.currentTimeMillis()), style = Tui.label.copy(color = Tui.textFaint))
            Text(
                text = listOfNotNull(
                    app.repository.currentProfile?.displayName,
                    connected?.lastPollMs?.let { "${it}ms" },
                ).joinToString(" · "),
                style = Tui.label.copy(color = Tui.textFaint),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TuiIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to connections", onClick = onBack)
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row {
                        Text(
                            text = current?.workspace?.label ?: "no workspace",
                            style = Tui.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = " [${current?.agent?.state.statusLabel()}]",
                            style = Tui.label.copy(color = current?.agent?.state.color()),
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            current?.agent?.kind ?: "shell",
                            current?.workspace?.cwd,
                        ).joinToString(" · "),
                        style = Tui.label.copy(color = Tui.textFaint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (connected?.reconnecting == true) {
                CircularProgressIndicator(
                    color = Tui.accentBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                TuiIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Force reconnect",
                    tint = Tui.textDim,
                    onClick = { scope.launch { app.repository.reconnect() } },
                )
            }
            TuiIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "New workspace",
                tint = Tui.textDim,
                onClick = {
                    scope.launch {
                        app.repository.createWorkspace().onSuccess { selectedWorkspaceId = it }
                    }
                },
            )
            TuiIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close workspace",
                tint = if (current != null) Tui.textDim else Tui.textFaint,
                onClick = { current?.let { pendingClose = it } },
            )
            TuiIconButton(
                icon = if (keyboardVisible) Icons.Filled.KeyboardHide else Icons.Filled.Keyboard,
                contentDescription = if (keyboardVisible) "Hide keyboard" else "Show keyboard",
                tint = if (keyboardVisible) Tui.accentBlue else Tui.textDim,
                onClick = { if (keyboardVisible) inputView?.hideKeyboard() else inputView?.showKeyboard() },
            )
        }

        WorkspaceTabsRow(
            workspaces = workspaces,
            currentId = currentWorkspaceId,
            onSelect = { selectedWorkspaceId = it },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Tui.background)
                .pointerInput(Unit) {
                    // Two-finger only: `detectTransformGestures` reacts to single-finger drags
                    // too (pan counts as a "gesture" there), which raced the terminal canvas's
                    // own tap/long-press/scroll recognizer for the same touches underneath —
                    // both watch the same touch-slop threshold on the same finger, so this
                    // outer detector could consume a touch mid-recognition and break a tap or
                    // a long-press-to-select before the canvas ever decided what it was.
                    detectPinchZoom { zoom ->
                        fontSize = (fontSize * zoom).coerceIn(8f, 30f)
                    }
                },
        ) {
            val activeSession = session
            if (activeSession == null) {
                Text(
                    text = when {
                        repoState is RepoState.Connecting -> "connecting..."
                        currentWorkspaceId == null -> connected?.staleError ?: "no active workspaces"
                        else -> "attaching..."
                    },
                    style = Tui.label,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                )
            } else {
                TerminalCanvasView(
                    session = activeSession,
                    fontSizeSp = fontSize,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Invisible: exists only to hold IME focus and receive committed text/backspaces from
        // the system keyboard, translated to bytes and fed to the PTY. Using the real system
        // keyboard (not a custom-drawn one) means autocorrect-off ASCII input, held-key
        // repeat, swipe typing etc. all keep working exactly as everywhere else on the device.
        // A raw View with its own InputConnection (see TerminalInputView), not a Compose
        // BasicTextField faking an always-empty field: that approach works, but resetting a
        // backing text buffer on every single keystroke makes the IME resync its view of the
        // field's contents that same number of times, which is exactly the kind of per-key
        // overhead a real terminal app's input path has none of.
        AndroidView(
            factory = { ctx -> TerminalInputView(ctx) },
            update = { view ->
                inputView = view
                view.onText = { text -> session?.sendText(text.replace("\n", "\r")) }
                view.onBackspace = { session?.write(TermKeys.BACKSPACE, 0, TermKeys.BACKSPACE.size) }
                view.onEnter = { session?.write(TermKeys.ENTER, 0, TermKeys.ENTER.size) }
            },
            modifier = Modifier.size(1.dp),
        )

        QuickAnswerRow(session = session)
        TermuxModifierRow(session = session)
    }

    pendingClose?.let { toClose ->
        TuiConfirmDialog(
            title = "Close workspace?",
            text = "“${toClose.workspace.label}” will be closed on the host.",
            confirmLabel = "Close",
            onConfirm = {
                val id = toClose.workspace.id
                pendingClose = null
                if (selectedWorkspaceId == id) selectedWorkspaceId = null
                sessions.remove(id)
                scope.launch { app.repository.closeWorkspace(id) }
            },
            onDismiss = { pendingClose = null },
        )
    }
}

/** The braille "dots" spinner CLIs use while something is running. */
private const val SPINNER_FRAMES = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
private const val SPINNER_FRAME_MS = 90L

/**
 * The tab strip's at-a-glance state: green while the agent works, orange while it is waiting
 * on an answer, and grey whenever nothing wants attention. Deliberately coarser than
 * [color] — which still labels the exact state in the header — because a strip of dots is
 * only useful if "needs me" reads differently from "doesn't" across the room.
 */
private fun AgentState?.indicatorColor(): Color = when (this) {
    AgentState.WORKING, AgentState.DONE -> Tui.accentGreen
    AgentState.BLOCKED -> Tui.accentOrange
    else -> Tui.textFaint
}

/** A horizontal strip of workspace tabs, replacing individual tiles once zoomed into one. */
@Composable
private fun WorkspaceTabsRow(
    workspaces: List<WorkspaceOverview>,
    currentId: String?,
    onSelect: (String) -> Unit,
) {
    // One tick for the whole row, so every running workspace spins in step, and none of them
    // spin — or recompose — while nothing is running.
    val anyWorking = workspaces.any { it.agent?.state == AgentState.WORKING }
    var spinnerFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(anyWorking) {
        if (!anyWorking) return@LaunchedEffect
        while (isActive) {
            delay(SPINNER_FRAME_MS)
            spinnerFrame = (spinnerFrame + 1) % SPINNER_FRAMES.length
        }
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(workspaces, key = { it.workspace.id }) { overview ->
            val isCurrent = overview.workspace.id == currentId
            val state = overview.agent?.state
            val statusColor = state.indicatorColor()
            // Which tab you're looking at and what its agent is doing are two different things,
            // so they get two different channels: the yellow frame flokk's own TUI uses for the
            // focused element marks the current tab, and the dot alone carries agent state.
            val borderColor = if (isCurrent) Tui.accentYellow else Tui.border
            val fill = if (isCurrent) Tui.accentYellow.copy(alpha = 0.1f) else Color.Transparent
            Column(
                modifier = Modifier
                    .background(fill, RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(overview.workspace.id) },
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = overview.workspace.label,
                        style = Tui.label.copy(color = if (isCurrent) Tui.text else Tui.textDim),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        state == AgentState.WORKING -> Text(
                            text = " ${SPINNER_FRAMES[spinnerFrame]}",
                            style = Tui.label.copy(color = statusColor),
                        )
                        state == AgentState.BLOCKED -> Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Needs input",
                            tint = statusColor,
                            modifier = Modifier.padding(start = 4.dp).size(12.dp),
                        )
                        else -> Text(text = " ●", style = Tui.label.copy(color = statusColor))
                    }
                }
                Text(
                    text = overview.agent?.kind ?: "shell",
                    style = Tui.label.copy(
                        color = if (isCurrent) Tui.textDim else Tui.textFaint,
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Pinch-to-zoom, and nothing else: a single finger is left completely alone (no consumption,
 * no callback), unlike `detectTransformGestures` which treats a one-finger drag as a "pan"
 * gesture too. That mattered here because this sits in a `Box` directly over the terminal
 * canvas's own tap/long-press/scroll recognizer — both watch the same touch-slop distance on
 * the same finger, and consuming as this one did was enough to break a tap or a long-press
 * mid-recognition before the canvas underneath ever got to decide what the touch was.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPinchZoom(
    onZoom: (Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.size >= 2) {
                val zoomChange = event.calculateZoom()
                if (zoomChange != 1f) {
                    onZoom(zoomChange)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
