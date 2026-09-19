package com.flokk.app.herdr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/**
 * Defensive JSON helpers for parsing `herdr ... --json` output.
 *
 * herdr's exact response envelope isn't pinned down in this app (its CLI reference doesn't
 * spell out full example payloads), so rather than hard-coding one shape, we search for the
 * first plausible array/field under a handful of likely wrapper keys. If herdr's real output
 * doesn't match, [HerdrCli] degrades to an empty section instead of crashing the poll loop.
 */
object HerdrJson {

    private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseOrNull(text: String): JsonElement? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { lenientJson.parseToJsonElement(trimmed) }.getOrNull()
    }

    private val wrapperKeys = listOf("result", "data", "response", "payload")
    private val arrayKeys = listOf("workspaces", "panes", "agents", "tabs", "items", "list", "entries")

    /** Finds the first JSON array reachable from [root] via common wrapper/collection keys. */
    fun extractArray(root: JsonElement?): List<JsonObject> {
        if (root == null) return emptyList()
        val found = searchArray(root, depth = 0) ?: return emptyList()
        return found.mapNotNull { it as? JsonObject }
    }

    private fun searchArray(element: JsonElement, depth: Int): JsonArray? {
        if (depth > 4) return null
        if (element is JsonArray) return element
        val obj = element as? JsonObject ?: return null
        for (key in arrayKeys) {
            (obj[key] as? JsonArray)?.let { return it }
        }
        for (key in wrapperKeys) {
            obj[key]?.let { child -> searchArray(child, depth + 1)?.let { return it } }
        }
        // Last resort: first array found anywhere at this level or one level down.
        for (value in obj.values) {
            if (value is JsonArray) return value
        }
        for (value in obj.values) {
            if (value is JsonObject) searchArray(value, depth + 1)?.let { return it }
        }
        return null
    }

    fun firstString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj[key] ?: continue
            val primitive = value as? JsonPrimitive ?: continue
            if (!primitive.isString && primitive.content.isEmpty()) continue
            return primitive.content
        }
        return null
    }

    fun firstBoolean(obj: JsonObject, vararg keys: String): Boolean {
        for (key in keys) {
            val value = obj[key] ?: continue
            return runCatching { value.jsonPrimitive.boolean }.getOrDefault(false)
        }
        return false
    }

    /**
     * Best-effort nested lookup, e.g. for `{"result": {"workspace": {"workspace_id": "..."}}}`.
     *
     * Searches the whole tree for each key in turn — rather than checking every key at one
     * level before descending — so a specific key nested deep (`workspace_id`) is found ahead
     * of a generic one that happens to sit at the root (herdr's own request-echo `id`, present
     * on every response envelope, which would otherwise win by sitting closer to the top).
     */
    fun firstStringDeep(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            findStringDeep(obj, key)?.let { return it }
        }
        return null
    }

    private fun findStringDeep(obj: JsonObject, key: String): String? {
        firstString(obj, key)?.let { return it }
        for (value in obj.values) {
            val child = value as? JsonObject ?: continue
            findStringDeep(child, key)?.let { return it }
        }
        return null
    }
}
