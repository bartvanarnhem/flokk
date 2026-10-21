package com.flokk.app.ui.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flokk.app.data.ConnectionProfile
import com.flokk.app.herdr.HerdrRepository
import com.flokk.app.herdr.RepoState
import com.flokk.app.ssh.ConnectivityProbe
import com.flokk.app.ssh.ProbeResult
import com.flokk.app.ui.LocalFlokk
import com.flokk.app.ui.theme.DashedButton
import com.flokk.app.ui.theme.Flokk
import com.flokk.app.ui.theme.FlokkCard
import com.flokk.app.ui.theme.HeaderIconButton
import com.flokk.app.ui.theme.InlineConfirm
import com.flokk.app.ui.theme.OutlineButton
import com.flokk.app.ui.theme.PrimaryButton
import com.flokk.app.ui.theme.StatusDot
import com.flokk.app.ui.theme.TonalButton
import com.flokk.app.ui.theme.plainClickable
import com.flokk.app.ui.workspace.relativeTimeShort
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    onOpenWorkspaces: () -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
) {
    val app = LocalFlokk.current
    val scope = rememberCoroutineScope()

    var profiles by remember { mutableStateOf(app.profileStore.listProfiles()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                profiles = app.profileStore.listProfiles()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val repositories by app.connectionManager.repositories.collectAsState()
    val connectingIds by app.connectionManager.connectingIds.collectAsState()
    val connectErrors by app.connectionManager.connectErrors.collectAsState()
    val hostStates = rememberHostStates(repositories)
    var probes by remember { mutableStateOf<Map<String, ProbeResult>>(emptyMap()) }
    // Only one card shows its actions (and maybe its delete confirmation) at a time.
    var actionsOpenId by remember { mutableStateOf<String?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var reloading by remember { mutableStateOf(false) }

    suspend fun probeOne(profile: ConnectionProfile) {
        val result = ConnectivityProbe.probe(profile.host, profile.port)
        probes = probes + (profile.id to result)
    }

    fun connect(profile: ConnectionProfile) {
        scope.launch { app.connectionManager.connect(profile) }
    }

    // Re-probes every host and force-reconnects the connected ones, for when the list (or a
    // connection that silently died in the background) looks stale.
    fun reload() {
        if (reloading) return
        reloading = true
        scope.launch {
            coroutineScope {
                launch { app.connectionManager.reloadAll() }
                profiles.forEach { profile -> launch { probeOne(profile) } }
            }
            reloading = false
        }
    }

    LaunchedEffect(profiles) {
        probes = emptyMap()
        profiles.forEach { profile -> launch { probeOne(profile) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Flokk.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Connections", style = Flokk.screenTitle, modifier = Modifier.weight(1f))
            if (profiles.isNotEmpty()) {
                if (reloading) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Flokk.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    }
                } else {
                    HeaderIconButton(icon = Icons.Rounded.Refresh, contentDescription = "Reload connections", onClick = ::reload)
                }
            }
        }

        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = reloading,
            onRefresh = ::reload,
            state = pullState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = reloading,
                    containerColor = Flokk.raised,
                    color = Flokk.accent,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "summary") {
                    SummaryLine(profiles, repositories, probes)
                }
                if (profiles.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text("No saved connections yet", style = Flokk.cardTitle)
                            Text("Add a host running herdr to get started.", style = Flokk.small)
                        }
                    }
                }
                items(profiles, key = { it.id }) { profile ->
                    val repo = repositories[profile.id]
                    val actionsOpen = actionsOpenId == profile.id
                    ConnectionCard(
                        profile = profile,
                        probe = probes[profile.id],
                        connecting = profile.id in connectingIds,
                        connected = repo != null,
                        hostState = hostStates[profile.id],
                        error = connectErrors[profile.id],
                        actionsOpen = actionsOpen,
                        confirmingDelete = actionsOpen && confirmDeleteId == profile.id,
                        onClick = {
                            when {
                                repo != null -> onOpenWorkspaces()
                                profile.id !in connectingIds -> connect(profile)
                            }
                        },
                        onToggleActions = {
                            actionsOpenId = if (actionsOpen) null else profile.id
                            confirmDeleteId = null
                        },
                        onRetry = {
                            scope.launch { probeOne(profile) }
                            connect(profile)
                        },
                        onEdit = { actionsOpenId = null; onEditConnection(profile.id) },
                        onToggleConnection = {
                            actionsOpenId = null
                            if (repo != null) app.connectionManager.disconnect(profile.id) else connect(profile)
                        },
                        onDelete = { confirmDeleteId = profile.id },
                        onCancelDelete = { confirmDeleteId = null },
                        onConfirmDelete = {
                            app.connectionManager.disconnect(profile.id)
                            app.profileStore.deleteProfile(profile.id)
                            profiles = app.profileStore.listProfiles()
                            actionsOpenId = null
                            confirmDeleteId = null
                        },
                    )
                }
                item(key = "add") {
                    DashedButton(text = "Add connection", icon = Icons.Rounded.Add, onClick = onAddConnection)
                }
            }
        }

        val connectedCount = repositories.size
        val workspaceCount = hostStates.values.sumOf { it.workspaces.size }
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PrimaryButton(
                text = "Open workspaces",
                trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                enabled = connectedCount > 0,
                onClick = onOpenWorkspaces,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (connectedCount == 0) {
                    "Connect a host to open its workspaces"
                } else {
                    "${plural(workspaceCount, "workspace")} on ${plural(connectedCount, "host")}"
                },
                style = Flokk.small.copy(color = Flokk.textFaint),
            )
        }
    }
}

