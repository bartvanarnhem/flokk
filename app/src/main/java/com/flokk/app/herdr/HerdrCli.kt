package com.flokk.app.herdr

import com.flokk.app.data.ConnectionProfile
import com.flokk.app.ssh.SshTransport
import com.flokk.app.ssh.shellQuote
import kotlinx.serialization.json.JsonObject

private const val SEP_PANES = "__HERDR_SEP_PANES__"
private const val SEP_AGENTS = "__HERDR_SEP_AGENTS__"

/** Wraps SSH exec calls to the remote `herdr` CLI and turns their JSON into [WorkspaceOverview]s. */
class HerdrCli(
    private val transport: SshTransport,
    private val profile: ConnectionProfile,
) {

    /**
     * The binary every command runs. Starts as the profile's own [ConnectionProfile.herdrBinary];
     * see [resolveBinary] for when that changes.
     */
    @Volatile private var binary: String = profile.herdrBinary
    @Volatile private var binaryResolved = false

    /**
     * herdr's installer puts it in `~/.local/bin`, which a non-interactive SSH exec shell
     * often doesn't have on PATH (that's usually added in ~/.bashrc / ~/.profile, which exec
     * sessions don't source). So when the configured binary is a bare name the remote shell
     * can't find, fall back to `~/.local/bin/<name>` if it exists there. Checked once per
     * connection, on the first snapshot; an explicit path is always used as given.
     */
    private suspend fun resolveBinary() {
        if (binaryResolved) return
        val name = profile.herdrBinary
        if (!name.contains('/')) {
            val fallback = "\$HOME/.local/bin/$name"
            val probe = "command -v ${shellQuote(name)} >/dev/null 2>&1 || " +
                "{ test -x \"$fallback\" && echo \"$fallback\"; }"
            // A failed probe (e.g. the transport dropped) leaves this unresolved, to retry next time.
            val found = runCatching { transport.execBatch(probe) }.getOrNull() ?: return
            found.trim().lineSequence().lastOrNull { it.startsWith("/") }?.let { binary = shellQuote(it) }
        }
        binaryResolved = true
    }

    /** One SSH round trip: workspaces, panes, and agents in a single batched exec. */
    suspend fun fetchSnapshot(): SnapshotResult {
        resolveBinary()
        val cmd = buildString {
            // herdr's list subcommands don't take a `--json` flag (they error on it with a
            // usage message); they emit JSON automatically once stdout isn't a TTY, which is
            // always true for an SSH exec channel.
            append(herdr("workspace list"))
            append("; echo ").append(SEP_PANES)
            append("; ").append(herdr("pane list"))
            append("; echo ").append(SEP_AGENTS)
            append("; ").append(herdr("agent list"))
        }
        val raw = runCatching { transport.execBatch(cmd) }
            .getOrElse { return SnapshotResult.Failure(it.message ?: "SSH command failed") }

        val workspacesPart = raw.substringBefore(SEP_PANES)
        val panesPart = raw.substringAfter(SEP_PANES, "").substringBefore(SEP_AGENTS)
        val agentsPart = raw.substringAfter(SEP_AGENTS, "")

        val workspacesJson = HerdrJson.parseOrNull(workspacesPart)
        if (workspacesJson == null) {
            // `herdr workspace list` printed something that isn't JSON at all (as opposed to
            // a valid empty list) — most likely the SSH exec channel's non-interactive shell
            // doesn't have `herdrBinary` on PATH (SSH exec sessions don't source ~/.bashrc),
            // or the command otherwise failed. Surface the raw output instead of silently
            // reporting zero workspaces, so this is diagnosable from the app alone.
            val snippet = workspacesPart.trim().take(200)
            return SnapshotResult.Failure(
                if (snippet.isEmpty()) {
                    "`herdr workspace list` produced no output — herdr wasn't found on PATH or " +
                        "in ~/.local/bin. Set the \"Binary\" field to its absolute path (SSH " +
                        "exec sessions don't source ~/.bashrc, so a PATH entry added there " +
                        "won't apply here)"
                } else {
                    "unexpected output from `herdr workspace list`: $snippet"
                },
            )
        }

        val workspaces = HerdrJson.extractArray(workspacesJson).mapNotNull { obj ->
            val id = HerdrJson.firstString(obj, "workspace_id", "id") ?: return@mapNotNull null
            HerdrWorkspace(
                id = id,
                label = HerdrJson.firstString(obj, "label", "name", "title") ?: id,
                cwd = HerdrJson.firstString(obj, "cwd", "path", "directory"),
            )
        }

        val panes = HerdrJson.extractArray(HerdrJson.parseOrNull(panesPart)).mapNotNull { obj ->
            val id = HerdrJson.firstString(obj, "pane_id", "id") ?: return@mapNotNull null
            HerdrPane(
                id = id,
                workspaceId = HerdrJson.firstString(obj, "workspace_id", "workspace"),
                focused = HerdrJson.firstBoolean(obj, "focused", "is_focused", "root", "is_root"),
                terminalId = HerdrJson.firstString(obj, "terminal_id") ?: id,
            )
        }

        val agents = HerdrJson.extractArray(HerdrJson.parseOrNull(agentsPart)).mapNotNull { obj ->
            val paneId = HerdrJson.firstString(obj, "pane_id", "target", "id") ?: return@mapNotNull null
            HerdrAgent(
                paneId = paneId,
                kind = HerdrJson.firstString(obj, "kind", "agent", "type"),
                state = AgentState.parse(HerdrJson.firstString(obj, "agent_status", "state", "status")),
            )
        }.associateBy { it.paneId }

        val panesByWorkspace = panes.groupBy { it.workspaceId }

        // An agent waiting on an answer is the whole reason to look at a workspace, so it wins
        // over the focused pane: with a split layout the focused pane is often a plain shell,
        // and attaching to that would report the workspace as agent-less.
        val previewPanes = workspaces.mapNotNull { ws ->
            val candidates = panesByWorkspace[ws.id].orEmpty()
            val chosen = candidates.firstOrNull { agents[it.id]?.state == AgentState.BLOCKED }
                ?: candidates.firstOrNull { agents[it.id] != null && it.focused }
                ?: candidates.firstOrNull { agents[it.id] != null }
                ?: candidates.firstOrNull { it.focused }
                ?: candidates.firstOrNull()
            chosen?.let { ws.id to it }
        }.toMap()

        val overviews = workspaces.map { ws ->
            val pane = previewPanes[ws.id]
            WorkspaceOverview(
                workspace = ws,
                previewTerminalId = pane?.terminalId,
                agent = pane?.id?.let { agents[it] },
            )
        }
        return SnapshotResult.Success(overviews)
    }

    /**
     * The interactive command to type into a PTY shell to attach to [terminalId].
     *
     * Always passes `--takeover`: flokk is a single-user client, so the only "other" client
     * that could already hold this terminal is a previous flokk attach whose channel died
     * without a clean detach (the app backgrounded, a network drop, a force-kill) — herdr
     * still refuses a fresh attach without it, with "terminal already has an attached
     * client; retry with --takeover". Reclaiming our own stale attachment is always correct
     * here, unlike in a multi-user setting where taking over could steal someone else's.
     */
    fun attachCommand(terminalId: String): String =
        // Positional TERMINAL_ID must come before --takeover: herdr's arg parser mis-handles
        // `--takeover <id>` and rejects the id as an unknown option in that order.
        herdr("terminal attach ${shellQuote(terminalId)} --takeover")

    /** `herdr workspace create` — returns the new workspace's id from the command's own JSON reply. */
    suspend fun createWorkspace(): Result<String> {
        val raw = runCatching { transport.execBatch(herdr("workspace create")) }
            .getOrElse { return Result.failure(it) }
        val json = HerdrJson.parseOrNull(raw) as? JsonObject
            ?: return Result.failure(IllegalStateException("unexpected output from `herdr workspace create`: ${raw.trim().take(200)}"))
        val id = HerdrJson.firstStringDeep(json, "workspace_id", "id")
            ?: return Result.failure(IllegalStateException("no workspace id in create response: ${raw.trim().take(200)}"))
        return Result.success(id)
    }

    /** `herdr workspace close <id>`. */
    suspend fun closeWorkspace(workspaceId: String): Result<Unit> =
        runCatching { transport.execBatch(herdr("workspace close ${shellQuote(workspaceId)}")) }.map { }

    private fun herdr(rest: String): String {
        val sessionEnv = profile.herdrSession
            ?.takeIf { it.isNotBlank() }
            ?.let { "HERDR_SESSION=${shellQuote(it)} " }
            ?: ""
        return "$sessionEnv$binary $rest"
    }
}
