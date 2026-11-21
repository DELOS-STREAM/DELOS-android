package com.limelight.ps

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.util.Base64

/** An HTTP-style response the console sends: status, headers (lower-case names), and whatever bytes followed the header. */
class PsHttpResponse(val status: Int, val headers: Map<String, String>, val rest: ByteArray) {
    fun header(name: String): String? = headers[name.lowercase()]
    val reason: Long? get() = header("RP-Application-Reason")?.let { it.removePrefix("0x").toLongOrNull(16) }
}

internal object PsHttp {
    /** Reads up to the blank line; the bytes already read past it are kept in [PsHttpResponse.rest]. */
    fun readResponse(input: InputStream): PsHttpResponse {
        val buf = ByteArrayOutputStream()
        val one = ByteArray(1)
        while (true) {
            if (input.read(one) < 0) throw java.io.EOFException("closed before the response header")
            buf.write(one[0].toInt())
            val b = buf.toByteArray()
            if (b.size >= 4 && b[b.size - 4] == '\r'.code.toByte() && b[b.size - 3] == '\n'.code.toByte() && b[b.size - 2] == '\r'.code.toByte() && b[b.size - 1] == '\n'.code.toByte()) break
            if (b.size > 16384) throw java.io.IOException("response header too long")
        }
        val lines = buf.toString(Charsets.ISO_8859_1).split("\r\n")
        val status = Regex("""^HTTP/1\.[01] (\d{3})""").find(lines.first())?.groupValues?.get(1)?.toInt() ?: throw java.io.IOException("bad status line: ${lines.first()}")
        val headers = lines.drop(1).mapNotNull { l -> l.indexOf(':').takeIf { it > 0 }?.let { l.substring(0, it).trim().lowercase() to l.substring(it + 1).trim() } }.toMap()
        return PsHttpResponse(status, headers, ByteArray(0))
    }

    fun readBody(input: InputStream, length: Int): ByteArray {
        val out = ByteArray(length)
        var n = 0
        while (n < length) { val r = input.read(out, n, length - n); if (r < 0) break; n += r }
        return out.copyOf(n)
    }
}

class PsException(message: String, val reason: Long? = null) : Exception(message)

/** Registration (3.3–3.5). Blocking; run it off the main thread while the console shows its pairing screen. */
object PsRegistrationClient {
    fun register(host: InetAddress, port: Int, target: PsTarget, accountId: ByteArray, pin: Int): PsRegisteredConsole {
        searchRegistration(host, target)
        Thread.sleep(100)
        val request = PsRegistrationRequest(target, accountId, pin)
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 3000)
            socket.soTimeout = 3000
            socket.getOutputStream().apply { write(request.bytes()); flush() }
            val input = socket.getInputStream()
            val response = PsHttp.readResponse(input)
            if (response.status != 200) throw PsException("registration refused: ${PsRegistError.of(response.reason ?: 0)}", response.reason)
            val length = response.header("Content-Length")?.toIntOrNull() ?: throw PsException("registration answer without a length")
            return request.parseBody(PsHttp.readBody(input, length)) ?: throw PsException("registration answer is missing a key")
        }
    }

    /** SRC2/SRC3 to UDP 9295, waits for RES2/RES3; a silent console has no pairing screen open. */
    private fun searchRegistration(host: InetAddress, target: PsTarget) {
        val message = (if (target.ps5) "SRC3" else "SRC2").toByteArray(Charsets.US_ASCII) + 0.toByte()
        val answer = if (target.ps5) "RES3" else "RES2"
        DatagramSocket().use { s ->
            s.soTimeout = 3000
            s.send(DatagramPacket(message, message.size, host, 9295))
            val buf = ByteArray(1024)
            val until = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < until) {
                val p = DatagramPacket(buf, buf.size)
                try { s.receive(p) } catch (_: SocketTimeoutException) { break }
                if (String(buf, 0, minOf(4, p.length), Charsets.US_ASCII) == answer) return
            }
        }
        throw PsException("the console does not answer: open Settings › System › Remote Play › Pair Device on it")
    }
}

/**
 * The ctrl channel (4.1–4.5): session init for the nonce, then the ctrl request and its binary messages. Holds the TCP socket for
 * the whole session; [readLoop] answers heartbeats and reports the session id.
 */
class PsCtrl(private val host: InetAddress, private val port: Int, val console: PsRegisteredConsole, private val codec: PsCodec) : AutoCloseable {
    lateinit var crypt: RpCrypt
        private set
    private var socket: Socket? = null
    private var localCounter = 0L
    private var remoteCounter = 0L
    var serverType: Int = -1
        private set

