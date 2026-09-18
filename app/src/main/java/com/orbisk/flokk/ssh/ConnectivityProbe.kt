package com.orbisk.flokk.ssh

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface ProbeResult {
    data class Reachable(val latencyMs: Long) : ProbeResult
    data class Unreachable(val reason: String) : ProbeResult
}

/**
 * A bare TCP connect — no SSH handshake, no auth — used to show a saved connection's live
 * up/down state and round-trip latency in the connections list before the user commits to
 * a full authenticated connect.
 */
object ConnectivityProbe {
    suspend fun probe(host: String, port: Int, timeoutMs: Int = 2_500): ProbeResult =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            try {
                Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs) }
                ProbeResult.Reachable((System.nanoTime() - start) / 1_000_000)
            } catch (t: UnknownHostException) {
                ProbeResult.Unreachable("can't resolve host")
            } catch (t: SocketTimeoutException) {
                ProbeResult.Unreachable("timed out")
            } catch (t: IOException) {
                ProbeResult.Unreachable(t.message ?: "unreachable")
            }
        }
}
