package com.flokk.app.herdr

import com.flokk.app.data.ConnectionProfile
import com.flokk.app.data.ProfileStore
import com.flokk.app.ssh.SshTransport
import com.flokk.app.ssh.TofuHostKeyVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One workspace tagged with the connected host it came from — the unit the (multi-host)
 *  workspace screen operates on, since two different hosts can each report a workspace "w1". */
data class HostWorkspace(val profile: ConnectionProfile, val overview: WorkspaceOverview) {
    /** Unique across every connected host, unlike [WorkspaceOverview]'s own (per-host) id. */
    val key: String get() = "${profile.id}:${overview.workspace.id}"
}

/**
 * Owns one [HerdrRepository] — and its own [SshTransport] — per currently-connected host, so
 * several hosts can be attached to at once instead of just one at a time. Each repository is
 * created fresh on [connect] and discarded on [disconnect]; nothing here is shared between
 * hosts, exactly like opening several independent `ssh` connections.
 */
class HerdrConnectionManager(
    private val profileStore: ProfileStore,
    private val hostKeyVerifier: TofuHostKeyVerifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _repositories = MutableStateFlow<Map<String, HerdrRepository>>(emptyMap())
    val repositories: StateFlow<Map<String, HerdrRepository>> = _repositories

    /** [ConnectionProfile.id]s with a connect currently in flight — before success, there's no
     *  [HerdrRepository] yet for the UI to read a "connecting" state from. */
    private val _connectingIds = MutableStateFlow<Set<String>>(emptySet())
    val connectingIds: StateFlow<Set<String>> = _connectingIds

    /** The last connect failure per [ConnectionProfile.id], shown in that host's card until
     *  the next attempt — the failed attempt's [HerdrRepository] is discarded immediately, so
     *  its [RepoState.ConnectFailed] message has to be kept here before it's gone. */
    private val _connectErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectErrors: StateFlow<Map<String, String>> = _connectErrors

    fun isConnected(profileId: String): Boolean = _repositories.value.containsKey(profileId)

    suspend fun connect(profile: ConnectionProfile) {
        if (_repositories.value.containsKey(profile.id) || _connectingIds.value.contains(profile.id)) return
        _connectingIds.update { it + profile.id }
        _connectErrors.update { it - profile.id }
        try {
            val repo = HerdrRepository(SshTransport(hostKeyVerifier), profileStore)
            repo.connect(profile)
            when (val state = repo.state.value) {
                is RepoState.Connected -> _repositories.update { it + (profile.id to repo) }
                is RepoState.ConnectFailed -> {
                    repo.disconnect()
                    _connectErrors.update { it + (profile.id to state.message) }
                }
                else -> repo.disconnect()
            }
        } finally {
            _connectingIds.update { it - profile.id }
        }
    }

    fun disconnect(profileId: String) {
        _repositories.value[profileId]?.disconnect()
        _repositories.update { it - profileId }
    }

    fun disconnectAll() {
        _repositories.value.values.forEach { it.disconnect() }
        _repositories.value = emptyMap()
    }

    /**
     * The connections screen's "reload": force-reconnects every connected host (reviving a
     * session that silently died in the background) and retries every autoconnect host that
     * isn't connected. Returns once every attempt has finished.
     */
    suspend fun reloadAll() = coroutineScope {
        val connected = _repositories.value
        connected.values.forEach { repo -> launch { repo.reconnect() } }
        profileStore.listProfiles()
            .filter { it.autoConnect && it.id !in connected }
            .forEach { profile -> launch { connect(profile) } }
    }

    /** Connects every saved profile with [ConnectionProfile.autoConnect] set, on app start. */
    fun autoConnectAll() {
        profileStore.listProfiles().filter { it.autoConnect }.forEach { profile ->
            scope.launch { connect(profile) }
        }
    }
}