    /** Session init then ctrl; returns after the console accepted the ctrl request. */
    fun open(did: ByteArray = PsSession.newDid()) {
        val target = console.target
        val nonce = Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 5000)
            s.soTimeout = 5000
            val path = if (target.pre10) "/sce/rp/session" else "${target.sessionPrefix}init"
            val head = "GET $path HTTP/1.1\r\nHost: ${host.hostAddress}:$port\r\nUser-Agent: remoteplay Windows\r\nConnection: close\r\nContent-Length: 0\r\n" +
                "RP-Registkey: ${PsSession.registKeyHeader(console.registKey)}\r\nRp-Version: ${target.rpVersion}\r\n\r\n"
            s.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); flush() }
            val r = PsHttp.readResponse(s.getInputStream())
            if (r.status != 200) throw PsException("session refused (${r.status}, reason ${r.reason?.toString(16)}, version ${r.header("RP-Version")})", r.reason)
            Base64.getDecoder().decode(r.header("RP-Nonce") ?: throw PsException("no nonce")).also { if (it.size != 16) throw PsException("nonce size ${it.size}") }
        }
        crypt = RpCrypt.session(target, nonce, console.morning)
        Thread.sleep(10)
        val (headers, next) = PsSession.ctrlHeaders(crypt, console.registKey, did, codec)
        localCounter = next
        val values = headers.toMap()
        val path = if (target.pre10) "/sce/rp/session/ctrl" else "${target.sessionPrefix}ctrl"
        val head = buildString {
            append("GET $path HTTP/1.1\r\nHost: ${host.hostAddress}:$port\r\nUser-Agent: remoteplay Windows\r\nConnection: keep-alive\r\nContent-Length: 0\r\n")
            append("RP-Auth: ${values["RP-Auth"]}\r\nRP-Version: ${target.rpVersion}\r\nRP-Did: ${values["RP-Did"]}\r\nRP-ControllerType: 3\r\nRP-ClientType: 11\r\n")
            append("RP-OSType: ${values["RP-OSType"]}\r\nRP-ConPath: 1\r\n")
            if (target.ps5) append("RP-PadProcNo: 3\r\n")
            values["RP-StartBitrate"]?.let { append("RP-StartBitrate: $it\r\n") }
            values["RP-StreamingType"]?.let { append("RP-StreamingType: $it\r\n") }
            append("\r\n")
        }
        val s = Socket()
        s.connect(InetSocketAddress(host, port), 5000)
        s.soTimeout = 5000
        s.tcpNoDelay = true
        s.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); flush() }
        val r = PsHttp.readResponse(s.getInputStream())
        if (r.status != 200) { s.close(); throw PsException("ctrl refused (${r.status}, reason ${r.reason?.toString(16)})", r.reason) }
        r.header("RP-Server-Type")?.let { serverType = u(crypt.decrypt(0, Base64.getDecoder().decode(it))[0]); remoteCounter = 1 }
        if (r.header("RP-Prohibit") == "1") { s.close(); throw PsException("this content cannot be streamed") }
        socket = s
    }

    fun send(type: Int, payload: ByteArray = ByteArray(0)) {
        val body = if (payload.isEmpty()) payload else crypt.encrypt(localCounter++, payload)
        val out = ByteBuffer.allocate(8 + body.size).putInt(body.size).putShort(type.toShort()).putShort(0).put(body).array()
        synchronized(this) { socket?.getOutputStream()?.apply { write(out); flush() } }
    }

    /** Reads ctrl messages until the socket closes. Heartbeats are answered here; everything else goes to [onMessage]. */
    fun readLoop(onMessage: (type: Int, payload: ByteArray) -> Unit) {
        val input = socket?.getInputStream() ?: return
        socket?.soTimeout = 0
        val header = ByteArray(8)
        while (true) {
            if (PsHttp.readBody(input, 8).also { it.copyInto(header) }.size < 8) return
            val b = ByteBuffer.wrap(header)
            val length = b.int
            val type = b.short.toInt() and 0xFFFF
            val raw = PsHttp.readBody(input, length)
            val payload = if (raw.isEmpty()) raw else crypt.decrypt(remoteCounter++, raw)
            if (type == HEARTBEAT_REQ) send(HEARTBEAT_REP) else onMessage(type, payload)
        }
    }

    override fun close() { runCatching { socket?.close() }; socket = null }

    companion object {
        const val SESSION_ID = 0x0033
        const val HEARTBEAT_REQ = 0x00FE
        const val HEARTBEAT_REP = 0x01FE
        const val LOGIN_PIN_REQ = 0x0004
        const val GOTO_BED = 0x0050
        const val MIC_TOGGLE = 0x0036
        const val DISPLAY_DEVICES = 0x0910
        const val ENABLE_DUALSENSE = 0x0013
        const val DUALSENSE_CONNECT = 0x0011

        /** The session id payload: byte 0 is a marker, then the ASCII id (4.5). */
        fun sessionId(payload: ByteArray): String = String(payload, 1, payload.size - 1, Charsets.US_ASCII).trimEnd('\u0000')
    }
}
