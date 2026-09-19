package com.flokk.app.herdr

/** One `herdr workspace list` entry. */
data class HerdrWorkspace(
    val id: String,
    val label: String,
    val cwd: String?,
)

/** One `herdr pane list` entry. */
data class HerdrPane(
    val id: String,
    val workspaceId: String?,
    val focused: Boolean,
    /** The id `herdr terminal attach` expects — distinct from [id] (the pane id). */
    val terminalId: String,
)

enum class AgentState { IDLE, WORKING, BLOCKED, DONE, UNKNOWN;

    companion object {
        fun parse(raw: String?): AgentState = when (raw?.lowercase()) {
            "idle" -> IDLE
            "working" -> WORKING
            "blocked" -> BLOCKED
            "done" -> DONE
            else -> UNKNOWN
        }
    }
}

/** One `herdr agent list` entry, keyed by the pane it runs in. */
data class HerdrAgent(
    val paneId: String,
    val kind: String?,
    val state: AgentState,
)

/**
 * Everything the UI needs for one workspace: the workspace, the pane it attaches to,
 * and the coding-agent status running in that pane, if any.
 */
data class WorkspaceOverview(
    val workspace: HerdrWorkspace,
    /** The id to pass to `herdr terminal attach`, if a preview pane was found. */
    val previewTerminalId: String?,
    val agent: HerdrAgent?,
)

sealed interface SnapshotResult {
    data class Success(val workspaces: List<WorkspaceOverview>) : SnapshotResult
    data class Failure(val message: String) : SnapshotResult
}
