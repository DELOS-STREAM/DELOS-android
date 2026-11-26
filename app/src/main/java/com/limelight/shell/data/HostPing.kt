package com.limelight.shell.data

import com.limelight.nvstream.http.ComputerDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight pre-flight TCP RTT probe against a paired PC's control port. Works on all
 * unrooted Android / Android TV devices without requiring raw ICMP sockets.
 */
object HostPing {
    data class Result(val avgMs: Int, val jitterMs: Int, val samples: Int)

    fun targetFor(details: ComputerDetails): Pair<String, Int>? {
        val tuple = details.activeAddress
            ?: details.localAddress
            ?: details.manualAddress
            ?: details.remoteAddress
            ?: details.ipv6Address
            ?: return null
        val host = tuple.address?.takeIf { it.isNotBlank() } ?: return null
        val port = if (tuple.port > 0) tuple.port else 47989
        return host to port
    }

    suspend fun measure(host: String, port: Int, samples: Int = 3, timeoutMs: Int = 1000): Result? =
        withContext(Dispatchers.IO) {
            val rtts = ArrayList<Double>(samples)
            repeat(samples) {
                val ms = probeOnce(host, port, timeoutMs)
                if (ms != null) rtts += ms
            }
            if (rtts.isEmpty()) return@withContext null
            val avg = rtts.average()
            val jitter = if (rtts.size > 1) rtts.map { abs(it - avg) }.average() else 0.0
            Result(
                avgMs = avg.roundToInt().coerceAtLeast(1),
                jitterMs = jitter.roundToInt(),
                samples = rtts.size,
            )
        }

    internal fun probeOnce(host: String, port: Int, timeoutMs: Int): Double? = runCatching {
        Socket().use { socket ->
            val start = System.nanoTime()
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            (System.nanoTime() - start) / 1_000_000.0
        }
    }.getOrNull()
}
