package com.orbisk.flokk.ssh

import com.orbisk.flokk.data.AuthMethod
import com.orbisk.flokk.data.ConnectionProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.compression.DelayedZlibCompression
import net.schmizz.sshj.transport.compression.NoneCompression
import net.schmizz.sshj.transport.compression.ZlibCompression
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Thrown with a message meant to be read as-is by a user, wrapping the real cause for logs/debugging. */
class FriendlyConnectException(message: String, cause: Throwable) : Exception(message, cause)

/**
 * Owns a single authenticated SSH connection and multiplexes short-lived exec channels
 * (for polling `herdr ... --json`) and long-lived PTY channels (for an attached terminal)
 * over it, exactly like an `ssh` client would. Optionally tunnels through one jump/bastion
 * host, chained via sshj's `connectVia` (the same mechanism as OpenSSH's `ProxyJump`).
 */
class SshTransport(private val hostKeyVerifier: TofuHostKeyVerifier) {

    @Volatile private var client: SSHClient? = null
    @Volatile private var jumpClient: SSHClient? = null

    // sshj's Connection is not safe to open/close channels on from more than one thread at
    // once: opening the attach PTY on a UI-driven coroutine while the poll loop's execBatch
    // (or a previous attach's teardown) is also touching the connection on Dispatchers.IO
    // corrupts its shared state and kills the whole transport, not just one channel — surfaced
    // as sshj's TransportException("Broken transport; encountered EOF") on the next call.
    // Serializing every open/close through one lock is the fix; reading/writing an
    // already-open channel's own streams afterward doesn't need it.
    private val sessionLock = Any()

    private val openChannels = AtomicInteger(0)

    /** Channels currently multiplexed over the one TCP connection — exec calls plus attach PTYs. */
    val activeChannelCount: Int get() = openChannels.get()

    val isConnected: Boolean get() = client?.let { it.isConnected && it.isAuthenticated } == true

    /** [via], if given, is dialed and authenticated first; [profile] is then reached through it. */
    suspend fun connect(profile: ConnectionProfile, via: ConnectionProfile? = null) = withContext(Dispatchers.IO) {
        disconnect()
        var jump: SSHClient? = null
        try {
            jump = via?.let { dialAndAuth(it) }
            client = dialAndAuth(profile, through = jump)
            jumpClient = jump
        } catch (t: Throwable) {
            runCatching { jump?.disconnect() }
            throw t
        }
    }

    private fun dialAndAuth(profile: ConnectionProfile, through: SSHClient? = null): SSHClient {
        val ssh = createSshClient(hostKeyVerifier)
        try {
            if (through != null) {
                ssh.connectVia(through.newDirectConnection(profile.host, profile.port))
            } else {
                ssh.connect(profile.host, profile.port)
                disableNagle(ssh)
            }
        } catch (t: Throwable) {
            hostKeyVerifier.lastMismatch?.let { throw it }
            throw networkFailure(t, profile.host, profile.port)
        }
        try {
            authenticate(ssh, profile)
        } catch (t: Throwable) {
            runCatching { ssh.disconnect() }
            throw authFailure(t)
        }
        return ssh
    }

    /**
     * Maps the low-level exceptions [SSHClient.connect] throws (bare [UnknownHostException]s
     * whose `.message` is just the hostname, `ConnectException`s that only say "Connection
     * refused", etc.) to a sentence a user can act on, without losing the original for logs.
     */
    private fun networkFailure(t: Throwable, host: String, port: Int): FriendlyConnectException {
        val message = when (t) {
            is UnknownHostException ->
                "Can't find host \"$host\" — check the address, and that this device can reach it " +
                    "(same Wi-Fi or VPN as the server?)."
            is ConnectException ->
                "Connection refused by $host:$port — is the SSH server running there and is the " +
                    "port right?"
            is SocketTimeoutException ->
                "Timed out connecting to $host:$port — check the address and your network connection."
            else -> t.message ?: t.javaClass.simpleName
        }
        return FriendlyConnectException(message, t)
    }

    private fun authFailure(t: Throwable): FriendlyConnectException {
        val message = when (t) {
            is UserAuthException -> "Login rejected — check the username and password/key."
            else -> t.message ?: t.javaClass.simpleName
        }
        return FriendlyConnectException(message, t)
    }

    /** Runs [command] to completion on a fresh exec channel and returns its stdout. */
    suspend fun execBatch(command: String, timeoutSeconds: Long = 20): String = withContext(Dispatchers.IO) {
        val ssh = client ?: error("Not connected")
        // Only the moments that mutate the connection's shared channel table — opening and
        // closing — need the lock. Holding it across the whole round trip (as a first pass at
        // this fix did) serializes every poll behind a live attach's setup/teardown and vice
        // versa, and a slow remote command then blocks the other side for as long as
        // `timeoutSeconds` — long enough for the connection to die for unrelated reasons while
        // waiting. Reading the command's output happens on this channel's own stream and
        // doesn't touch shared state, so it's fine to run unlocked.
        val session = synchronized(sessionLock) { ssh.startSession() }
        openChannels.incrementAndGet()
        try {
            // A plain SSH "exec" channel gets a non-interactive, non-login shell, which
            // skips ~/.bashrc entirely — exactly where most users' PATH additions (e.g.
            // for a ~/.local/bin install) live. Re-invoke bash with -i so it's considered
            // interactive (bypassing the `[[ $- != *i* ]] && return` guard most distros'
            // default ~/.bashrc starts with) and sources it before running the real command.
            val cmd = session.exec("bash -ic ${shellQuote(command)}")
            val out = readAll(cmd.inputStream)
            runCatching { cmd.join(timeoutSeconds, TimeUnit.SECONDS) }
            out
        } finally {
            synchronized(sessionLock) { runCatching { session.close() } }
            openChannels.decrementAndGet()
        }
    }

