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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.flokk.app.data.ConnectionProfile
import com.flokk.app.data.TabLayout
import com.flokk.app.herdr.AgentState
import com.flokk.app.herdr.HerdrRepository
import com.flokk.app.herdr.HostWorkspace
import com.flokk.app.herdr.RepoState
import com.flokk.app.terminal.TermKeys
import com.flokk.app.terminal.TerminalCanvasView
import com.flokk.app.terminal.TerminalInputView
import com.flokk.app.ui.LocalFlokk
import com.flokk.app.ui.theme.Flokk
import com.flokk.app.ui.theme.FlokkCard
import com.flokk.app.ui.theme.HeaderIconButton
import com.flokk.app.ui.theme.InlineConfirm
import com.flokk.app.ui.theme.OutlineButton
import com.flokk.app.ui.theme.PrimaryButton
import com.flokk.app.ui.theme.StatusDot
import com.flokk.app.ui.theme.plainClickable
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

/** What opens in place under the workspace header; at most one at a time. */
private enum class Panel { NONE, NEW_WORKSPACE, SETTINGS }

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
    var panel by remember { mutableStateOf(Panel.NONE) }

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

    // Visible by default, as soon as a pane is attached — matching a real terminal app. With the
    // toggle off the input view still takes focus (so a hardware keyboard keeps working through
    // the IME as usual) but the soft keyboard is never requested.
    var softKeyboardOn by remember { mutableStateOf(true) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(session, inputView, softKeyboardOn, resumed) {
        if (session == null || !resumed) return@LaunchedEffect
        if (softKeyboardOn) inputView?.showKeyboard() else {
            inputView?.focusInput()
            inputView?.hideKeyboard()
        }
    }
    // Dismissing it with back/the system gesture turns the toggle off too. Only while resumed:
    // leaving for another activity (e.g. the attachment picker) also hides it.
    var imeWasVisible by remember { mutableStateOf(false) }
    LaunchedEffect(keyboardVisible) {
        if (imeWasVisible && !keyboardVisible && resumed) softKeyboardOn = false
        imeWasVisible = keyboardVisible
    }

    fun createWorkspace(repo: HerdrRepository) {
        panel = Panel.NONE
        val profileId = repo.currentProfile?.id
        scope.launch { repo.createWorkspace().onSuccess { id -> selectedKey = "$profileId:$id" } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Flokk.background)
            .imePadding(),
    ) {
        val current = workspaces.firstOrNull { it.key == currentKey }
        val currentRepo = current?.profile?.id?.let { repositories[it] }
        val currentHostState = current?.profile?.id?.let { aggregate.hostStates[it] }
        val reconnecting = currentHostState?.reconnecting == true

        WorkspaceHeader(
            current = current,
            reconnecting = reconnecting,
            canRefresh = currentRepo != null,
            canAdd = repositories.isNotEmpty(),
            panel = panel,
            softKeyboardOn = softKeyboardOn,
            onBack = onBack,
            onRefresh = { currentRepo?.let { repo -> scope.launch { repo.reconnect() } } },
            onAdd = {
                when {
                    panel == Panel.NEW_WORKSPACE -> panel = Panel.NONE
                    repositories.size == 1 -> createWorkspace(repositories.values.first())
                    repositories.size > 1 -> { panel = Panel.NEW_WORKSPACE; pendingClose = null }
                }
            },
            onToggleKeyboard = { softKeyboardOn = !softKeyboardOn },
            onToggleSettings = {
                panel = if (panel == Panel.SETTINGS) Panel.NONE else Panel.SETTINGS
                pendingClose = null
            },
        )

        val panelModifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
        when (panel) {
            Panel.NEW_WORKSPACE -> NewWorkspacePicker(
                hosts = repositories.mapNotNull { (profileId, repo) ->
                    repo.currentProfile?.let { profile ->
                        Triple(profile, repo, aggregate.hostStates[profileId]?.workspaces?.size ?: 0)
                    }
                },
                onPick = ::createWorkspace,
                modifier = panelModifier,
            )
            Panel.SETTINGS -> SettingsPanel(
                settings = settings,
                onChange = { transform -> app.settingsStore.update(transform) },
                modifier = panelModifier,
            )
            Panel.NONE -> Unit
        }

        // While the host picker is open, everything under it is only context.
        val pickerDim = if (panel == Panel.NEW_WORKSPACE) 0.5f else 1f

        WorkspaceTabsRow(
            workspaces = workspaces,
            currentKey = currentKey,
            closingKey = pendingClose?.key,
            multiHost = repositories.size > 1,
            layout = settings.tabLayout,
            hostOrder = repositories.keys.toList(),
            hostStates = aggregate.hostStates,
            onSelect = { selectedKey = it; pendingClose = null },
            onClose = { toClose ->
                pendingClose = if (pendingClose?.key == toClose.key) null else toClose
                if (panel != Panel.NONE) panel = Panel.NONE
            },
            modifier = Modifier.alpha(pickerDim),
        )

        pendingClose?.let { toClose ->
            InlineConfirm(
                title = "Close workspace?",
                message = "“${toClose.overview.workspace.label}” will be closed on the host.",
                confirmLabel = "Close",
                background = Flokk.card,
                radius = 12.dp,
                titleSize = 16f,
                buttonHeight = 36.dp,
                spacing = 8.dp,
                modifier = panelModifier,
                onConfirm = {
                    val key = toClose.key
                    pendingClose = null
                    if (selectedKey == key) selectedKey = null
                    allSessions.removeSession(key)
                    val repo = repositories[toClose.profile.id]
                    val workspaceId = toClose.overview.workspace.id
                    scope.launch { repo?.closeWorkspace(workspaceId) }
                },
                onCancel = { pendingClose = null },
            )
        }

        val attachError = session?.error?.takeIf { session.finished }
        val hostProblem = currentHostState?.staleError
        // A failed attach, or a host that dropped before this tab ever attached: either way
        // there's no terminal to show, so say why and offer a way out instead of a bare canvas.
        val failure = when {
            attachError != null -> attachError
            session == null && current != null && (hostProblem != null || reconnecting) -> hostProblem ?: "Not connected"
            else -> null
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Flokk.terminalBackground)
                .alpha(pickerDim)
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
            when {
                failure != null && current != null -> AttachFailedCard(
                    error = failure,
                    hostName = current.profile.displayName,
                    reconnecting = reconnecting,
                    onConnections = onBack,
                    onRetry = { currentRepo?.let { repo -> scope.launch { repo.reconnect() } } },
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                repositories.isEmpty() -> NoticeCard(
                    title = "Not connected to any host",
                    message = "Connect a host to open its workspaces.",
                    actionLabel = "Connections",
                    onAction = onBack,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                activeSession == null -> Text(
                    text = when {
                        currentKey == null -> aggregate.hostStates.values.firstNotNullOfOrNull { it.staleError }
                            ?: "No active workspaces. Tap + to create one."
                        else -> "Attaching…"
                    },
                    style = Flokk.small,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
                )
                else -> TerminalCanvasView(
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
            modifier = Modifier.alpha(if (failure != null) 0.5f else 1f),
        )
    }
}

/**
 * Back, the current workspace ("host · label" over "kind · ● status"), then refresh, new
 * workspace, keyboard and settings. Closing a workspace lives on its own tab instead.
 */
@Composable
private fun WorkspaceHeader(
    current: HostWorkspace?,
    reconnecting: Boolean,
    canRefresh: Boolean,
    canAdd: Boolean,
    panel: Panel,
    softKeyboardOn: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onToggleKeyboard: () -> Unit,
    onToggleSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back to connections",
            iconSize = 24.dp,
            tint = Flokk.text,
            onClick = onBack,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = current?.let { "${it.profile.displayName} · ${it.overview.workspace.label}" } ?: "No workspace",
                style = TextStyle(fontFamily = Flokk.sans, fontWeight = FontWeight.Medium, fontSize = 14.5.sp, lineHeight = 18.sp, color = Flokk.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (current != null) {
                val state = current.overview.agent?.state
                val sub = TextStyle(fontFamily = Flokk.sans, fontSize = 11.5.sp, lineHeight = 14.sp, color = Flokk.textMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(current.overview.agent?.kind ?: "shell", style = sub, maxLines = 1)
                    Text("·", style = sub.copy(color = Flokk.dim))
                    StatusDot(state.color())
                    Text(state.statusLabel(), style = sub, maxLines = 1)
                }
            }
        }
        if (reconnecting) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Flokk.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
        } else {
            HeaderIconButton(icon = Icons.Rounded.Refresh, contentDescription = "Force reconnect", enabled = canRefresh, onClick = onRefresh)
        }
        HeaderIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = "New workspace",
            active = panel == Panel.NEW_WORKSPACE,
            enabled = canAdd,
            onClick = onAdd,
        )
        HeaderIconButton(
            icon = Icons.Rounded.Keyboard,
            contentDescription = if (softKeyboardOn) "Hide keyboard" else "Show keyboard",
            active = softKeyboardOn,
            onClick = onToggleKeyboard,
        )
        HeaderIconButton(
            icon = Icons.Rounded.Tune,
            contentDescription = "Settings",
            active = panel == Panel.SETTINGS,
            onClick = onToggleSettings,
        )
    }
}

