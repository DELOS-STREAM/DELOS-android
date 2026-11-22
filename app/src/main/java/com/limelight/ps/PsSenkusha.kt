package com.limelight.ps

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

/** Senkusha's results (6.4): the largest packets from ([mtuIn]) and to ([mtuOut]) the console and the round trip. */
internal data class PsNetwork(val mtuIn: Int, val mtuOut: Int, val rttMs: Int, val measured: Boolean) {
    companion object {
        /** The spec's values without a measurement (6.4 step 8). */
        val FALLBACK = PsNetwork(1454, 1454, 1, false)
    }
}

/**
 * The network test before the stream (spec 6.4, issue #122): Takion v7 on UDP 9297 without encryption, the protocol request
 * and an empty BIG, ten echo pings for the round trip, then binary searches for the largest packet in each direction. Any
 * failure, or more than [BUDGET_MS] in total, gives [PsNetwork.FALLBACK]: the stream starts as it did without Senkusha.
 * Single-threaded: every step sends, then receives (acking DATA, resending unacked DATA every 200 ms) until its answer or deadline.
 */
internal class PsSenkusha(private val host: InetAddress) : AutoCloseable {
    private val socket = DatagramSocket()
    private val rng = SecureRandom()
    private val ownTag = rng.nextInt().let { if (it == 0) 1 else it }
    private var consoleTag = 0
    private var sendTsn = ownTag
    private var expectedTsn = 0
    private var ackedTsn = ownTag - 1
    private val unacked = LinkedHashMap<Int, ByteArray>()
    private var resendAtNs = 0L
    private val messages = ArrayDeque<PsProtoMessage>()
    private val av = ArrayDeque<ByteArray>()
    private val budgetEndNs = System.nanoTime() + BUDGET_MS * 1_000_000L
    private val buf = ByteArray(0x19000)
    private var dontFragment = false

    fun run(): PsNetwork = try {
        measure().also { Log.i(TAG, "senkusha: rtt=${it.rttMs} ms, mtu in ${it.mtuIn}, mtu out ${it.mtuOut}") }
    } catch (e: Exception) {
        Log.i(TAG, "senkusha failed (${e.message}): MTU 1454, RTT 1 ms")
        PsNetwork.FALLBACK
    } finally {
        close()
    }

    private fun measure(): PsNetwork {
        socket.connect(InetSocketAddress(host, PORT))
        socket.receiveBufferSize = 0x19000
        dontFragment = dontFragment(socket)
        handshake()
        sendMessage(1, PsProtoWriter().uint(1, PsMsg.TAKIONPROTOCOLREQUEST.toLong())
            .message(31, PsProtoWriter().uint(1, 9)).toByteArray())
        awaitMessage(STEP_MS) { it.uint(1)?.toInt() == PsMsg.TAKIONPROTOCOLREQUESTACK } ?: throw PsException("no protocol request ack")
        sendMessage(1, PsProtoWriter().uint(1, PsMsg.BIG.toLong()).message(2, PsProtoWriter().uint(1, 9).string(2, "").string(3, "")
            .bytes(4, ByteArray(0))).toByteArray())
        awaitMessage(STEP_MS) { it.uint(1)?.toInt() == PsMsg.BANG } ?: throw PsException("no BANG")

        // Round trip: ECHO on, ten pings, ECHO off
        val echoOn = sendMessage(8, PsMsg.senkusha(ECHO, 2, PsProtoWriter().bool(1, true)))
        if (!awaitAck(echoOn, STEP_MS)) throw PsException("ECHO not acked")
        val samples = ArrayList<Long>()
        for (i in 0 until PINGS) {
            val tag = rng.nextInt()
            val sent = System.nanoTime()
            send(ping(i, tag, PING_SIZE, null))
            if (awaitAv(PING_WAIT_MS) { isPong(it, i, tag) }) samples += System.nanoTime() - sent
            else if (samples.isEmpty()) throw PsException("no pong")
        }
        sendMessage(8, PsMsg.senkusha(ECHO, 2, PsProtoWriter().bool(1, false)))
        val rttMs = maxOf(1L, samples.average().toLong() / 1_000_000L).toInt()
        val probeMs = (5L * rttMs).coerceIn(5L, 500L)

        // MTU in: the console sends a video packet of the requested size with the probe's id as its frame index
        var id = 1
        val mtuIn = bisect { candidate ->
            (0 until 3).any {
                val probe = id++
                sendMessage(8, PsMsg.senkusha(MTU, 3, PsProtoWriter().uint(1, probe.toLong()).uint(2, candidate.toLong()).uint(4, 1)))
                awaitAv(probeMs) { (it[0].toInt() and 0x0F) == 2 && frameIndex(it) == (probe and 0xFFFF) }
            }
        }

        // MTU out: pings of the candidate size, which only arrive whole with "don't fragment"
        var mtuOut = mtuIn
        sendMessage(8, PsMsg.senkusha(CLIENT_MTU, 5, PsProtoWriter().uint(1, 1).uint(2, mtuIn.toLong()).bool(3, true).uint(4, mtuIn.toLong())))
        awaitMessage(STEP_MS) { m -> m.uint(1)?.toInt() == PsMsg.SENKUSHA && m.message(14)?.let { it.uint(1)?.toInt() == CLIENT_MTU && it.message(5)?.uint(1) == 1L } == true }
            ?: throw PsException("no CLIENT_MTU answer")
        if (dontFragment) {
            var ping = PINGS
            mtuOut = bisect { candidate ->
                (0 until 3).any {
                    val tag = rng.nextInt()
                    val n = ping++
                    // Larger than the interface takes with don't-fragment: the send fails, the candidate does not work
                    if (runCatching { send(ping(n, tag, candidate - 28, FILLER)) }.isFailure) return@any false
                    awaitAv(probeMs) { isPong(it, n, tag) }
                }
            }
        }
        val end = sendMessage(8, PsMsg.senkusha(CLIENT_MTU, 5, PsProtoWriter().uint(1, 2).uint(2, mtuOut.toLong()).bool(3, false).uint(4, mtuIn.toLong())))
        awaitAck(end, 1000)
        sendMessage(1, PsProtoWriter().uint(1, PsMsg.DISCONNECT.toLong()).message(10, PsProtoWriter().string(1, "Client Disconnecting")).toByteArray())
        return PsNetwork(mtuIn, mtuOut, rttMs, measured = true)
    }