    /**
     * Opens an interactive PTY + login shell and types [attachCommand] into it, mirroring
     * what a user does over a plain `ssh -t host` connection.
     */
    suspend fun openAttach(attachCommand: String, cols: Int, rows: Int): AttachChannel =
        withContext(Dispatchers.IO) {
            val ssh = client ?: error("Not connected")
            // Only the setup needs the lock — once opened, this channel's own streams are
            // read/written independently of anything else happening on the connection.
            synchronized(sessionLock) {
                val session = ssh.startSession()
                openChannels.incrementAndGet()
                session.allocatePTY("xterm-256color", cols, rows, 0, 0, emptyMap())
                val shell = session.startShell()
                shell.outputStream.write("$attachCommand\n".toByteArray(Charsets.UTF_8))
                shell.outputStream.flush()
                AttachChannel(session, shell, sessionLock, onClose = { openChannels.decrementAndGet() })
            }
        }

    fun disconnect() {
        val ssh = client
        val jump = jumpClient
        client = null
        jumpClient = null
        openChannels.set(0)
        runCatching { ssh?.disconnect() }
        runCatching { jump?.disconnect() }
    }

    private fun readAll(input: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
        }
        return buffer.toString("UTF-8")
    }

    companion object {
        /**
         * A one-off, password-authenticated connection used only to append [publicKeyLine] to
         * `~/.ssh/authorized_keys` on a host (the mobile equivalent of `ssh-copy-id`). Doesn't
         * touch the shared persistent connection — dials its own client and tears it down.
         */
        suspend fun installPublicKey(
            host: String,
            port: Int,
            username: String,
            password: String,
            publicKeyLine: String,
            hostKeyVerifier: TofuHostKeyVerifier,
        ): Result<Unit> = withContext(Dispatchers.IO) {
            runCatching {
                val ssh = createSshClient(hostKeyVerifier)
                try {
                    ssh.connect(host, port)
                    disableNagle(ssh)
                    ssh.authPassword(username, password)
                    val session = ssh.startSession()
                    try {
                        val quoted = shellQuote(publicKeyLine)
                        val cmd = session.exec(
                            "mkdir -p ~/.ssh && chmod 700 ~/.ssh && " +
                                "(grep -qxF $quoted ~/.ssh/authorized_keys 2>/dev/null || " +
                                "echo $quoted >> ~/.ssh/authorized_keys) && " +
                                "chmod 600 ~/.ssh/authorized_keys",
                        )
                        cmd.join(20, TimeUnit.SECONDS)
                        check(cmd.exitStatus == 0) { "remote command exited ${cmd.exitStatus}" }
                    } finally {
                        runCatching { session.close() }
                    }
                } finally {
                    runCatching { ssh.disconnect() }
                }
            }
        }
    }
}

private fun createSshClient(hostKeyVerifier: TofuHostKeyVerifier): SSHClient {
    val config = DefaultConfig().apply {
        compressionFactories = listOf(
            DelayedZlibCompression.Factory(),
            ZlibCompression.Factory(),
            NoneCompression.Factory(),
        )
    }
    return SSHClient(config).apply {
        addHostKeyVerifier(hostKeyVerifier)
        connection.keepAlive.keepAliveInterval = 15
        connectTimeout = 10_000
        timeout = 15_000
    }
}

private fun authenticate(ssh: SSHClient, profile: ConnectionProfile) {
    when (val auth = profile.auth) {
        is AuthMethod.Password -> ssh.authPassword(profile.username, auth.password)
        is AuthMethod.PrivateKey -> {
            val passwordFinder = auth.passphrase
                ?.takeIf { it.isNotEmpty() }
                ?.let { PasswordUtils.createOneOff(it.toCharArray()) }
            val keyProvider = ssh.loadKeys(auth.pem, null, passwordFinder)
            ssh.authPublickey(profile.username, keyProvider)
        }
    }
}

/**
 * Disables Nagle's algorithm on [ssh]'s underlying socket — the same thing a real `ssh`
 * client does for every connection. Without this, each keystroke-sized write can sit
 * buffered behind Nagle (which holds small writes back hoping to coalesce them) crossed
 * with the remote end's delayed-ACK timer, adding tens to a couple hundred milliseconds
 * of pure buffering on top of the network's actual round trip — the classic cause of
 * typing over SSH feeling laggy even on a fast link. A no-op (not an error) when there's
 * no real socket to tune, e.g. a `connectVia`-tunneled hop.
 */
private fun disableNagle(ssh: SSHClient) {
    runCatching { ssh.socket?.tcpNoDelay = true }
}
