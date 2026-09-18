package com.orbisk.flokk.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.orbisk.flokk.herdr.HerdrRepository
import com.orbisk.flokk.terminal.RemoteTerminalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * How many workspace terminals stay attached at once. Each one holds an SSH channel and its
 * own scrollback, so the cap bounds both rather than growing with every tab ever opened;
 * beyond it, the least recently shown tab is detached and re-attaches when you return to it.
 */
private const val MAX_LIVE_SESSIONS = 4

class WorkspaceSessionCache(
    val sessions: SnapshotStateMap<String, RemoteTerminalSession>,
    private val recentlyShown: SnapshotStateList<String>,
) {
    fun get(workspaceId: String?): RemoteTerminalSession? = workspaceId?.let { sessions[it] }

    fun remove(workspaceId: String) {
        sessions.remove(workspaceId)?.stop()
        recentlyShown.remove(workspaceId)
    }

    fun stopAll() {
        sessions.values.forEach { it.stop() }
        sessions.clear()
        recentlyShown.clear()
    }

    fun promote(workspaceId: String) {
        recentlyShown.remove(workspaceId)
        recentlyShown.add(0, workspaceId)
        while (recentlyShown.size > MAX_LIVE_SESSIONS) {
            sessions.remove(recentlyShown.removeAt(recentlyShown.lastIndex))?.stop()
        }
    }
}

@Composable
fun rememberWorkspaceSessions(
    repository: HerdrRepository,
    currentWorkspaceId: String?,
    connectionGeneration: Int,
): WorkspaceSessionCache {
    val cache = remember(connectionGeneration) {
        WorkspaceSessionCache(mutableStateMapOf(), mutableStateListOf())
    }
    DisposableEffect(connectionGeneration) {
        onDispose { cache.stopAll() }
    }
    LaunchedEffect(currentWorkspaceId, connectionGeneration) {
        val workspaceId = currentWorkspaceId ?: return@LaunchedEffect
        cache.promote(workspaceId)
        val existing = cache.sessions[workspaceId]
        if (existing != null && !existing.finished) return@LaunchedEffect
        if (existing != null) cache.sessions.remove(workspaceId)?.stop()

        var attachCommand = repository.attachCommandFor(workspaceId)
        var attempts = 0
        while (attachCommand == null && attempts < 30 && isActive) {
            delay(300)
            attachCommand = repository.attachCommandFor(workspaceId)
            attempts++
        }
        val command = attachCommand ?: return@LaunchedEffect
        cache.sessions[workspaceId] = RemoteTerminalSession(repository.transport, command)
    }
    return cache
}