    /** The largest size between [MIN_MTU] and [MAX_MTU] that [works]. */
    private fun bisect(works: (Int) -> Boolean): Int {
        var lo = MIN_MTU
        var hi = MAX_MTU
        while (lo < hi) {
            val candidate = (lo + hi + 1) / 2
            if (works(candidate)) lo = candidate else hi = candidate - 1
        }
        return lo
    }

    // --- Takion v7 ---------------------------------------------------------------------------------------------------------------

    private fun handshake() {
        var cookie: ByteArray? = null
        for (attempt in 0 until 2) {
            send(PsTakionPackets.init(ownTag))
            val ack = receiveControl(1500) { it.chunkType == PsTakionPackets.CHUNK_INIT_ACK }
            if (ack != null) {
                consoleTag = ByteBuffer.wrap(ack.payload).int
                expectedTsn = consoleTag
                cookie = ack.payload.copyOfRange(16, 48)
                break
            }
        }
        val c = cookie ?: throw PsException("no INIT_ACK")
        if (consoleTag == 0) throw PsException("INIT_ACK tag 0")
        for (attempt in 0 until 2) {
            send(PsTakionPackets.cookie(consoleTag, c))
            if (receiveControl(1500) { it.chunkType == PsTakionPackets.CHUNK_COOKIE_ACK } != null) return
        }
        throw PsException("no COOKIE_ACK")
    }

    /** Sends a message (one fragment: Senkusha messages are small) and returns its TSN. */
    private fun sendMessage(channel: Int, message: ByteArray): Int {
        val tsn = sendTsn++
        val d = PsTakionPackets.control(consoleTag, 0, PsTakionPackets.CHUNK_DATA, 1, PsTakionPackets.dataPayload(tsn, channel, 0, message))
        unacked[tsn] = d
        send(d)
        return tsn
    }

    private fun send(d: ByteArray) = socket.send(DatagramPacket(d, d.size))

    private fun awaitMessage(ms: Long, match: (PsProtoMessage) -> Boolean): PsProtoMessage? {
        var found: PsProtoMessage? = null
        pump(ms) { found = messages.firstOrNull(match)?.also { messages.remove(it) }; found != null }
        return found
    }

    private fun awaitAck(tsn: Int, ms: Long): Boolean = pump(ms) { ackedTsn - tsn >= 0 }

    private fun awaitAv(ms: Long, match: (ByteArray) -> Boolean): Boolean = pump(ms) {
        var hit = false
        while (av.isNotEmpty()) if (match(av.removeFirst())) hit = true
        hit
    }

    /** Receives and handles datagrams until [done] or [ms] (and the whole budget) run out. */
    private fun pump(ms: Long, done: () -> Boolean): Boolean {
        val end = minOf(System.nanoTime() + ms * 1_000_000L, budgetEndNs)
        while (true) {
            if (done()) return true
            val left = (end - System.nanoTime()) / 1_000_000L
            if (left <= 0) { if (System.nanoTime() >= budgetEndNs) throw PsException("over the time budget"); return false }
            resendDue()
            socket.soTimeout = left.coerceIn(1L, 200L).toInt()
            val p = DatagramPacket(buf, buf.size)
            try { socket.receive(p) } catch (_: SocketTimeoutException) { continue }
            handle(buf.copyOf(p.length))
        }
    }

