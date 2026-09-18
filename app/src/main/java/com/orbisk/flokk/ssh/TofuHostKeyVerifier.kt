package com.orbisk.flokk.ssh

import com.orbisk.flokk.data.HostKeyStore
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.PublicKey

/** Thrown by [TofuHostKeyVerifier] when a host presents a key that doesn't match the pinned one. */
class HostKeyChangedException(host: String, port: Int) :
    Exception("Host key for $host:$port changed since it was first trusted. Refusing to connect.")

/**
 * Trust-on-first-use verifier backed by [HostKeyStore]. Pins the fingerprint on first
 * connect; on every later connect, the presented key must match exactly.
 */
class TofuHostKeyVerifier(private val store: HostKeyStore) : HostKeyVerifier {

    var lastMismatch: HostKeyChangedException? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = SecurityUtils.getFingerprint(key)
        val known = store.knownFingerprint(hostname, port)
        if (known == null) {
            store.pin(hostname, port, fingerprint)
            return true
        }
        if (known == fingerprint) return true
        lastMismatch = HostKeyChangedException(hostname, port)
        return false
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
}
