package com.flokk.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Terminal font size presets. Pinch-to-zoom still adjusts freely from whichever is picked. */
enum class FontSizePreset(val label: String, val sp: Float) {
    SMALL("small", 11f),
    STANDARD("standard", 13f),
    BIG("big", 16f),
}

/** How the workspace tab strip is laid out. */
enum class TabLayout(val label: String) {
    /** One scrollable row of every workspace, most recently used first. */
    SINGLE_ROW("single row"),

    /** One scrollable row per host, with compact one-line tiles. */
    GROUP_BY_HOST("group by host"),
}

data class AppSettings(
    val fontSize: FontSizePreset = FontSizePreset.STANDARD,
    val tabLayout: TabLayout = TabLayout.SINGLE_ROW,
)

/**
 * User-facing app settings, persisted in plain prefs (nothing sensitive) and exposed as a
 * [StateFlow] so a change made in the settings dialog applies immediately.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("flokk_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit()
            .putString(KEY_FONT_SIZE, updated.fontSize.name)
            .putString(KEY_TAB_LAYOUT, updated.tabLayout.name)
            .apply()
    }

    private fun load(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            fontSize = enumOrDefault(prefs.getString(KEY_FONT_SIZE, null), defaults.fontSize),
            tabLayout = enumOrDefault(prefs.getString(KEY_TAB_LAYOUT, null), defaults.tabLayout),
        )
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_TAB_LAYOUT = "tab_layout"
    }
}