    private fun receiveControl(ms: Long, match: (PsTakionPackets.Control) -> Boolean): PsTakionPackets.Control? {
        socket.soTimeout = ms.toInt()
        val end = System.nanoTime() + ms * 1_000_000L
        while (System.nanoTime() < end) {
            val p = DatagramPacket(buf, buf.size)
            try { socket.receive(p) } catch (_: SocketTimeoutException) { return null }
            val c = PsTakionPackets.parseControl(buf, p.length) ?: continue
            if (match(c)) return c
        }
        return null
    }

    private fun handle(d: ByteArray) {
        when (d[0].toInt() and 0x0F) {
            0 -> {
                val c = PsTakionPackets.parseControl(d) ?: return
                if (c.tag != ownTag) return
                when (c.chunkType) {
                    PsTakionPackets.CHUNK_DATA -> onData(c)
                    PsTakionPackets.CHUNK_DATA_ACK -> {
                        val acked = ByteBuffer.wrap(c.payload).int
                        if (acked - ackedTsn > 0) ackedTsn = acked
                        unacked.keys.removeAll { acked - it >= 0 }
                    }
                }
            }
            2, 3 -> av.addLast(d)
        }
    }

    private fun onData(c: PsTakionPackets.Control) {
        val tsn = ByteBuffer.wrap(c.payload).int
        if (tsn == expectedTsn) {
            expectedTsn++
            // One fragment with data type 0: the protobuf message
            if (c.flags and 1 != 0 && c.payload.size > 9 && c.payload[8].toInt() == 0) messages.addLast(PsProtoMessage(c.payload.copyOfRange(9, c.payload.size)))
        }
        send(PsTakionPackets.dataAck(consoleTag, 0, expectedTsn - 1))
    }

    private fun resendDue() {
        val now = System.nanoTime()
        if (now < resendAtNs) return
        resendAtNs = now + 200_000_000L
        for (d in unacked.values) send(d)
    }

    override fun close() { runCatching { socket.close() } }

    companion object {
        private const val TAG = "PsSenkusha"
        private const val PORT = 9297
        private const val ECHO = 0
        private const val MTU = 1
        private const val CLIENT_MTU = 4
        private const val MIN_MTU = 576
        private const val MAX_MTU = 1454
        private const val PINGS = 10
        private const val PING_SIZE = 0x224
        private const val PING_WAIT_MS = 1000L
        private const val STEP_MS = 3000L
        /** The whole test: past this the stream starts with the fallback (the spec sets no limit; a start must not wait long). */
        private const val BUDGET_MS = 8000L
        private val FILLER = "CHIAKI".toByteArray(Charsets.US_ASCII)

        /**
         * A v7 audio datagram of [size] bytes (5.7): frame index 0, unit [unit] of 0x800, codec 0xFF, a zero word and [tag] (BE)
         * at the data's start + 4 (0x16), then zeros or [filler] repeated.
         */
        fun ping(unit: Int, tag: Int, size: Int, filler: ByteArray?): ByteArray {
            val d = ByteArray(maxOf(size, 0x1A))
            val b = ByteBuffer.wrap(d).order(ByteOrder.BIG_ENDIAN)
            b.put(0, 3)
            b.putShort(3, 0)
            b.putInt(5, (unit shl 21) or (0x7FF shl 10))
            b.put(9, 0xFF.toByte())
            b.putInt(0x16, tag)
            if (filler != null) for (i in 0x1A until d.size) d[i] = filler[(i - 0x1A) % filler.size]
            return d
        }

        private fun frameIndex(d: ByteArray) = if (d.size >= 5) ((d[3].toInt() and 0xFF) shl 8) or (d[4].toInt() and 0xFF) else -1

        /** A pong: frame index 0, the ping's unit index and its tag at 0x16. */
        fun isPong(d: ByteArray, unit: Int, tag: Int): Boolean {
            if (d.size < 0x1A || frameIndex(d) != 0) return false
            val b = ByteBuffer.wrap(d).order(ByteOrder.BIG_ENDIAN)
            return (b.getInt(5) ushr 21) == unit && b.getInt(0x16) == tag
        }

        /**
         * Sets "don't fragment" (IP_MTU_DISCOVER = IP_PMTUDISC_DO, Linux values 10 and 2) on [socket] so a packet too large for
         * the path is dropped, not split; false when the platform refuses.
         */
        fun dontFragment(socket: DatagramSocket): Boolean = runCatching {
            ParcelFileDescriptor.fromDatagramSocket(socket).use { Os.setsockoptInt(it.fileDescriptor, OsConstants.IPPROTO_IP, 10, 2) }
            true
        }.getOrElse { Log.i(TAG, "don't fragment not set: ${it.message}"); false }
    }
}
