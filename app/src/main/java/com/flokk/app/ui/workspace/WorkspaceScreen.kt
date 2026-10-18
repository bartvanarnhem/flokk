package com.flokk.app.ui.workspace

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.flokk.app.data.TabLayout
import com.flokk.app.herdr.AgentState
import com.flokk.app.herdr.HerdrRepository
import com.flokk.app.herdr.HostWorkspace
import com.flokk.app.herdr.RepoState
import com.flokk.app.terminal.TermKeys
import com.flokk.app.terminal.TerminalCanvasView
import com.flokk.app.terminal.TerminalInputView
import com.flokk.app.ui.LocalFlokk
import com.flokk.app.ui.theme.Tui
import com.flokk.app.ui.theme.TuiConfirmDialog
import com.flokk.app.ui.theme.TuiIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream

/** Everything read off every connected host's own [HerdrRepository.state] in one pass, so
 *  the tab strip and the per-host status line below both drive off the same subscriptions
 *  instead of each walking [repositories] again. */
private class Aggregate(
    val items: List<HostWorkspace>,
    val hostStates: Map<String, RepoState.Connected>,
    val reconnecting: Map<String, Boolean>,
)

/** Subscribes to every connected host's state, `key`-scoped per profile id so a host that
 *  disconnects mid-composition cleanly drops its own subscription instead of the whole set
 *  restarting. */
@Composable
private fun rememberAggregate(repositories: Map<String, HerdrRepository>): Aggregate {
    val items = mutableListOf<HostWorkspace>()
    val hostStates = mutableMapOf<String, RepoState.Connected>()
    val reconnecting = mutableMapOf<String, Boolean>()
    for ((profileId, repo) in repositories) {
        key(profileId) {
            val state by repo.state.collectAsState()
            val connected = state as? RepoState.Connected
            val profile = repo.currentProfile
            if (connected != null && profile != null) {
                hostStates[profileId] = connected
                reconnecting[profileId] = connected.reconnecting
                for (overview in connected.workspaces) items.add(HostWorkspace(profile, overview))
            }
        }
    }
    return Aggregate(items, hostStates, reconnecting)
}

/** One [WorkspaceSessionCache] per connected host, each still keyed on that host's own
 *  connection generation exactly as the single-host version was — a host's own reconnect
 *  must only kill that host's sessions, never another connected host's. */
@Composable
private fun rememberAllSessions(
    repositories: Map<String, HerdrRepository>,
    currentKey: String?,
): Map<String, WorkspaceSessionCache> {
    val (currentProfileId, currentWorkspaceId) = splitKey(currentKey)
    val result = mutableMapOf<String, WorkspaceSessionCache>()
    for ((profileId, repo) in repositories) {
        key(profileId) {
            val generation by repo.connectionGeneration.collectAsState()
            val rawId = currentWorkspaceId.takeIf { profileId == currentProfileId }
            result[profileId] = rememberWorkspaceSessions(repo, rawId, generation)
        }
    }
    return result
}

private fun splitKey(key: String?): Pair<String?, String?> {
    if (key == null) return null to null
    val idx = key.indexOf(':')
    if (idx < 0) return null to null
    return key.substring(0, idx) to key.substring(idx + 1)
}

private fun Map<String, WorkspaceSessionCache>.sessionFor(key: String?) =
    splitKey(key).let { (profileId, workspaceId) -> profileId?.let { this[it] }?.get(workspaceId) }

