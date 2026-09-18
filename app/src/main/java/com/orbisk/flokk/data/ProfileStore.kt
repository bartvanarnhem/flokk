package com.orbisk.flokk.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists an address book of [ConnectionProfile]s in an AES-256-GCM-encrypted,
 * Keystore-backed SharedPreferences file, so re-opening the app doesn't require
 * re-adding every host. All profiles are serialized as one JSON array under a single key
 * — this is a handful of small records, not a dataset that needs per-row storage.
 */
class ProfileStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = encryptedPrefs(context, "flokk_secure_profiles")

    fun listProfiles(): List<ConnectionProfile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ConnectionProfile>>(raw) }.getOrDefault(emptyList())
    }

    fun getProfile(id: String): ConnectionProfile? = listProfiles().firstOrNull { it.id == id }

    /** Inserts [profile], or replaces the existing entry with the same [ConnectionProfile.id]. */
    fun saveProfile(profile: ConnectionProfile) {
        val updated = listProfiles().filterNot { it.id == profile.id } + profile
        persist(updated)
    }

    fun deleteProfile(id: String) {
        persist(listProfiles().filterNot { it.id == id })
    }

    /** Stamps [id]'s profile with "now" after a successful connect, for a "last seen" hint. */
    fun touchConnected(id: String) {
        val profile = getProfile(id) ?: return
        saveProfile(profile.copy(lastConnectedAt = System.currentTimeMillis()))
    }

    private fun persist(profiles: List<ConnectionProfile>) {
        prefs.edit().putString(KEY_PROFILES, json.encodeToString(profiles)).apply()
    }

    private companion object {
        const val KEY_PROFILES = "profiles"
    }
}
