package com.flokk.app.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists the most-recently-selected order of workspace tabs (see `HostWorkspace.key`, a
 * "<profileId>:<workspaceId>" pair) across app restarts. Plain prefs, not encrypted: this is
 * just tab ordering, not credential material, and a stale key (a workspace that's since been
 * closed, or a host that's no longer saved) is harmless — the tab strip just never matches it
 * and falls back to natural order for that entry.
 */
class WorkspaceOrderStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("flokk_workspace_order", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = prefs.getString(KEY_ORDER, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    fun save(order: List<String>) {
        prefs.edit().putString(KEY_ORDER, json.encodeToString(order)).apply()
    }

    private companion object {
        const val KEY_ORDER = "order"
    }
}