/** Every connected host's latest [RepoState.Connected], subscribed per profile id. */
@Composable
private fun rememberHostStates(repositories: Map<String, HerdrRepository>): Map<String, RepoState.Connected> {
    val result = mutableMapOf<String, RepoState.Connected>()
    for ((profileId, repo) in repositories) {
        key(profileId) {
            val state by repo.state.collectAsState()
            (state as? RepoState.Connected)?.let { result[profileId] = it }
        }
    }
    return result
}

private fun plural(count: Int, noun: String) = "$count $noun${if (count == 1) "" else "s"}"

/** "3 hosts · 2 up · 1 down · ~/.flokk/hosts" */
@Composable
private fun SummaryLine(
    profiles: List<ConnectionProfile>,
    repositories: Map<String, HerdrRepository>,
    probes: Map<String, ProbeResult>,
) {
    val up = profiles.count { it.id in repositories || probes[it.id] is ProbeResult.Reachable }
    val down = profiles.count { it.id !in repositories && probes[it.id] is ProbeResult.Unreachable }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val separator = @Composable { Text("·", style = Flokk.small.copy(color = Flokk.dim)) }
        Text(plural(profiles.size, "host"), style = Flokk.small)
        if (up > 0) {
            separator()
            Text("$up up", style = Flokk.small.copy(color = Flokk.green))
        }
        if (down > 0) {
            separator()
            Text("$down down", style = Flokk.small.copy(color = Flokk.red))
        }
        separator()
        Text("~/.flokk/hosts", style = Flokk.monoSmall.copy(fontSize = 12.sp))
    }
}

