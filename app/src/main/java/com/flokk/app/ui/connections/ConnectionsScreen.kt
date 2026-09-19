package com.flokk.app.ui.connections

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.flokk.app.data.AuthMethod
import com.flokk.app.data.ConnectionProfile
import com.flokk.app.herdr.HerdrRepository
import com.flokk.app.herdr.RepoState
import com.flokk.app.ssh.ConnectivityProbe
import com.flokk.app.ssh.ProbeResult
import com.flokk.app.ui.LocalFlokk
import com.flokk.app.ui.workspace.relativeTimeShort
import com.flokk.app.ui.theme.Tui
import com.flokk.app.ui.theme.TuiBox
import com.flokk.app.ui.theme.TuiCheckbox
import com.flokk.app.ui.theme.TuiConfirmDialog
import com.flokk.app.ui.theme.TuiDashedButton
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    onOpenWorkspaces: () -> Unit,
    onAddConnection: () -> Unit,
    onEditConnection: (String) -> Unit,
) {
    val app = LocalFlokk.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
    var probes by remember { mutableStateOf<Map<String, ProbeResult>>(emptyMap()) }
    var pendingDelete by remember { mutableStateOf<ConnectionProfile?>(null) }

    suspend fun probeOne(profile: ConnectionProfile) {
        val result = ConnectivityProbe.probe(profile.host, profile.port)
        probes = probes + (profile.id to result)
    }

    LaunchedEffect(profiles) {
        probes = emptyMap()
        profiles.forEach { profile -> launch { probeOne(profile) } }
    }

    LaunchedEffect(Unit) {
        app.connectionManager.connectErrors.collect { message ->
            snackbarHostState.showSnackbar(message = message, withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tui.background),
    ) {
        val upCount = probes.values.count { it is ProbeResult.Reachable }
        val downCount = probes.values.count { it is ProbeResult.Unreachable }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "connections", style = Tui.body)
                Text(
                    text = "~/.flokk/hosts · $upCount up · $downCount down",
                    style = Tui.label.copy(color = Tui.textFaint),
                )
            }
        }

        if (profiles.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(text = "no saved connections yet", style = Tui.body)
                    Text(
                        text = "tap “+ add” to add your first herdr host.",
                        style = Tui.label,
                    )
                    TuiDashedButton(text = "+ add connection", onClick = onAddConnection)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    key(profile.id) {
                        val repo = repositories[profile.id]
                        val state = repo?.state?.collectAsState()?.value as? RepoState.Connected
                        ConnectionRow(
                            profile = profile,
                            jumpName = profile.jumpHostId?.let { id -> profiles.firstOrNull { it.id == id }?.displayName },
                            probe = probes[profile.id],
                            connecting = connectingIds.contains(profile.id),
                            connected = repo != null,
                            connectedLatencyMs = state?.lastPollMs,
                            connectedChannelCount = repo?.transport?.activeChannelCount,
                            connectedWorkspaceCount = state?.workspaces?.size ?: 0,
                            onToggleConnect = {
                                if (repo != null) {
                                    app.connectionManager.disconnect(profile.id)
                                } else {
                                    scope.launch { app.connectionManager.connect(profile) }
                                }
                            },
                            onToggleAutoConnect = {
                                val updated = profile.copy(autoConnect = it)
                                app.profileStore.saveProfile(updated)
                                profiles = app.profileStore.listProfiles()
                            },
                            onRetry = { scope.launch { probeOne(profile) } },
                            onEdit = { onEditConnection(profile.id) },
                            onDelete = { pendingDelete = profile },
                        )
                    }
                }
                item {
                    TuiDashedButton(text = "+ add connection", onClick = onAddConnection)
                }
                if (repositories.isNotEmpty()) {
                    item {
                        OpenWorkspacesButton(onClick = onOpenWorkspaces, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        SnackbarHost(snackbarHostState) { Snackbar(it) }
    }

    pendingDelete?.let { profile ->
        TuiConfirmDialog(
            title = "Delete connection?",
            text = "“${profile.displayName}” will be removed from this device.",
            confirmLabel = "Delete",
            onConfirm = {
                app.connectionManager.disconnect(profile.id)
                app.profileStore.deleteProfile(profile.id)
                profiles = app.profileStore.listProfiles()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun ConnectionRow(
    profile: ConnectionProfile,
    jumpName: String?,
    probe: ProbeResult?,
    connecting: Boolean,
    connected: Boolean,
    connectedLatencyMs: Long?,
    connectedChannelCount: Int?,
    connectedWorkspaceCount: Int,
    onToggleConnect: () -> Unit,
    onToggleAutoConnect: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val unreachable = probe is ProbeResult.Unreachable
    val dotColor = when {
        connecting || connected -> Tui.accentGreen
        unreachable -> Tui.accentRed
        probe is ProbeResult.Reachable -> Tui.accentGreen
        else -> Tui.textFaint
    }
    val statusText = when {
        connecting -> "connecting…"
        connected -> if (connectedLatencyMs != null) "connected · ${connectedLatencyMs}ms" else "connected"
        unreachable -> "unreachable"
        probe is ProbeResult.Reachable -> "reachable · ${probe.latencyMs}ms"
        else -> "checking…"
    }

    TuiBox(
        borderColor = if (connected) Tui.accentGreen else Tui.border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleConnect,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "● ", style = Tui.body.copy(color = dotColor))
                    Text(
                        text = profile.displayName,
                        style = Tui.body.copy(color = if (unreachable) Tui.textDim else Tui.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row {
                    Text(text = statusText, style = Tui.label.copy(color = dotColor))
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Tui.textFaint,
                        )
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
            Text(text = profile.target, style = Tui.label.copy(color = Tui.textDim))

            val authLabel = if (profile.auth is AuthMethod.PrivateKey) "key" else "password"
            val metaLine = buildString {
                append(authLabel)
                if (connected) {
                    append(" · mux ${connectedChannelCount ?: 0}")
                    append(" · $connectedWorkspaceCount workspace${if (connectedWorkspaceCount == 1) "" else "s"}")
                } else if (jumpName != null) {
                    append(" · via jump $jumpName")
                }
            }
            Text(text = metaLine, style = Tui.label.copy(color = Tui.textFaint))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TuiCheckbox's own `clickable` consumes the tap, so it doesn't also toggle
                // the card's connect state underneath it.
                TuiCheckbox(
                    checked = profile.autoConnect,
                    onCheckedChange = onToggleAutoConnect,
                    label = "autoconnect",
                )
                if (unreachable) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.lastConnectedAt?.let { t ->
                                val rel = relativeTimeShort(t)
                                if (rel == "just now") "last seen just now" else "last seen $rel ago"
                            } ?: "never connected",
                            style = Tui.label.copy(color = Tui.textFaint),
                        )
                        Text(
                            text = "  [ retry ]",
                            style = Tui.label.copy(color = Tui.textDim),
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onRetry,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** Hairline-bordered, matching the connections list's own cards rather than a filled CTA —
 *  the arrow is the same [Icons.AutoMirrored.Filled.ArrowForward] family as the workspace
 *  screen's own back button, just pointed the other way. */
@Composable
private fun OpenWorkspacesButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, Tui.accentGreen), RoundedCornerShape(5.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "workspaces", style = Tui.label.copy(color = Tui.accentGreen, fontSize = 12.sp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Tui.accentGreen,
            modifier = Modifier.padding(start = 6.dp).size(14.dp),
        )
    }
}
