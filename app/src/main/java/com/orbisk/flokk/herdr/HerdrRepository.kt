package com.orbisk.flokk.herdr

import com.orbisk.flokk.data.ConnectionProfile
import com.orbisk.flokk.data.ProfileStore
import com.orbisk.flokk.ssh.SshTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

sealed interface RepoState {
    data object Disconnected : RepoState
    data object Connecting : RepoState
    data class Connected(
        val workspaces: List<WorkspaceOverview>,
        val staleError: String? = null,
        val lastPollMs: Long? = null,
        val reconnecting: Boolean = false,
    ) : RepoState
    data class ConnectFailed(val message: String) : RepoState
}

private const val POLL_INTERVAL_MS = 2_000L

/**
 * Owns the SSH connection lifecycle and polls `herdr` for an overview snapshot on a fixed
 * interval, exposing it as a [StateFlow] the UI observes. One instance per app process,
 * held in [com.orbisk.flokk.FlokkApplication].
 */
class HerdrRepository(val transport: SshTransport, private val profileStore: ProfileStore) {

    private val _state = MutableStateFlow<RepoState>(RepoState.Disconnected)
    val state: StateFlow<RepoState> = _state

    private val _connectionGeneration = MutableStateFlow(0)
    /** Bumped on every [connect] or [reconnect], so a terminal screen can key its attach effect
     *  on it (alongside the workspace id) and actually replace the live session instead of
     *  leaving the old, now-dead one attached with nothing to revive it. */
    val connectionGeneration: StateFlow<Int> = _connectionGeneration

    @Volatile var cli: HerdrCli? = null
        private set

    @Volatile var currentProfile: ConnectionProfile? = null
        private set

    /**
     * Owns the poll loop's lifetime. Deliberately NOT a Compose `rememberCoroutineScope()`
     * passed in from a screen: the polling must keep running across navigation between
     * the connections and workspace screens, only stopping on an explicit [disconnect].
     */
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var pollJob: Job? = null

    suspend fun connect(profile: ConnectionProfile) {
        pollJob?.cancel()
        _connectionGeneration.value++
        _state.value = RepoState.Connecting
        try {
            openTransport(profile)
        } catch (t: Throwable) {
            _state.value = RepoState.ConnectFailed(t.message ?: t.javaClass.simpleName)
            return
        }
        currentProfile = profile
        profileStore.touchConnected(profile.id)
        _state.value = RepoState.Connected(emptyList())
        pollJob = repoScope.launch { pollLoop() }
    }

    /**
     * Re-authenticates with the same [currentProfile] without resetting the poll loop or any
     * per-workspace state — unlike [connect], meant for reviving an already-established
     * session rather than starting a fresh one. Used both by the poll loop's own self-healing
     * above and by a user-facing "force reconnect" action; either way, only one attempt runs
     * at a time.
     */
    suspend fun reconnect(): Boolean {
        val profile = currentProfile ?: return false
        val previous = _state.value as? RepoState.Connected ?: return false
        if (previous.reconnecting) return false
        emitConnected(previous.workspaces, previous.staleError, previous.lastPollMs, reconnecting = true)
        try {
            openTransport(profile)
            _connectionGeneration.value++
            val latest = _state.value as? RepoState.Connected
            emitConnected(latest?.workspaces ?: previous.workspaces, staleError = null, lastPollMs = latest?.lastPollMs ?: previous.lastPollMs, reconnecting = false)
            return true
        } catch (t: Throwable) {
            val latest = _state.value as? RepoState.Connected
            emitConnected(
                latest?.workspaces ?: previous.workspaces,
                "reconnect failed: ${t.message ?: t.javaClass.simpleName}",
                latest?.lastPollMs ?: previous.lastPollMs,
                reconnecting = false,
            )
            return false
        }
    }

    /** Creates a new workspace and returns its id, refreshing the snapshot immediately on success. */
    suspend fun createWorkspace(): Result<String> {
        val current = cli ?: return Result.failure(IllegalStateException("not connected"))
        val result = current.createWorkspace()
        if (result.isSuccess) refreshNow()
        return result
    }

    /** Closes a workspace by id, refreshing the snapshot immediately on success. */
    suspend fun closeWorkspace(workspaceId: String): Result<Unit> {
        val current = cli ?: return Result.failure(IllegalStateException("not connected"))
        val result = current.closeWorkspace(workspaceId)
        if (result.isSuccess) refreshNow()
        return result
    }

    /** The command to type into a PTY to attach to [workspaceId]'s previewed pane, if known. */
    fun attachCommandFor(workspaceId: String): String? {
        val connected = _state.value as? RepoState.Connected ?: return null
        val terminalId = connected.workspaces.firstOrNull { it.workspace.id == workspaceId }?.previewTerminalId
            ?: return null
        return cli?.attachCommand(terminalId)
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        cli = null
        currentProfile = null
        transport.disconnect()
        _state.value = RepoState.Disconnected
    }

    private suspend fun openTransport(profile: ConnectionProfile) {
        val jump = profile.jumpHostId?.let { profileStore.getProfile(it) }
        transport.connect(profile, via = jump)
        cli = HerdrCli(transport, profile)
    }

    private suspend fun pollLoop() {
        while (coroutineContext.isActive) {
            val current = cli
            if (current == null) {
                delay(POLL_INTERVAL_MS)
                continue
            }
            // Backgrounding (app switch, Doze) or a plain network blip can silently kill
            // the socket without either side ever getting a chance to report an error —
            // the next thing that touches it just throws. Catching that here and
            // self-healing means a dropped connection recovers on its own within one poll
            // tick, instead of staring at "last poll failed" forever until the user
            // manually backs out to reconnect.
            if (!transport.isConnected) {
                reconnect()
                delay(POLL_INTERVAL_MS)
                continue
            }
            val started = System.currentTimeMillis()
            when (val snapshot = current.fetchSnapshot()) {
                is SnapshotResult.Success -> {
                    emitConnected(snapshot.workspaces, staleError = null, lastPollMs = System.currentTimeMillis() - started)
                }
                is SnapshotResult.Failure -> {
                    val previous = _state.value as? RepoState.Connected
                    emitConnected(previous?.workspaces.orEmpty(), snapshot.message, previous?.lastPollMs)
                    if (!transport.isConnected) reconnect()
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /** One off-cycle poll, so a create/close is reflected right away rather than up to
     *  [POLL_INTERVAL_MS] later. Safe to run alongside the poll loop: both go through
     *  [SshTransport]'s own session lock, which serializes exec channels. */
    private suspend fun refreshNow() {
        val current = cli ?: return
        when (val snapshot = current.fetchSnapshot()) {
            is SnapshotResult.Success -> emitConnected(snapshot.workspaces, staleError = null)
            is SnapshotResult.Failure -> Unit // the regular poll loop will retry and surface it
        }
    }

    private fun emitConnected(
        workspaces: List<WorkspaceOverview>,
        staleError: String? = null,
        lastPollMs: Long? = (_state.value as? RepoState.Connected)?.lastPollMs,
        reconnecting: Boolean = (_state.value as? RepoState.Connected)?.reconnecting ?: false,
    ) {
        _state.value = RepoState.Connected(workspaces, staleError, lastPollMs, reconnecting)
    }
}
