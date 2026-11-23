package com.limelight.ps

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** A console that answered a search (2.2). */
data class PsHost(
    val address: InetAddress,
    val ps5: Boolean,
    /** 200 on, 620 rest mode. */
    val status: Int,
    val hostId: String?,
    val name: String?,
    val systemVersion: String?,
    val protocolVersion: String?,
    val requestPort: Int,
    val runningApp: String?,
    /** The title id of the game or app in front (`running-app-titleid`), null when none runs or the console does not say. */
    val runningTitleId: String? = null,
) {
    val awake: Boolean get() = status == 200
    val standby: Boolean get() = status == 620
    val target: PsTarget get() = PsTarget.fromDiscovery(protocolVersion, systemVersion)
}

/** Discovery and wake over UDP 987 (PS4) and 9302 (PS5), section 2. Text messages, LF lines, one trailing NUL. */
object PsDiscovery {
    const val PS4_PORT = 987
    const val PS5_PORT = 9302
    const val PS4_PROTOCOL = "00020020"
    const val PS5_PROTOCOL = "00030010"

    fun searchMessage(ps5: Boolean): ByteArray = nul("SRCH * HTTP/1.1\ndevice-discovery-protocol-version:${if (ps5) PS5_PROTOCOL else PS4_PROTOCOL}\n")

    /** The wake message; [registKey] is the stored registration key (its ASCII hex text up to the first NUL). */
    fun wakeMessage(ps5: Boolean, registKey: ByteArray): ByteArray = nul(
        "WAKEUP * HTTP/1.1\nclient-type:vr\nauth-type:R\nmodel:w\napp-type:r\nuser-credential:${credential(registKey)}\n" +
            "device-discovery-protocol-version:${if (ps5) PS5_PROTOCOL else PS4_PROTOCOL}\n",
    )

    /** The registration key text (≤ 8 hex characters up to the first NUL) read as a number and written in decimal (2.3). */
    fun credential(registKey: ByteArray): String {
        val end = registKey.indexOf(0).let { if (it < 0) registKey.size else it }
        val text = String(registKey, 0, minOf(end, 8), Charsets.US_ASCII)
        return java.lang.Long.parseLong(text, 16).toString()
    }

    /** Reads an answer; null when it is not a discovery answer. */
    fun parse(address: InetAddress, data: ByteArray, length: Int = data.size): PsHost? {
        val text = String(data, 0, length, Charsets.ISO_8859_1).trimEnd('\u0000')
        val lines = text.split('\n').map { it.trimEnd('\r') }
        val status = Regex("""^HTTP/1\.1 (\d{3})""").find(lines.firstOrNull() ?: return null)?.groupValues?.get(1)?.toInt() ?: return null
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        val protocol = headers["device-discovery-protocol-version"]
        val type = headers["host-type"]
        return PsHost(
            address, ps5 = protocol == PS5_PROTOCOL || type.equals("PS5", ignoreCase = true), status = status,
            hostId = headers["host-id"], name = headers["host-name"], systemVersion = headers["system-version"],
            protocolVersion = protocol, requestPort = headers["host-request-port"]?.toIntOrNull() ?: 9295,
            runningApp = headers["running-app-name"], runningTitleId = headers["running-app-titleid"]?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Sends a search to [target] (a console or the broadcast address) for both PS4 and PS5 and collects the answers for
     * [timeoutMs], or until an answer satisfies [stopWhen]. Blocking; call it off the main thread.
     */
    fun search(target: InetAddress, timeoutMs: Int = 2000, stopWhen: (PsHost) -> Boolean = { false }): List<PsHost> {
        val socket = bindPreferred() ?: return emptyList()
        socket.use { s ->
            s.broadcast = true
            s.soTimeout = 200
            val ps5 = searchMessage(true)
            val ps4 = searchMessage(false)
            val found = LinkedHashMap<String, PsHost>()
            val until = System.nanoTime() + timeoutMs * 1_000_000L
            var nextSend = 0L
            val buf = ByteArray(2048)
            while (System.nanoTime() < until) {
                if (System.nanoTime() >= nextSend) {
                    s.send(DatagramPacket(ps5, ps5.size, target, PS5_PORT))
                    s.send(DatagramPacket(ps4, ps4.size, target, PS4_PORT))
                    nextSend = System.nanoTime() + 500_000_000L
                }
                val packet = DatagramPacket(buf, buf.size)
                try {
                    s.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val host = parse(packet.address, packet.data, packet.length) ?: continue
                found[host.hostId ?: host.address.hostAddress.orEmpty()] = host
                if (stopWhen(host)) break
            }
            return found.values.toList()
        }
    }

    /** Sends the wake message a few times to the console's discovery port. */
    fun wake(host: InetAddress, ps5: Boolean, registKey: ByteArray) {
        val message = wakeMessage(ps5, registKey)
        (bindPreferred() ?: return).use { s ->
            repeat(3) { s.send(DatagramPacket(message, message.size, host, if (ps5) PS5_PORT else PS4_PORT)) }
        }
    }

    /** The preferred local ports 9303..9319, then any port [OBS 1.2]. */
    private fun bindPreferred(): DatagramSocket? {
        for (port in 9303..9319) runCatching { return DatagramSocket(port) }
        return runCatching { DatagramSocket() }.getOrNull()
    }

    private fun nul(text: String) = text.toByteArray(Charsets.US_ASCII) + 0.toByte()
}