private fun Map<String, WorkspaceSessionCache>.removeSession(key: String) {
    val (profileId, workspaceId) = splitKey(key)
    if (profileId != null && workspaceId != null) this[profileId]?.remove(workspaceId)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceScreen(onBack: () -> Unit) {
    val app = LocalFlokk.current
    val scope = rememberCoroutineScope()
    val repositories by app.connectionManager.repositories.collectAsState()
    val aggregate = rememberAggregate(repositories)

    // Most-recently-selected first: with several hosts connected, the tab strip can span more
    // workspaces than fit on screen, so the ones you're actually moving between stay up front
    // instead of scattered wherever their host happened to report them. Persisted so the order
    // (and, via `currentKey` below, which tab you land on) survives an app restart instead of
    // resetting to whatever each host happens to report first.
    var recentKeys by remember { mutableStateOf(app.workspaceOrderStore.load()) }
    val workspaces = remember(aggregate.items, recentKeys) {
        val rank = recentKeys.withIndex().associate { (i, k) -> k to i }
        aggregate.items.sortedBy { rank[it.key] ?: Int.MAX_VALUE }
    }

    // Nothing is selected until the user picks a tab; until then we follow the most recently
    // used one (the front of the now-sorted list above), which is also what makes this screen
    // land on something right after the first connect.
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val currentKey = selectedKey ?: workspaces.firstOrNull()?.key
    val settings by app.settingsStore.settings.collectAsState()
    // Pinch-to-zoom adjusts freely from here; picking a preset in settings resets to it.
    var fontSize by remember(settings.fontSize) { mutableFloatStateOf(settings.fontSize.sp) }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(currentKey) {
        val key = currentKey ?: return@LaunchedEffect
        val reordered = listOf(key) + recentKeys.filterNot { it == key }
        recentKeys = reordered
        app.workspaceOrderStore.save(reordered)
    }

    val allSessions = rememberAllSessions(repositories, currentKey)
    val session = allSessions.sessionFor(currentKey)

    var inputView by remember { mutableStateOf<TerminalInputView?>(null) }
    val keyboardVisible = WindowInsets.isImeVisible
    var pendingClose by remember { mutableStateOf<HostWorkspace?>(null) }
    var newWorkspaceMenuOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val uploads = remember { mutableStateListOf<UploadProgress>() }
    val pickAttachment = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val target = session
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        val (name, size) = context.describe(uri)
        val upload = UploadProgress(name, size)
        uploads.add(upload)
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri) ?: error("can't read file")
                    CountingInputStream(input) { upload.sent = it }.use { target.attachFile(name, it) }
                }
            }.onFailure {
                Toast.makeText(context, "Attach failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
            uploads.remove(upload)
        }
    }

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
        val current = workspaces.firstOrNull { it.key == currentKey }
        val currentRepo = current?.profile?.id?.let { repositories[it] }

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
                            text = current?.let { "${it.profile.displayName} · ${it.overview.workspace.label}" }
                                ?: "no workspace",
                            style = Tui.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = " [${current?.overview?.agent?.state.statusLabel()}]",
                            style = Tui.label.copy(color = current?.overview?.agent?.state.color()),
                        )
                    }
                    Text(
                        text = listOfNotNull(
                            current?.overview?.agent?.kind ?: "shell",
                            current?.overview?.workspace?.cwd,
                        ).joinToString(" · "),
                        style = Tui.label.copy(color = Tui.textFaint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (current?.profile?.id?.let { aggregate.reconnecting[it] } == true) {
                CircularProgressIndicator(
                    color = Tui.accentBlue,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                TuiIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Force reconnect",
                    tint = if (currentRepo != null) Tui.textDim else Tui.textFaint,
                    onClick = { currentRepo?.let { repo -> scope.launch { repo.reconnect() } } },
                )
            }
            Box {
                TuiIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "New workspace",
                    tint = if (repositories.isEmpty()) Tui.textFaint else Tui.textDim,
                    onClick = {
                        when {
                            repositories.isEmpty() -> Unit
                            repositories.size == 1 -> {
                                val repo = repositories.values.first()
                                scope.launch { repo.createWorkspace().onSuccess { id -> selectedKey = "${repo.currentProfile?.id}:$id" } }
                            }
                            else -> newWorkspaceMenuOpen = true
                        }
                    },
                )
                DropdownMenu(expanded = newWorkspaceMenuOpen, onDismissRequest = { newWorkspaceMenuOpen = false }) {
                    for ((profileId, repo) in repositories) {
                        DropdownMenuItem(
                            text = { Text(repo.currentProfile?.displayName ?: profileId) },
                            onClick = {
                                newWorkspaceMenuOpen = false
                                scope.launch { repo.createWorkspace().onSuccess { id -> selectedKey = "$profileId:$id" } }
                            },
                        )
                    }
                }
            }
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
            TuiIconButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = { settingsOpen = true },
            )
        }

        WorkspaceTabsRow(
            workspaces = workspaces,
            currentKey = currentKey,
            multiHost = repositories.size > 1,
            layout = settings.tabLayout,
            hostOrder = repositories.keys.toList(),
            onSelect = { selectedKey = it },
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
                        repositories.isEmpty() -> "not connected to any host"
                        currentKey == null -> aggregate.hostStates.values.firstNotNullOfOrNull { it.staleError }
                            ?: "no active workspaces"
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
            if (uploads.isNotEmpty()) {
                UploadsOverlay(uploads, Modifier.align(Alignment.BottomEnd).padding(6.dp))
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
        TermuxModifierRow(
            session = session,
            onAttach = { if (session != null) pickAttachment.launch("*/*") },
        )
    }

    pendingClose?.let { toClose ->
        TuiConfirmDialog(
            title = "Close workspace?",
            text = "“${toClose.overview.workspace.label}” will be closed on the host.",
            confirmLabel = "Close",
            onConfirm = {
                val key = toClose.key
                pendingClose = null
                if (selectedKey == key) selectedKey = null
                allSessions.removeSession(key)
                val repo = repositories[toClose.profile.id]
                val workspaceId = toClose.overview.workspace.id
                scope.launch { repo?.closeWorkspace(workspaceId) }
            },
            onDismiss = { pendingClose = null },
        )
    }

    if (settingsOpen) {
        SettingsDialog(
            settings = settings,
            onChange = { transform -> app.settingsStore.update(transform) },
            onDismiss = { settingsOpen = false },
        )
    }
}

