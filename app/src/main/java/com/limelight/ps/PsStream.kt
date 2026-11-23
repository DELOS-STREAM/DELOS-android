package com.limelight.ps

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/** What the stream reports to its user (the stream activity through [PsConnection], or the debug bench). */
interface PsStreamListener {
    fun onStreamInfo(width: Int, height: Int, videoHeader: ByteArray, audioHeader: ByteArray?) {}
    /** A frame could not be rebuilt (FEC failed): the following pictures reference it, so the decoder waits for a keyframe (7.3). */
    fun onVideoLost() {}
    /** The console switched to another adaptive profile (7.2): its size and parameter sets apply from the next keyframe. */
    fun onVideoProfile(width: Int, height: Int, videoHeader: ByteArray) {}
    /** A complete Annex-B access unit. */
    fun onVideoFrame(frame: ByteArray, index: Int, receivedUs: Long) {}
    fun onAudio(opus: ByteArray, frameIndex: Int) {}
    fun onRumble(left: Int, right: Int) {}
    /** DualSense trigger effects (data type 11, 8.4): each trigger's mode and 10 parameter bytes in the DualSense format. */
    fun onTriggerEffects(leftMode: Int, rightMode: Int, left: ByteArray, right: ByteArray) {}
    /** The console's pad info (8.4): light bar colour and whether the game resets the motion origin. */
    fun onPadInfo(red: Int, green: Int, blue: Int, motionReset: Boolean) {}
    /** CONNECTIONQUALITY: the console's own rtt figure (a double, unit unknown) and its target bitrate. */
    fun onConnectionQuality(rtt: Double, targetKbps: Int) {}
    /** The round trip measured on the acks of reliable messages, smoothed, and its mean deviation, in milliseconds. */
    fun onRtt(rttMs: Int, varianceMs: Int) {}
    fun onStatus(text: String) {}
    fun onEnded(reason: String) {}
}

/**
 * The stream connection on UDP 9296 (sections 5 and 6): Takion handshake, BIG/BANG with ECDH, STREAMINFO, then AV packets, heartbeats,
 * congestion reports and input feedback. One instance per session; [start] blocks until BANG, then threads keep it running.
 */