@Composable
private fun ConnectionCard(
    profile: ConnectionProfile,
    probe: ProbeResult?,
    connecting: Boolean,
    connected: Boolean,
    hostState: RepoState.Connected?,
    error: String?,
    actionsOpen: Boolean,
    confirmingDelete: Boolean,
    onClick: () -> Unit,
    onToggleActions: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onToggleConnection: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val unreachable = !connected && probe is ProbeResult.Unreachable
    val dotColor = when {
        connected -> if (hostState?.staleError != null) Flokk.red else Flokk.green
        connecting -> Flokk.accent
        unreachable || error != null -> Flokk.red
        else -> Flokk.dotUnknown
    }

    val message = when {
        connected -> hostState?.staleError
        connecting -> null
        else -> error
    }

    FlokkCard(
        border = if (actionsOpen) Flokk.border else null,
        padding = 10.dp,
        spacing = 6.dp,
        modifier = Modifier.plainClickable(onClick = onClick),
    ) {
        // One line: dot, name, user@host, then a quiet warning (if any) and the actions toggle.
        Row(
            modifier = Modifier.height(28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(dotColor, size = 8.dp)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(profile.displayName, style = Flokk.cardTitle, maxLines = 1, softWrap = false)
                Text(profile.target, style = Flokk.monoSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (message != null) ErrorBadge(message)
            HeaderIconButton(
                icon = if (actionsOpen) Icons.Rounded.Close else Icons.Rounded.MoreVert,
                contentDescription = if (actionsOpen) "Close actions" else "Connection actions",
                size = 28.dp,
                iconSize = if (actionsOpen) 18.dp else 20.dp,
                tint = if (actionsOpen) Flokk.text else Flokk.textFaint,
                shape = CircleShape,
                modifier = if (actionsOpen) Modifier.background(Flokk.raised, CircleShape) else Modifier,
                onClick = onToggleActions,
            )
        }

        when {
            connected -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val reconnecting = hostState?.reconnecting == true
                val latency = hostState?.lastPollMs
                StatusChip(
                    text = when {
                        reconnecting -> "Reconnecting…"
                        latency != null -> "Connected · $latency ms"
                        else -> "Connected"
                    },
                )
                Text(plural(hostState?.workspaces?.size ?: 0, "workspace"), style = Flokk.small)
            }
            connecting -> Text("Connecting…", style = Flokk.small)
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val lastSeen = profile.lastConnectedAt?.let { t ->
                    val rel = relativeTimeShort(t)
                    if (rel == "just now") "last seen just now" else "last seen $rel ago"
                } ?: "never connected"
                Text(
                    text = when (probe) {
                        is ProbeResult.Unreachable -> "Unreachable · $lastSeen"
                        is ProbeResult.Reachable -> "Reachable · ${probe.latencyMs} ms"
                        null -> "Checking…"
                    },
                    style = Flokk.small,
                    modifier = Modifier.weight(1f),
                )
                if (probe != null) {
                    OutlineButton(
                        text = if (unreachable || error != null) "Retry" else "Connect",
                        leadingIcon = if (unreachable || error != null) Icons.Rounded.Refresh else Icons.Rounded.Link,
                        height = 28.dp,
                        radius = 7.dp,
                        fontSize = 13f,
                        contentColor = Flokk.text,
                        onClick = onRetry,
                    )
                }
            }
        }

        if (actionsOpen) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalButton(text = "Edit", leadingIcon = Icons.Rounded.Edit, onClick = onEdit, modifier = Modifier.weight(1f))
                TonalButton(
                    text = if (connected) "Disconnect" else "Connect",
                    leadingIcon = if (connected) Icons.Rounded.LinkOff else Icons.Rounded.Link,
                    enabled = !connecting,
                    onClick = onToggleConnection,
                    modifier = Modifier.weight(1f),
                )
                TonalButton(
                    text = "Delete",
                    leadingIcon = Icons.Rounded.Delete,
                    background = Flokk.redBackground,
                    contentColor = Flokk.red,
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
            if (confirmingDelete) {
                InlineConfirm(
                    title = "Delete connection?",
                    message = "“${profile.displayName}” will be removed from this device.",
                    confirmLabel = "Delete",
                    onConfirm = onConfirmDelete,
                    onCancel = onCancelDelete,
                )
            }
        }
    }
}

/** A subtle warning icon standing in for a host's error; tapping it shows the message in a popup. */
@Composable
private fun ErrorBadge(message: String) {
    var open by remember { mutableStateOf(false) }
    Box {
        HeaderIconButton(
            icon = Icons.Rounded.WarningAmber,
            contentDescription = "Show error",
            size = 28.dp,
            iconSize = 18.dp,
            tint = Flokk.red,
            shape = CircleShape,
            active = false,
            onClick = { open = !open },
        )
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = with(LocalDensity.current) { IntOffset(0, 32.dp.roundToPx()) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .shadow(8.dp, RoundedCornerShape(10.dp))
                        .background(Flokk.raised, RoundedCornerShape(10.dp))
                        .border(BorderStroke(1.dp, Flokk.border), RoundedCornerShape(10.dp))
                        .plainClickable { open = false }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Error, null, tint = Flokk.red, modifier = Modifier.size(18.dp))
                    Text(message, style = Flokk.small.copy(color = Flokk.textSecondary))
                }
            }
        }
    }
}

/** The small green "Connected · 355 ms" pill. */
@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(Flokk.greenBackground, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = Flokk.sans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = Flokk.green),
        )
    }
}