/** The picked document's name (what a file manager would show) and byte size, where its provider says. */
private fun Context.describe(uri: Uri): Pair<String, Long?> {
    val columns = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    return contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0) ?: "file"
        val size = if (cursor.isNull(1)) null else cursor.getLong(1)
        name to size
    } ?: ("file" to null)
}

/** One in-flight attachment upload; [sent] is written from the upload's IO thread. */
private class UploadProgress(val name: String, val total: Long?) {
    var sent by mutableLongStateOf(0L)
}

/** Reports the running byte count to [onProgress] as the upload reads through [input]. */
private class CountingInputStream(input: InputStream, private val onProgress: (Long) -> Unit) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) onProgress(++count) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) { count += it; onProgress(count) } }
}

/** A small translucent card listing every upload in flight, each with its own progress bar. */
@Composable
private fun UploadsOverlay(uploads: List<UploadProgress>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(200.dp)
            .background(Tui.background.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, Tui.border), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (upload in uploads) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "↑ ${upload.name}",
                    style = Tui.label.copy(color = Tui.textDim, fontSize = 10.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val total = upload.total
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (upload.sent.toFloat() / total).coerceIn(0f, 1f) },
                        color = Tui.accentBlue,
                        trackColor = Tui.border,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // The provider didn't say how big the file is, so there's no fraction to show.
                    LinearProgressIndicator(color = Tui.accentBlue, trackColor = Tui.border, modifier = Modifier.fillMaxWidth())
                }
            }
        }
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

/**
 * The workspace tab strip: either one scrollable row of every workspace, or — with
 * [TabLayout.GROUP_BY_HOST] — one scrollable row per host, ordered as [hostOrder], with
 * compact single-line tiles since the row itself already says which host they're on.
 */
