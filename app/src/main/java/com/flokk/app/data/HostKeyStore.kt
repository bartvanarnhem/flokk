package com.flokk.app.data

import android.content.Context

/**
 * Trust-on-first-use store for SSH host key fingerprints, keyed by "host:port".
 *
 * The first successful connection to a given host pins its fingerprint; any later
 * connection presenting a different key for the same host:port is refused rather than
 * silently accepted, which is the same trade-off `ssh` itself makes with known_hosts.
 */
class HostKeyStore(context: Context) {

    private val prefs = encryptedPrefs(context, "flokk_known_hosts")

    fun knownFingerprint(host: String, port: Int): String? = prefs.getString(key(host, port), null)

    fun pin(host: String, port: Int, fingerprint: String) {
        prefs.edit().putString(key(host, port), fingerprint).apply()
    }

    private fun key(host: String, port: Int) = "$host:$port"
}