/** "New workspace on": one row per connected host, opened in place by the header's +. */
@Composable
private fun NewWorkspacePicker(
    hosts: List<Triple<ConnectionProfile, HerdrRepository, Int>>,
    onPick: (HerdrRepository) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Flokk.card, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Text(
            "New workspace on",
            style = Flokk.fieldLabel.copy(fontSize = 13.sp),
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
        )
        for ((profile, repo, count) in hosts) {
            HorizontalDivider(thickness = 1.dp, color = Flokk.dividerOnCard)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .plainClickable { onPick(repo) },
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(width = 36.dp, height = 30.dp).background(Flokk.raised, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Computer, null, tint = Flokk.textSecondary, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, style = Flokk.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$count workspace${if (count == 1) "" else "s"}", style = Flokk.small)
                }
                Icon(Icons.Rounded.Add, "New workspace on ${profile.displayName}", tint = Flokk.accent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Shown over the terminal when there's nothing to attach to, with a way out and a retry. */
@Composable
private fun AttachFailedCard(
    error: String,
    hostName: String,
    reconnecting: Boolean,
    onConnections: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A short reason reads well in the title; a long one goes underneath it instead.
    val shortError = error.takeIf { it.length <= 32 }
    FlokkCard(modifier = modifier, padding = 16.dp, spacing = 14.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(Flokk.redBackground, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.CloudOff, null, tint = Flokk.red, modifier = Modifier.size(22.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (shortError != null) "Attach failed: $shortError" else "Attach failed", style = Flokk.cardTitle)
                val detail = listOfNotNull(
                    error.takeIf { shortError == null },
                    if (reconnecting) "$hostName is unreachable. Reconnecting…" else null,
                ).joinToString("\n").ifEmpty { "Retry to reconnect to $hostName." }
                Text(detail, style = Flokk.small)
            }
        }
        if (reconnecting) {
            LinearProgressIndicator(
                color = Flokk.accent,
                trackColor = Flokk.border,
                strokeCap = StrokeCap.Round,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(text = "Connections", onClick = onConnections, height = 36.dp, radius = 8.dp, fontSize = 14f, modifier = Modifier.weight(1f))
            PrimaryButton(
                text = "Retry now",
                leadingIcon = Icons.Rounded.Refresh,
                enabled = !reconnecting,
                onClick = onRetry,
                height = 36.dp,
                radius = 8.dp,
                fontSize = 14f,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NoticeCard(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlokkCard(modifier = modifier, padding = 16.dp, spacing = 14.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Flokk.cardTitle)
            Text(message, style = Flokk.small)
        }
        PrimaryButton(text = actionLabel, onClick = onAction, height = 36.dp, radius = 8.dp, fontSize = 14f, modifier = Modifier.fillMaxWidth())
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

/** A small card listing every upload in flight, each with its own progress bar. */
@Composable
private fun UploadsOverlay(uploads: List<UploadProgress>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(200.dp)
            .background(Flokk.card.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (upload in uploads) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Upload, null, tint = Flokk.textMuted, modifier = Modifier.size(14.dp))
                    Text(
                        text = upload.name,
                        style = Flokk.small.copy(fontSize = 12.sp, color = Flokk.textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val total = upload.total
                val barModifier = Modifier.fillMaxWidth().height(4.dp)
                if (total != null && total > 0) {
                    LinearProgressIndicator(
                        progress = { (upload.sent.toFloat() / total).coerceIn(0f, 1f) },
                        color = Flokk.accent,
                        trackColor = Flokk.border,
                        strokeCap = StrokeCap.Round,
                        drawStopIndicator = {},
                        modifier = barModifier,
                    )
                } else {
                    // The provider didn't say how big the file is, so there's no fraction to show.
                    LinearProgressIndicator(color = Flokk.accent, trackColor = Flokk.border, strokeCap = StrokeCap.Round, modifier = barModifier)
                }
            }
        }
    }
}

/** The braille "dots" spinner CLIs use while something is running. */
private const val SPINNER_FRAMES = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
private const val SPINNER_FRAME_MS = 90L

/**
 * The tab strip's at-a-glance state: green while the agent works or has finished, amber while
 * it is waiting on an answer, and a quiet grey whenever nothing wants attention. Deliberately
 * coarser than [color] — which still labels the exact state in the header — because a strip
 * of dots is only useful if "needs me" reads differently from "doesn't" across the room.
 */
private fun AgentState?.indicatorColor(): Color = when (this) {
    AgentState.WORKING, AgentState.DONE -> Flokk.green
    AgentState.BLOCKED -> Flokk.accent
    else -> Flokk.dim
}

/** Whether a tab has anything to say beyond "nothing happening here". */
private fun AgentState?.wantsAttention(): Boolean =
    this == AgentState.WORKING || this == AgentState.DONE || this == AgentState.BLOCKED

/** A host's own dot in the grouped layout: red once its polling fails, amber while reconnecting. */
private fun RepoState.Connected?.hostColor(): Color = when {
    this == null -> Flokk.dim
    reconnecting -> Flokk.accent
    staleError != null -> Flokk.red
    else -> Flokk.green
}

/**
 * The workspace tab strip: either one scrollable row of every workspace, or — with
 * [TabLayout.GROUP_BY_HOST] — one scrollable row per host, ordered as [hostOrder], with
 * compact single-line tabs since the row itself already says which host they're on.
 */
@Composable
private fun WorkspaceTabsRow(
    workspaces: List<HostWorkspace>,
    currentKey: String?,
    closingKey: String?,
    multiHost: Boolean,
    layout: TabLayout,
    hostOrder: List<String>,
    hostStates: Map<String, RepoState.Connected>,
    onSelect: (String) -> Unit,
    onClose: (HostWorkspace) -> Unit,
    modifier: Modifier = Modifier,
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
            closingKey = closingKey,
            spinnerFrame = spinnerFrame,
            compact = false,
            multiHost = multiHost,
            onSelect = onSelect,
            onClose = onClose,
            modifier = modifier.padding(bottom = 6.dp),
            startPadding = 8.dp,
        )
        TabLayout.GROUP_BY_HOST -> {
            // `groupBy` keeps each host's own MRU order; the rows themselves follow
            // [hostOrder] so they stay put instead of reshuffling on every tab switch.
            val byHost = workspaces.groupBy { it.profile.id }
            val orderedHosts = hostOrder.filter { it in byHost } + byHost.keys.filterNot { it in hostOrder }
            Column(modifier = modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (profileId in orderedHosts) {
                    key(profileId) {
                        val group = byHost.getValue(profileId)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (multiHost) {
                                // A fixed-width label column, so every row's tabs start at the same x.
                                Row(
                                    modifier = Modifier.width(84.dp).padding(start = 10.dp, end = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    StatusDot(hostStates[profileId].hostColor())
                                    Text(
                                        text = group.first().profile.displayName,
                                        style = TextStyle(fontFamily = Flokk.sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = Flokk.textFaint),
                                        maxLines = 1,
                                        overflow = TextOverflow.Clip,
                                        softWrap = false,
                                    )
                                }
                            }
                            WorkspaceTabStrip(
                                workspaces = group,
                                currentKey = currentKey,
                                closingKey = closingKey,
                                spinnerFrame = spinnerFrame,
                                compact = true,
                                multiHost = multiHost,
                                onSelect = onSelect,
                                onClose = onClose,
                                modifier = Modifier.weight(1f),
                                startPadding = if (multiHost) 0.dp else 8.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One horizontally scrollable row of workspace tabs, fading out at the end while it overflows. */
@Composable
private fun WorkspaceTabStrip(
    workspaces: List<HostWorkspace>,
    currentKey: String?,
    closingKey: String?,
    spinnerFrame: Int,
    compact: Boolean,
    multiHost: Boolean,
    onSelect: (String) -> Unit,
    onClose: (HostWorkspace) -> Unit,
    modifier: Modifier = Modifier,
    startPadding: Dp = 0.dp,
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

    val fade = listState.canScrollForward
    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                if (fade) {
                    drawRect(
                        brush = Brush.horizontalGradient(0.82f to Color.Black, 1f to Color.Transparent),
                        blendMode = BlendMode.DstIn,
                    )
                }
            },
        contentPadding = PaddingValues(start = startPadding, end = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(workspaces, key = { it.key }) { hostWorkspace ->
            WorkspaceTab(
                hostWorkspace = hostWorkspace,
                isCurrent = hostWorkspace.key == currentKey,
                closing = hostWorkspace.key == closingKey,
                spinnerFrame = spinnerFrame,
                compact = compact,
                multiHost = multiHost,
                onClick = { onSelect(hostWorkspace.key) },
                onClose = { onClose(hostWorkspace) },
            )
        }
    }
}

/**
 * One workspace tab. Which tab you're looking at and what its agent is doing are two different
 * things, so they get two different channels: the amber outline and raised fill mark the
 * current tab (which also carries its close button), and the indicator alone carries agent
 * state — a braille spinner while running, a bell while it needs input, a dot otherwise.
 */
@Composable
private fun WorkspaceTab(
    hostWorkspace: HostWorkspace,
    isCurrent: Boolean,
    closing: Boolean,
    spinnerFrame: Int,
    compact: Boolean,
    multiHost: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val overview = hostWorkspace.overview
    val state = overview.agent?.state
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .height(if (compact) 24.dp else 32.dp)
            .background(if (isCurrent) Flokk.raised else Color.Transparent, shape)
            .border(BorderStroke(1.dp, if (isCurrent) Flokk.accent else Flokk.border), shape)
            .plainClickable(onClick = onClick)
            .padding(start = 9.dp, end = if (isCurrent) 3.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelStyle = TextStyle(
            fontFamily = Flokk.sans,
            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
            fontSize = if (compact) 12.sp else 13.sp,
            color = if (isCurrent || !compact) Flokk.text else Flokk.textSecondary,
        )
        if (compact) {
            AgentIndicator(state, spinnerFrame)
            Text(overview.workspace.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(overview.workspace.label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.wantsAttention()) AgentIndicator(state, spinnerFrame)
                }
                Text(
                    // With more than one host connected, a bare "shell"/"claude" no longer says
                    // which machine it's running on, so the host's own name leads.
                    text = listOfNotNull(
                        hostWorkspace.profile.displayName.takeIf { multiHost },
                        overview.agent?.kind ?: "shell",
                    ).joinToString(" · "),
                    style = TextStyle(
                        fontFamily = Flokk.sans,
                        fontSize = 10.5.sp,
                        lineHeight = 12.sp,
                        color = if (isCurrent) Flokk.textMuted else Flokk.textFaint,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(if (closing) Flokk.red else Color.Transparent, RoundedCornerShape(4.dp))
                    .plainClickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close workspace",
                    tint = if (closing) Flokk.background else Flokk.text,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun AgentIndicator(state: AgentState?, spinnerFrame: Int) {
    val color = state.indicatorColor()
    when (state) {
        AgentState.WORKING -> Text(
            text = SPINNER_FRAMES[spinnerFrame].toString(),
            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 12.sp, color = color),
        )
        AgentState.BLOCKED -> Icon(
            imageVector = Icons.Rounded.Notifications,
            contentDescription = "Needs input",
            tint = color,
            modifier = Modifier.size(12.dp),
        )
        else -> StatusDot(color)
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