@Composable
private fun WorkspaceTabsRow(
    workspaces: List<HostWorkspace>,
    currentKey: String?,
    multiHost: Boolean,
    layout: TabLayout,
    hostOrder: List<String>,
    onSelect: (String) -> Unit,
) {
    // One tick for every row, so every running workspace spins in step, and none of them
    // spin — or recompose — while nothing is running.
    val anyWorking = workspaces.any { it.overview.agent?.state == AgentState.WORKING }
    var spinnerFrame by remember { mutableIntStateOf(0) }
    LaunchedEffect(anyWorking) {
        if (!anyWorking) return@LaunchedEffect
        while (isActive) {
            delay(SPINNER_FRAME_MS)
            spinnerFrame = (spinnerFrame + 1) % SPINNER_FRAMES.length
        }
    }

    when (layout) {
        TabLayout.SINGLE_ROW -> WorkspaceTabStrip(
            workspaces = workspaces,
            currentKey = currentKey,
            spinnerFrame = spinnerFrame,
            compact = false,
            multiHost = multiHost,
            onSelect = onSelect,
        )
        TabLayout.GROUP_BY_HOST -> {
            // `groupBy` keeps each host's own MRU order; the rows themselves follow
            // [hostOrder] so they stay put instead of reshuffling on every tab switch.
            val byHost = workspaces.groupBy { it.profile.id }
            val orderedHosts = hostOrder.filter { it in byHost } + byHost.keys.filterNot { it in hostOrder }
            // Padded to the longest label (the font is monospace) so every row's tiles start
            // at the same x instead of wherever its own host name happens to end.
            val hostLabels = byHost.mapValues { (_, group) -> group.first().profile.displayName.take(12) }
            val labelWidth = hostLabels.values.maxOfOrNull { it.length } ?: 0
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                for (profileId in orderedHosts) {
                    key(profileId) {
                        val group = byHost.getValue(profileId)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (multiHost) {
                                Text(
                                    text = hostLabels.getValue(profileId).padEnd(labelWidth),
                                    style = Tui.label.copy(color = Tui.textFaint, fontSize = 10.sp),
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                            WorkspaceTabStrip(
                                workspaces = group,
                                currentKey = currentKey,
                                spinnerFrame = spinnerFrame,
                                compact = true,
                                multiHost = multiHost,
                                onSelect = onSelect,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One horizontally scrollable row of workspace tiles. */
@Composable
private fun WorkspaceTabStrip(
    workspaces: List<HostWorkspace>,
    currentKey: String?,
    spinnerFrame: Int,
    compact: Boolean,
    multiHost: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The selected tab is always sorted to index 0 (see the MRU reorder above this row's
    // caller), so picking one is pointless if the strip is still scrolled to wherever it used
    // to sit — follow it back to the start the same way switching apps jumps you to the front
    // of a recents list.
    //
    // Keyed on the front item, not on `currentKey` directly: the caller's own reorder (which
    // moves the newly-selected tab to index 0 of `workspaces`) happens in its own effect one
    // recomposition after `currentKey` changes, so keying on `currentKey` here raced it —
    // this scrolled to index 0 of the *not-yet-reordered* list, landing on whatever tab used
    // to be first instead of the one just tapped, which is what showed up as a half-offscreen
    // selection outline to the left. Waiting for the front slot itself to change means the
    // reorder has already landed by the time this runs.
    val listState = rememberLazyListState()
    val frontKey = workspaces.firstOrNull()?.key
    LaunchedEffect(frontKey) {
        if (frontKey != null) listState.animateScrollToItem(0)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 2.dp else 4.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(workspaces, key = { it.key }) { hostWorkspace ->
            WorkspaceTile(
                hostWorkspace = hostWorkspace,
                isCurrent = hostWorkspace.key == currentKey,
                spinnerFrame = spinnerFrame,
                compact = compact,
                multiHost = multiHost,
                onClick = { onSelect(hostWorkspace.key) },
            )
        }
    }
}

@Composable
private fun WorkspaceTile(
    hostWorkspace: HostWorkspace,
    isCurrent: Boolean,
    spinnerFrame: Int,
    compact: Boolean,
    multiHost: Boolean,
    onClick: () -> Unit,
) {
    val overview = hostWorkspace.overview
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
                onClick = onClick,
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
        if (!compact) {
            Text(
                // With more than one host connected, a bare "shell"/"claude" no longer says
                // which machine it's running on — so the host's own name leads, clipped to
                // a short prefix so it doesn't blow up the tile's width just to disambiguate.
                text = if (multiHost) {
                    listOfNotNull(
                        hostWorkspace.profile.displayName.take(4),
                        overview.agent?.kind ?: "shell",
                    ).joinToString(" · ")
                } else {
                    overview.agent?.kind ?: "shell"
                },
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
