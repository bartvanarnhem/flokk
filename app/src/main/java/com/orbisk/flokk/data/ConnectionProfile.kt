package com.orbisk.flokk.data

import kotlinx.serialization.Serializable

/**
 * Everything needed to open an SSH connection to a host running the herdr server, plus
 * enough identity ([id]/[name]) to be one entry in a saved address book of hosts.
 *
 * [herdrSession] mirrors `herdr --session <name>`: leave null/blank for the default session.
 */
@Serializable
data class ConnectionProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val auth: AuthMethod,
    val herdrSession: String? = null,
    val herdrBinary: String = "herdr",
    /** [ConnectionProfile.id] of another saved profile to tunnel through (an SSH jump/bastion host), if any. */
    val jumpHostId: String? = null,
    val lastConnectedAt: Long? = null,
    /** Connect to this host automatically when the app starts, alongside every other profile
     *  with this set — flokk can hold several hosts connected at once. */
    val autoConnect: Boolean = false,
) {
    val target: String get() = "$username@$host:$port"
    val displayName: String get() = name.ifBlank { target }
}

@Serializable
sealed interface AuthMethod {
    @Serializable
    data class Password(val password: String) : AuthMethod

    @Serializable
    data class PrivateKey(
        val pem: String,
        val passphrase: String? = null,
    ) : AuthMethod
}