class PsStream(
    private val host: InetAddress,
    private val target: PsTarget,
    private val sessionKey: String,
    private val rpCrypt: RpCrypt,
    private val request: PsStreamRequest,
    listener: PsStreamListener,
    /** Ask for a keyframe whenever frames go missing, not only when one cannot be rebuilt (PsPrefs.KEYFRAME_ON_LOSS). */
    private val keyframeOnLoss: Boolean = false,
    /**
     * A fixed bitrate: the loss the congestion report tells the console is held at 3 % so sporadic Wi-Fi loss does not lower it.
     * Automatic bitrate (false): the real loss goes out and the console lowers and raises its bitrate with the network (5.8).
     */
    private val holdBitrate: Boolean = true,
) : AutoCloseable {
    private val v12 = target.takionVersion >= 12
    private val socket = DatagramSocket()
    private val rng = SecureRandom()
    private val ownTag = rng.nextInt().let { if (it == 0) 1 else it }
    private var consoleTag = 0
    private var sendTsn = ownTag
    private var expectedTsn = 0
    private var sendPosition = 0L
    private var recvPosition = 0L
    private var gkSend: PsGkCrypt? = null
    private var gkRecv: PsGkCrypt? = null
    private val handshakeKey = ByteArray(16).also { rng.nextBytes(it) }
    private val ecdh = PsEcdh()
    private val timers: ScheduledExecutorService = Executors.newScheduledThreadPool(2)
    private val unacked = ConcurrentHashMap<Int, Pair<ByteArray, Int>>()
    // When each reliable message part was first sent: its ack gives a round-trip sample (not after a resend)
    private val sentAtNs = ConcurrentHashMap<Int, Long>()
    private var rttMs = -1f
    private var rttVarianceMs = 0f
    @Volatile private var running = true
    @Volatile private var bang: PsProtoMessage? = null
    @Volatile private var streamInfo: PsProtoMessage? = null
    private val reorder = HashMap<Int, PsTakionPackets.Control>()
    private val fragments = HashMap<Int, java.io.ByteArrayOutputStream>()
    private val fragmentType = HashMap<Int, Int>()
    private val assembler = PsFrameAssembler()
    private var feedbackSeq = 0
    private var historySeq = 0
    private var state = PsControllerState()
    private val history = ArrayDeque<ByteArray>()
    /**
     * Who gets the frames, sound and feedback: replaced when the session is parked or taken back (PsSessionPark). A new listener
     * gets the current adaptive profile again with the next frame.
     */
    @Volatile var listener: PsStreamListener = listener
        set(value) { field = value; profile = -1 }
    /** Whether the stream still runs (not closed). */
    val alive: Boolean get() = running
    /** Milliseconds since the last datagram from the console. */
    val quietMs: Long get() = (System.nanoTime() - lastReceiveNs) / 1_000_000L
    @Volatile private var lastReceiveNs = System.nanoTime()
    // The adaptive profiles of STREAMINFO (resolution entries) and the one the frames use (7.2, adaptive resolution)
    @Volatile private var profiles: List<PsProtoMessage> = emptyList()
    private var profile = 0
    // Video statistics for the log, per 10 s: complete frames, frames FEC restored, frames lost
    private var framesComplete = 0
    private var framesRecovered = 0
    private var framesLost = 0

    fun start() {
        socket.connect(InetSocketAddress(host, 9296))
        socket.receiveBufferSize = 0x19000 * 4
        if (request.dontFragment) PsSenkusha.dontFragment(socket)
        handshake()
        Thread(::receiveLoop, "ps-takion-rx").apply { isDaemon = true; start() }
        timers.scheduleWithFixedDelay(::resend, 200, 200, TimeUnit.MILLISECONDS)
        val json = PsSession.launchSpecJson(target, request, handshakeKey)
        val big = PsSession.big(target, sessionKey, PsSession.launchSpecEncrypted(rpCrypt, json), ecdh, handshakeKey)
        sendMessage(1, 0, big)
        listener.onStatus("BIG sent, waiting for BANG")
        val until = System.currentTimeMillis() + 5000
        while (bang == null && running && System.currentTimeMillis() < until) Thread.sleep(10)
        val b = bang ?: throw PsException("no BANG from the console")
        if (b.bool(3) != true || b.bool(4) != true) throw PsException("the console refused BIG (key ${b.bool(3)}, version ${b.bool(4)})")
        val secret = ecdh.sharedSecret(b.bytes(8) ?: throw PsException("BANG without a public key"))
        gkSend = PsGkCrypt(2, handshakeKey, secret)
        gkRecv = PsGkCrypt(3, handshakeKey, secret)
        listener.onStatus("BANG accepted, keys ready")
        streamInfo?.let(::onStreamInfo)
        timers.scheduleWithFixedDelay({ sendMessage(1, 0, PsMsg.simple(PsMsg.HEARTBEAT)) }, 1, 1, TimeUnit.SECONDS)
        timers.scheduleWithFixedDelay(::sendCongestion, 200, 200, TimeUnit.MILLISECONDS)
        // The state every 200 ms, and the button history with it, so a lost release cannot leave a button held (8.3)
        timers.scheduleWithFixedDelay({ sendFeedbackState(); if (synchronized(history) { history.isNotEmpty() }) sendHistory() }, 200, 200, TimeUnit.MILLISECONDS)
        timers.scheduleWithFixedDelay(::stopIdleHaptics, 50, 50, TimeUnit.MILLISECONDS)
        timers.scheduleWithFixedDelay(::logVideo, 10, 10, TimeUnit.SECONDS)
    }

    // DualSense haptics as rumble: a PS5 that is told the controller is a DualSense sends its effects as PCM for the voice coils
    // (audio marker 0x02, spec 7.1) instead of rumble messages, so the controller's motors follow the PCM's strength per channel
    // (owner, 2026-11-22: no vibration on the Odin with PS5 games). Volatile: the receive thread writes, the timer stops it.
    @Volatile private var hapticAtNs = 0L
    @Volatile private var hapticLeft = 0
    @Volatile private var hapticRight = 0
    private var hapticUnits = 0L

    /** One haptics unit (signed 16-bit stereo PCM) as the two motors' strength, 0 to 255: the mean level, scaled up. */
    private fun onHaptics(pcm: ByteArray) {
        val b = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var left = 0L
        var right = 0L
        var n = 0
        while (b.remaining() >= 4) {
            left += Math.abs(b.short.toInt())
            right += Math.abs(b.short.toInt())
            n++
        }
        if (n == 0) return
        if (hapticUnits++ == 0L) Log.i(TAG, "First DualSense haptics unit: ${pcm.size} bytes")
        hapticAtNs = System.nanoTime()
        val l = minOf(255L, left * HAPTIC_GAIN / n / 128).toInt()
        val r = minOf(255L, right * HAPTIC_GAIN / n / 128).toInt()
        if (l != hapticLeft || r != hapticRight) {
            hapticLeft = l
            hapticRight = r
            listener.onRumble(l, r)
        }
    }

    /** Haptics that stopped arriving (the effect ended without silent units) stop the motors after 100 ms. */
    private fun stopIdleHaptics() {
        if ((hapticLeft != 0 || hapticRight != 0) && System.nanoTime() - hapticAtNs > 100_000_000L) {
            hapticLeft = 0
            hapticRight = 0
            listener.onRumble(0, 0)
        }
    }

    // --- Takion ---------------------------------------------------------------------------------------------------------------

    private fun handshake() {
        socket.soTimeout = 5000
        val buf = ByteArray(2048)
        var initAck: PsTakionPackets.Control? = null
        repeat(3) {
            if (initAck != null) return@repeat
            send(PsTakionPackets.init(ownTag))
            try {
                val p = DatagramPacket(buf, buf.size); socket.receive(p)
                PsTakionPackets.parseControl(buf, p.length)?.takeIf { it.chunkType == PsTakionPackets.CHUNK_INIT_ACK }?.let { initAck = it }
            } catch (_: SocketTimeoutException) {}
        }
        val ack = initAck ?: throw PsException("no Takion INIT_ACK")
        val b = ByteBuffer.wrap(ack.payload)
        consoleTag = b.int
        if (consoleTag == 0) throw PsException("INIT_ACK tag 0")
        expectedTsn = consoleTag
        val cookie = ack.payload.copyOfRange(16, 48)
        repeat(3) {
            send(PsTakionPackets.cookie(consoleTag, cookie))
            try {
                while (true) {
                    val p = DatagramPacket(buf, buf.size); socket.receive(p)
                    val c = PsTakionPackets.parseControl(buf, p.length) ?: continue
                    if (c.chunkType == PsTakionPackets.CHUNK_COOKIE_ACK) { socket.soTimeout = 0; return }
                }
            } catch (_: SocketTimeoutException) {}
        }
        throw PsException("no Takion COOKIE_ACK")
    }

    private fun send(d: ByteArray) { if (running) runCatching { socket.send(DatagramPacket(d, d.size)) } }

    /** Advances the send key position by s + (s mod 16) and returns the position the packet uses (5.5). */
    private fun takePosition(s: Int): Long = synchronized(this) {
        if (gkSend == null) 0L else sendPosition.also { sendPosition += s + (s % 16) }
    }

    /** Writes the GMAC of a CONTROL datagram (key position field zeroed for the computation, then restored). */
    private fun macControl(d: ByteArray, position: Long) {
        val gk = gkSend ?: return
        val copy = d.copyOf()
        for (i in 5 until 13) copy[i] = 0
        gk.gmac(copy, position).copyInto(d, 5)
    }

    /** Sends a Takion message on [channel] (fragmented, reliable). */
    fun sendMessage(channel: Int, dataType: Int, message: ByteArray) {
        val parts = PsTakionPackets.fragments(message, minOf(request.mtuIn, request.mtuOut))
        parts.forEachIndexed { i, (flags, data) ->
            val tsn: Int
            synchronized(this) { tsn = sendTsn++ }
            val position = takePosition(data.size)
            val payload = PsTakionPackets.dataPayload(tsn, channel, if (i == 0) dataType else null, data)
            val d = PsTakionPackets.control(consoleTag, position.toInt(), PsTakionPackets.CHUNK_DATA, flags, payload)
            macControl(d, position)
            unacked[tsn] = d to 0
            sentAtNs[tsn] = System.nanoTime()
            send(d)
        }
    }

    private fun resend() {
        for ((tsn, entry) in unacked) {
            if (entry.second >= 25) { unacked.remove(tsn); sentAtNs.remove(tsn); continue }
            unacked[tsn] = entry.first to entry.second + 1
            send(entry.first)
        }
    }

    private fun receiveLoop() {
        // MAC, decryption, assembly and FEC of every packet run here: as urgent as the decoder's feeder (Fable's review)
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)
        val buf = ByteArray(0x19000)
        while (running) {
            val p = DatagramPacket(buf, buf.size)
            try { socket.receive(p) } catch (_: Exception) { if (running) continue else return }
            lastReceiveNs = System.nanoTime()
            val type = buf[0].toInt() and 0x0F
            try {
                when (type) {
                    0 -> onControl(buf.copyOf(p.length))
                    2, 3 -> onAv(buf.copyOf(p.length))
                }
            } catch (e: Exception) {
                Log.w(TAG, "packet type $type: ${e.message}")
            }
        }
    }

    private fun extend(low: Int, last: Long): Long {
        val l = low.toLong() and 0xFFFFFFFFL
        var candidate = (last and 0xFFFFFFFFL.inv()) or l
        if (candidate < last - 0x80000000L) candidate += 0x100000000L
        else if (candidate > last + 0x80000000L && candidate >= 0x100000000L) candidate -= 0x100000000L
        return candidate
    }

    private fun onControl(d: ByteArray) {
        val c = PsTakionPackets.parseControl(d) ?: return
        if (c.tag != ownTag) return
        val gk = gkRecv
        if (gk != null) {
            val position = extend(c.keyPosition, recvPosition)
            val copy = d.copyOf(); for (i in 5 until 13) copy[i] = 0
            if (!gk.gmac(copy, position).contentEquals(d.copyOfRange(5, 9))) { Log.w(TAG, "control MAC mismatch at $position"); return }
            recvPosition = maxOf(recvPosition, position)
        }
        when (c.chunkType) {
            PsTakionPackets.CHUNK_DATA -> onData(c)
            PsTakionPackets.CHUNK_DATA_ACK -> {
                val acked = ByteBuffer.wrap(c.payload).int
                val now = System.nanoTime()
                val sample = unacked[acked]?.takeIf { it.second == 0 }?.let { sentAtNs[acked] }?.let { (now - it) / 1_000_000f }
                for (tsn in unacked.keys) if (acked - tsn >= 0) { unacked.remove(tsn); sentAtNs.remove(tsn) }
                if (sample != null) onRttSample(sample)
            }
        }
    }

    /** Smoothed round trip and its mean deviation (RFC 6298 weights) from the acks of reliable messages. */
    private fun onRttSample(ms: Float) {
        if (rttMs < 0) { rttMs = ms; rttVarianceMs = ms / 2 }
        else {
            rttVarianceMs = rttVarianceMs * .75f + kotlin.math.abs(rttMs - ms) * .25f
            rttMs = rttMs * .875f + ms * .125f
        }
        listener.onRtt(kotlin.math.round(rttMs).toInt(), kotlin.math.round(rttVarianceMs).toInt())
    }

    private fun onData(c: PsTakionPackets.Control) {
        val tsn = ByteBuffer.wrap(c.payload).int
        synchronized(reorder) {
            if (tsn - expectedTsn < 0) { sendAck(expectedTsn - 1); return }
            if (tsn - expectedTsn >= 16) return
            reorder[tsn] = c
            var delivered = false
            while (true) {
                val next = reorder.remove(expectedTsn) ?: break
                deliver(next)
                expectedTsn++
                delivered = true
            }
            if (delivered) sendAck(expectedTsn - 1)
        }
    }

    private fun sendAck(tsn: Int) {
        val position = takePosition(29)
        val d = PsTakionPackets.dataAck(consoleTag, position.toInt(), tsn)
        macControl(d, position)
        send(d)
    }

    private fun deliver(c: PsTakionPackets.Control) {
        val b = ByteBuffer.wrap(c.payload)
        b.int
        val channel = b.short.toInt() and 0xFFFF
        val buffer = fragments[channel]
        val data: ByteArray
        if (buffer == null) {
            fragmentType[channel] = u(c.payload[8])
            data = c.payload.copyOfRange(9, c.payload.size)
        } else data = c.payload.copyOfRange(8, c.payload.size)
        if (c.flags and 1 == 0) { (fragments.getOrPut(channel) { java.io.ByteArrayOutputStream() }).write(data); return }
        val whole = buffer?.let { it.write(data); fragments.remove(channel); it.toByteArray() } ?: data
        val dataType = fragmentType.remove(channel) ?: 0
        when (dataType) {
            0 -> onMessage(PsProtoMessage(whole))
            7 -> if (whole.size >= 3) listener.onRumble(u(whole[1]), u(whole[2]))
            9 -> when {
                whole.size >= 25 -> listener.onPadInfo(u(whole[9]), u(whole[10]), u(whole[11]), whole[12].toInt() != 0)
                whole.size >= 17 -> listener.onPadInfo(u(whole[1]), u(whole[2]), u(whole[3]), whole[4].toInt() != 0)
            }
            11 -> if (whole.size >= 25) listener.onTriggerEffects(u(whole[1]), u(whole[2]), whole.copyOfRange(5, 15), whole.copyOfRange(15, 25))
        }
    }

    private fun onMessage(m: PsProtoMessage) {
        when (m.uint(1)?.toInt()) {
            PsMsg.BANG -> bang = m.message(3)
            PsMsg.STREAMINFO -> m.message(15)?.let { if (gkRecv == null) streamInfo = it else onStreamInfo(it) }
            PsMsg.DISCONNECT -> { listener.onEnded(m.message(10)?.string(1) ?: "disconnected"); close() }
            PsMsg.CONNECTIONQUALITY -> m.message(17)?.let { listener.onConnectionQuality(it.double(5) ?: -1.0, it.uint(1)?.toInt() ?: 0) }
        }
    }

    private fun onStreamInfo(info: PsProtoMessage) {
        val res = info.messages(1).firstOrNull() ?: return
        profiles = info.messages(1)
        listener.onStreamInfo(res.uint(1)?.toInt() ?: 0, res.uint(2)?.toInt() ?: 0, res.bytes(3) ?: ByteArray(0), info.bytes(2))
        sendMessage(9, 0, PsMsg.simple(PsMsg.STREAMINFOACK))
        val controller = PsProtoWriter().bool(2, true).uint(3, if (target.ps5) 6 else 2)
        sendMessage(1, 0, PsProtoWriter().uint(1, PsMsg.CONTROLLERCONNECTION.toLong()).message(22, controller).toByteArray())
        val mic = byteArrayOf(1, 16, 0, 0, 0xBB.toByte(), 0x80.toByte(), 0, 0, 1, 0xE0.toByte(), 0, 0, 0, 1)
        sendMessage(1, 0, PsProtoWriter().uint(1, PsMsg.STREAMINFO.toLong()).message(15, PsProtoWriter().bytes(2, mic)).toByteArray())
    }

    private fun onAv(d: ByteArray) {
        val low = ByteBuffer.wrap(d).getInt(0x0E)
        val position = extend(low, recvPosition)
        val packet = PsAvParser.parse(d, d.size, v12, gkRecv, position) ?: return
        recvPosition = maxOf(recvPosition, position)
        if (packet.video) {
            for (r in assembler.add(packet)) when (r) {
                is PsFrameAssembler.Result.Frame -> {
                    framesComplete++
                    if (r.recovered) framesRecovered++
                    if (r.adaptiveIndex != profile) switchProfile(r.adaptiveIndex)
                    listener.onVideoFrame(r.data, r.index, r.receivedUs)
                }
                is PsFrameAssembler.Result.Lost -> { framesLost++; requestIdr(r.from, r.to); listener.onVideoLost() }
            }
            // Whole frames missing: CORRUPTFRAME tells the console not to reference them; with keyframe-on-loss it starts over
            assembler.takeLoss()?.let { (a, b) ->
                framesLost += ((b - a) and 0xFFFF) + 1
                if (keyframeOnLoss) { requestIdr(a, b); listener.onVideoLost() } else corrupt(a, b)
            }
        } else if (packet.audioMarker == 2) {
            onHaptics(packet.payload)
        } else {
            audioUnits.incrementAndGet()
            val desc = packet.fecOrAudioDesc
            val size = desc ushr 8
            val sources = desc and 0xF
            for (i in 0 until sources) {
                val from = i * size
                if (size > 0 && from + size <= packet.payload.size) listener.onAudio(packet.payload.copyOfRange(from, from + size), packet.frameIndex + i)
            }
        }
    }

    /** Frames of another adaptive profile: its size and parameter sets go to the listener first (7.2, 7.3 step 6). */
    private fun switchProfile(index: Int) {
        val p = profiles.getOrNull(index)
        profile = index
        if (p == null) { Log.w(TAG, "adaptive profile $index unknown (${profiles.size} in STREAMINFO)"); return }
        val width = p.uint(1)?.toInt() ?: 0
        val height = p.uint(2)?.toInt() ?: 0
        Log.i(TAG, "adaptive profile $index: ${width}x$height")
        listener.onVideoProfile(width, height, p.bytes(3) ?: ByteArray(0))
    }

    private fun logVideo() {
        val complete = framesComplete; val recovered = framesRecovered; val lost = framesLost
        val late = assembler.lateUnits.getAndSet(0)
        framesComplete = 0; framesRecovered = 0; framesLost = 0
        if (recovered > 0 || lost > 0 || late > 0) Log.i(TAG, "video 10 s: $complete frames, $recovered restored by FEC, $lost lost, $late units late after their frame")
    }

    private fun corrupt(from: Int, to: Int) =
        sendMessage(2, 0, PsProtoWriter().uint(1, PsMsg.CORRUPTFRAME.toLong()).message(6, PsProtoWriter().uint(1, from.toLong()).uint(2, to.toLong())).toByteArray())

    private fun requestIdr(from: Int, to: Int) { corrupt(from, to); sendMessage(2, 0, PsMsg.simple(PsMsg.IDRREQUEST)) }

    /** Asks the console for a keyframe now: the decoder lost its reference (codec reset, overflow) and drops frames until one comes. */
    fun requestKeyframe() { if (running && gkSend != null) sendMessage(2, 0, PsMsg.simple(PsMsg.IDRREQUEST)) }

    /** An empty datagram to the console: keeps a power-saving Wi-Fi radio awake between frames ("prevent packet loss"). */
    fun ping() = send(ByteArray(0))

    private fun sendCongestion() {
        val gk = gkSend ?: return
        val received = assembler.unitsReceived.getAndSet(0) + audioUnits.getAndSet(0)
        // A fixed bitrate reports sporadic Wi-Fi loss as at most 3 %: FEC hides it, and a higher figure makes the console drop its
        // bitrate (5.8). Automatic bitrate reports the real loss
        val measured = assembler.unitsLost.getAndSet(0)
        val lost = if (holdBitrate) measured.coerceAtMost(received * 3 / 100) else measured
        val position = takePosition(15)
        val d = ByteBuffer.allocate(15).order(ByteOrder.BIG_ENDIAN).apply {
            put(5); putShort(0); putShort(received.coerceAtMost(0xFFFF).toShort()); putShort(lost.coerceAtMost(0xFFFF).toShort()); putInt(0); putInt(0)
        }.array()
        gk.gmac(d, position).copyInto(d, 7)
        ByteBuffer.wrap(d).putInt(11, position.toInt())
        send(d)
    }

    // --- Input ----------------------------------------------------------------------------------------------------------------

    /** Sends the new controller state: buttons, triggers and touch as history events, sticks and motion as the state packet. */
    fun updateController(new: PsControllerState) {
        val events = PsFeedback.events(state, new)
        val sticksChanged = new.lx != state.lx || new.ly != state.ly || new.rx != state.rx || new.ry != state.ry
        val motionChanged = !sticksChanged && new.copy(buttons = 0, l2 = 0, r2 = 0, touches = emptyList()) != state.copy(buttons = 0, l2 = 0, r2 = 0, touches = emptyList())
        state = new
        if (events.isNotEmpty()) {
            synchronized(history) { for (e in events) { history.addFirst(e); historySeq++ } ; while (history.size > 8) history.removeLast() }
            sendHistory()
        }
        // Sticks go out at once; motion alone at most every 4 ms (gyro and accelerometer each sample at 200 Hz), the newest values
        val now = System.nanoTime()
        if (sticksChanged || (motionChanged && now - stateSentNs >= MOTION_SEND_NS)) { stateSentNs = now; sendFeedbackState() }
    }

    private var stateSentNs = 0L
    // Audio units received, counted into the congestion report with the video units (5.8)
    private val audioUnits = java.util.concurrent.atomic.AtomicInteger()

    private fun sendFeedbackState() {
        val gk = gkSend ?: return
        val body = PsFeedback.state(state, v12)
        sendFeedback(6, (feedbackSeq++) and 0xFFFF, 0, body, gk)
    }

    private fun sendHistory() {
        val gk = gkSend ?: return
        val (count, body) = synchronized(history) { history.size to history.fold(ByteArray(0)) { acc, e -> acc + e } }
        sendFeedback(1, historySeq and 0xFFFF, count - 1, body, gk)
    }

    private fun sendFeedback(type: Int, seq: Int, countMinusOne: Int, body: ByteArray, gk: PsGkCrypt) {
        val position = takePosition(body.size + 16)
        val enc = body.copyOf()
        gk.apply(enc, 0, enc.size, position + 16)
        val d = ByteBuffer.allocate(12 + enc.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(type.toByte()); putShort(seq.toShort()); put(countMinusOne.toByte()); putInt(position.toInt()); putInt(0); put(enc)
        }.array()
        gk.gmac(d, position).copyInto(d, 8)
        send(d)
    }

    // The microphone (8.5): a frame counter and the last two frames, repeated in every packet
    private var micCounter = 0
    private var micOneBack: ByteArray? = null
    private var micTwoBack: ByteArray? = null

    /**
     * One 40-byte Opus frame of the microphone as an AUDIO datagram (8.5): the newest frame, then the one two back and the one
     * before it (at the start the first frame fills both), encrypted at key position + 16 and authenticated with the GMAC.
     */
    @Synchronized
    fun sendMicrophone(frame: ByteArray) {
        val gk = gkSend ?: return
        val oneBack = micOneBack ?: frame
        val twoBack = micTwoBack ?: frame
        micTwoBack = oneBack
        micOneBack = frame
        val payload = frame + twoBack + oneBack
        val header = if (v12) 0x14 else 0x13
        val position = takePosition(payload.size + 16)
        gk.apply(payload, 0, payload.size, position + 16)
        val counter = micCounter++
        val d = ByteBuffer.allocate(header + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(3); putShort(counter.toShort()); putShort((counter + 1).toShort()); putInt((2 shl 16) or 0x2821); put(5)
            putInt(0); putInt(position.toInt()); put(0); if (v12) put(0); put(payload)
        }.array()
        gk.gmac(d, position).copyInto(d, 0x0A)
        send(d)
    }

    override fun close() {
        if (!running) return
        runCatching { sendMessage(1, 0, PsProtoWriter().uint(1, PsMsg.DISCONNECT.toLong()).message(10, PsProtoWriter().string(1, "Client Disconnecting")).toByteArray()) }
        running = false
        timers.shutdownNow()
        socket.close()
    }

    companion object {
        private const val TAG = "PsStream"
        /** Haptics PCM is quieter than a motor's full range: its mean level times this is the motor strength. */
        private const val HAPTIC_GAIN = 4L
        /** Motion-only controller state goes out at most this often. */
        private const val MOTION_SEND_NS = 4_000_000L
    }
}
