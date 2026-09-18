package com.orbisk.flokk.ui.connections

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LinkOff
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.orbisk.flokk.data.AuthMethod
import com.orbisk.flokk.data.ConnectionProfile
import com.orbisk.flokk.herdr.RepoState
import com.orbisk.flokk.ssh.ConnectivityProbe
import com.orbisk.flokk.ssh.ProbeResult
import com.orbisk.flokk.ui.LocalFlokk
import com.orbisk.flokk.ui.workspace.relativeTimeShort
import com.orbisk.flokk.ui.theme.Tui
import com.orbisk.flokk.ui.theme.TuiBox
import com.orbisk.flokk.ui.theme.TuiConfirmDialog
import com.orbisk.flokk.ui.theme.TuiDashedButton
import kotlinx.coroutines.launch

@Composable
fun ConnectionsScreen(
    onConnected: () -> Unit,
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

    var connectingId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<ConnectionProfile?>(null) }
    var probes by remember { mutableStateOf<Map<String, ProbeResult>>(emptyMap()) }
    val repoState by app.repository.state.collectAsState()
    val connected = repoState as? RepoState.Connected

    suspend fun probeOne(profile: ConnectionProfile) {
        val result = ConnectivityProbe.probe(profile.host, profile.port)
        probes = probes + (profile.id to result)
    }

    LaunchedEffect(profiles) {
        probes = emptyMap()
        profiles.forEach { profile -> launch { probeOne(profile) } }
    }

    LaunchedEffect(repoState) {
        when (val s = repoState) {
            // Only follow a connect started from here: coming back from the workspace screen
            // leaves the session connected, and must not bounce straight into it again.
            is RepoState.Connected -> {
                if (connectingId != null) {
                    connectingId = null
                    onConnected()
                }
            }
            is RepoState.ConnectFailed -> {
                connectingId = null
                snackbarHostState.showSnackbar(
                    message = s.message,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
            }
            else -> {}
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
                    val isActive = app.repository.currentProfile?.id == profile.id &&
                        connected != null
                    ConnectionRow(
                        profile = profile,
                        jumpName = profile.jumpHostId?.let { id -> profiles.firstOrNull { it.id == id }?.displayName },
                        probe = probes[profile.id],
                        connecting = connectingId == profile.id,
                        isActive = isActive,
                        activeLatencyMs = connected?.lastPollMs,
                        activeChannelCount = app.repository.transport.activeChannelCount,
                        activeWorkspaceCount = connected?.workspaces?.size ?: 0,
                        onClick = {
                            when {
                                isActive -> onConnected()
                                connectingId == null -> {
                                    connectingId = profile.id
                                    scope.launch { app.repository.connect(profile) }
                                }
                            }
                        },
                        onRetry = { scope.launch { probeOne(profile) } },
                        onEdit = { onEditConnection(profile.id) },
                        onDelete = { pendingDelete = profile },
                        onDisconnect = if (isActive) {
                            { app.repository.disconnect() }
                        } else {
                            null
                        },
                    )
                }
                item {
                    TuiDashedButton(text = "+ add connection", onClick = onAddConnection)
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
    isActive: Boolean,
    activeLatencyMs: Long?,
    activeChannelCount: Int,
    activeWorkspaceCount: Int,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDisconnect: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val unreachable = probe is ProbeResult.Unreachable
    val dotColor = when {
        connecting || isActive -> Tui.accentGreen
        unreachable -> Tui.accentRed
        probe is ProbeResult.Reachable -> Tui.accentGreen
        else -> Tui.textFaint
    }
    val statusText = when {
        connecting -> "connecting…"
        isActive -> if (activeLatencyMs != null) "connected · ${activeLatencyMs}ms" else "connected"
        unreachable -> "unreachable"
        probe is ProbeResult.Reachable -> "reachable · ${probe.latencyMs}ms"
        else -> "checking…"
    }

    TuiBox(
        borderColor = Tui.border,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
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
                        if (onDisconnect != null) {
                            DropdownMenuItem(
                                text = { Text("Disconnect") },
                                leadingIcon = { Icon(Icons.Filled.LinkOff, contentDescription = null) },
                                onClick = { menuExpanded = false; onDisconnect() },
                            )
                        }
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
                if (isActive) {
                    append(" · mux $activeChannelCount")
                    append(" · $activeWorkspaceCount workspace${if (activeWorkspaceCount == 1) "" else "s"}")
                } else if (jumpName != null) {
                    append(" · via jump $jumpName")
                }
            }
            Text(text = metaLine, style = Tui.label.copy(color = Tui.textFaint))

            if (unreachable) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = profile.lastConnectedAt?.let { t ->
                            val rel = relativeTimeShort(t)
                            if (rel == "just now") "last seen just now" else "last seen $rel ago"
                        } ?: "never connected",
                        style = Tui.label.copy(color = Tui.textFaint),
                    )
                    Text(
                        text = "[ retry ]",
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
