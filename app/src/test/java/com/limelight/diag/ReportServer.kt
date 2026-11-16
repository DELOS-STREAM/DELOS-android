package com.limelight.diag

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/** A request as the loopback server read it. */
class SeenRequest(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray) {
    /** The body, gunzipped when the request says it is gzip. */
    val text: String
        get() = (if (headers["content-encoding"] == "gzip") GZIPInputStream(body.inputStream()).readBytes() else body).toString(Charsets.UTF_8)
}

/** What the loopback server answers; [hang] reads the request and then says nothing until the server is closed. */
class ServerAnswer(val status: Int = 200, val body: String = "", val headers: Map<String, String> = emptyMap(), val hang: Boolean = false)

/**
 * A real HTTP server on 127.0.0.1 for the tests of the report upload (rule T6: no network beyond loopback). Every request is
 * recorded; [answer] decides what each one gets.
 */
class ReportServer(private val answer: (SeenRequest) -> ServerAnswer) : Closeable {
    private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
    val requests = CopyOnWriteArrayList<SeenRequest>()
    val url: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        Thread {
            while (!socket.isClosed) {
                try {
                    val peer = socket.accept()
                    Thread { serve(peer) }.apply { isDaemon = true }.start()
                } catch (_: IOException) {
                    // the closed server socket
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun serve(peer: Socket) = peer.use {
        try {
            val input = peer.getInputStream()
            fun line(): String {
                val out = ByteArrayOutputStream()
                while (true) {
                    val b = input.read()
                    if (b < 0 || b == '\n'.code) break
                    if (b != '\r'.code) out.write(b)
                }
                return out.toString(Charsets.ISO_8859_1)
            }
            val first = line().split(' ')
            val headers = HashMap<String, String>()
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                headers[header.substringBefore(':').trim().lowercase()] = header.substringAfter(':').trim()
            }
            val body = input.readNBytes(headers["content-length"]?.toIntOrNull() ?: 0)
            val request = SeenRequest(first[0], first.getOrElse(1) { "" }, headers, body)
            requests += request
            val reply = answer(request)
            if (reply.hang) {
                while (!socket.isClosed) Thread.sleep(20)
                return@use
            }
            val bytes = reply.body.toByteArray()
            val extra = reply.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }
            peer.getOutputStream().apply {
                write("HTTP/1.1 ${reply.status} X\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n$extra\r\n".toByteArray())
                write(bytes)
                flush()
            }
        } catch (_: IOException) {
            // the client gave up
        }
    }

    override fun close() = socket.close()
}
