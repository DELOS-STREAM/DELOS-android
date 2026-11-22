package com.limelight.ps

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.StreamConnection
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.shell.ps.PsConsoleStore
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.TreeMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A PlayStation Remote Play stream behind DELOS's stream activity: the same decoder, upscalers, pacing, overlay, stream menu and
 * controller handling as a PC stream. It wakes the console, opens the session (docs/PS_REMOTE_PLAY.md), and turns [PsStream]'s
 * frames, audio and feedback into the renderers' and listener's calls; the controller calls of [StreamConnection] become the
 * console's controller state. Keyboard, mouse and text do not exist on a console.
 */
class PsConnection(context: Context, private val consoleId: String, private val prefs: PreferenceConfiguration) : StreamConnection {
    private val appContext = context.applicationContext
    private val psPrefs = PsPrefs.prefs(appContext)

    @Volatile private var running = false
    @Volatile private var stream: PsStream? = null
    @Volatile private var ctrl: PsCtrl? = null
    private var video: VideoDecoderRenderer? = null
    private var audio: AudioRenderer? = null
    private lateinit var listener: NvConnectionListener
    private var feeder: VideoFeeder? = null
    private var audioSource: AudioSource? = null
    @Volatile private var started = false
    @Volatile private var lastPacketMs = 0L
    private val rttInfo = AtomicLong(0)
    private var qualityLogs = 0
    private var connectThread: Thread? = null

    /** The console this stream plays, for the activity's title and the loader. */
    val console: com.limelight.shell.ps.PsConsole? = PsConsoleStore.get(appContext).consoles.value.firstOrNull { it.id == consoleId }

    override fun start(audioRenderer: AudioRenderer, videoDecoderRenderer: VideoDecoderRenderer, connectionListener: NvConnectionListener) {
        audio = audioRenderer
        video = videoDecoderRenderer
        listener = connectionListener
        running = true
        connectThread = thread(name = "ps-connect") { connect() }
    }

    private fun connect() {
        val c = console
        listener.stageStarting(STAGE_WAKE)
        try {
            if (c == null) throw PsException("console $consoleId is not paired")
            val address = locate(c)
            runCatching { PsDiscovery.wake(address, c.console.target.ps5, c.console.registKey) }
            val request = request(c.console.target)
            // A console in rest mode takes a few seconds before the session starts; retry while it wakes
            var opened: PsCtrl? = null
            var lastError: Exception? = null
            for (attempt in 0 until 15) {
                if (!running) return
                try { opened = PsCtrl(address, PS_PORT, c.console, request.codec).also { it.open() }; break }
                catch (e: Exception) { lastError = e; Thread.sleep(2000) }
            }
            val session = opened ?: throw lastError ?: PsException("no session")
            ctrl = session
            var sessionId: String? = null
            val ready = Object()
            thread(name = "ps-ctrl") {
                runCatching { session.readLoop { type, payload -> if (type == PsCtrl.SESSION_ID) { sessionId = PsCtrl.sessionId(payload); synchronized(ready) { ready.notifyAll() } } } }
            }
            synchronized(ready) { if (sessionId == null) ready.wait(10_000) }
            val id = sessionId ?: throw PsException("the console sent no session id")
            session.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); session.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); session.send(PsCtrl.DISPLAY_DEVICES, ByteArray(4))
            listener.stageComplete(STAGE_WAKE)
            if (!running) return
            listener.stageStarting(STAGE_STREAM)
            ps5 = c.console.target.ps5
            val s = PsStream(address, c.console.target, id, session.crypt, request, streamListener(request),
                keyframeOnLoss = psPrefs.getBoolean(PsPrefs.KEYFRAME_ON_LOSS, false), holdBitrate = !PsPrefs.autoBitrate(psPrefs))
            stream = s
            s.start()
        } catch (e: Exception) {
            Log.w(TAG, "connect", e)
            if (running) listener.stageFailed(if (stream == null) STAGE_WAKE else STAGE_STREAM, 0, -1)
        }
    }

    /**
     * Where the console is now: a broadcast search that stops at its answer (its host id is the paired MAC), so a console that
     * moved to another address (Wi-Fi to Ethernet, a new lease; owner, 2026-11-22) is still found and remembered there. A
     * console in rest mode answers too. Without an answer within 1.5 s the stored address is used.
     */
    private fun locate(c: com.limelight.shell.ps.PsConsole): InetAddress {
        val answers = runCatching {
            PsDiscovery.search(InetAddress.getByName("255.255.255.255"), 1500) { it.hostId.equals(c.id, ignoreCase = true) }
        }.getOrDefault(emptyList())
        val found = answers.firstOrNull { it.hostId.equals(c.id, ignoreCase = true) }
        if (found == null) {
            Log.i(TAG, "console not found by search (${answers.size} answers, host ids ${answers.map { it.hostId }}, paired ${c.id})")
            return InetAddress.getByName(c.address)
        }
        val now = found.address.hostAddress.orEmpty()
        if (now != c.address) {
            Log.i(TAG, "console found at a new address")
            PsConsoleStore.get(appContext).moved(c.id, now)
        }
        return found.address
    }

    /**
     * The stream the settings ask for, within what Remote Play offers (docs/research/ps-remote-play-protocol.md, 9). The bitrate is the
     * console's ceiling: a fixed one is held, the automatic one is the most this size takes and the console moves below it.
     */
    private fun request(target: PsTarget): PsStreamRequest {
        val size = RESOLUTIONS.lastOrNull { it.second <= prefs.height } ?: RESOLUTIONS.first()
        val maxHeight = if (target.ps5) 1080 else 720
        val (w, h) = if (size.second > maxHeight) RESOLUTIONS.last { it.second <= maxHeight } else size
        val codec = when {
            prefs.videoFormat == PreferenceConfiguration.FormatOption.FORCE_H264 || !target.ps5 -> PsCodec.H264
            prefs.enableHdr -> PsCodec.HEVC_HDR
            else -> PsCodec.HEVC
        }
        val bitrate = if (PsPrefs.autoBitrate(psPrefs)) PsPrefs.autoCeilingKbps(h)
            else prefs.bitrate.coerceIn(PsPrefs.MIN_BITRATE_KBPS, PsPrefs.maxBitrateKbps(psPrefs))
        return PsStreamRequest(width = w, height = h, fps = if (prefs.fps >= 60) 60 else 30, bitrateKbps = bitrate, codec = codec)
    }

    private fun streamListener(request: PsStreamRequest) = object : PsStreamListener {
        override fun onStreamInfo(width: Int, height: Int, videoHeader: ByteArray, audioHeader: ByteArray?) {
            if (started) return
            val format = when (request.codec) {
                PsCodec.H264 -> MoonBridge.VIDEO_FORMAT_H264
                PsCodec.HEVC -> MoonBridge.VIDEO_FORMAT_H265
                PsCodec.HEVC_HDR -> MoonBridge.VIDEO_FORMAT_H265_MAIN10
            }
            val v = video ?: return
            if (v.setup(format, width, height, request.fps) != 0) { listener.stageFailed(STAGE_STREAM, 0, -1); return }
            v.start()
            feeder = VideoFeeder(v, request.codec != PsCodec.H264, videoHeader, PsPrefs.videoQueue(psPrefs)).also { it.start() }
            if (audioHeader != null) {
                runCatching { AudioSource(audioHeader).also { it.start(); audioSource = it } }.onFailure { Log.w(TAG, "audio: ${it.message}") }
            }
            if (request.codec.hdr) listener.setHdrMode(true, null)
            listener.setMotionEventState(0, MoonBridge.LI_MOTION_TYPE_GYRO, MOTION_RATE_HZ)
            listener.setMotionEventState(0, MoonBridge.LI_MOTION_TYPE_ACCEL, MOTION_RATE_HZ)
            started = true
            lastPacketMs = SystemClock.uptimeMillis()
            thread(name = "ps-watchdog", isDaemon = true) { watchdog() }
            listener.stageComplete(STAGE_STREAM)
            listener.connectionStarted()
        }

        override fun onVideoFrame(frame: ByteArray, index: Int) {
            lastPacketMs = SystemClock.uptimeMillis()
            feeder?.offer(frame, index)
        }

        override fun onAudio(opus: ByteArray, frameIndex: Int) {
            lastPacketMs = SystemClock.uptimeMillis()
            audioSource?.offer(opus, frameIndex)
        }

        override fun onRumble(left: Int, right: Int) = listener.rumble(0, (left * 257).toShort(), (right * 257).toShort())

        override fun onPadInfo(red: Int, green: Int, blue: Int, motionReset: Boolean) {
            listener.setControllerLED(0, red.toByte(), green.toByte(), blue.toByte())
            if (motionReset) synchronized(padLock) { orientation = floatArrayOf(0f, 0f, 0f, 1f) }
        }

        // The console's own rtt figure does not match the link (about 190 for a 6 ms ping on the Odin, 2026-11-22): it is logged;
        // the network latency shown is the round trip DELOS measures on the Takion acks
        override fun onConnectionQuality(rtt: Double, targetKbps: Int) {
            if (qualityLogs < 3) { qualityLogs++; Log.i(TAG, "connection quality: rtt=$rtt target=$targetKbps") }
        }

        override fun onRtt(rttMs: Int, varianceMs: Int) {
            rttInfo.set((rttMs.toLong() shl 32) or (varianceMs.toLong() and 0xFFFFFFFFL))
        }

        override fun onEnded(reason: String) {
            Log.i(TAG, "ended: $reason")
            if (running && started) listener.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        }
    }

    /** A console that vanished (switched off, Wi-Fi gone) sends nothing; the stream ends after [SILENCE_MS] without a packet. */
    private fun watchdog() {
        while (running) {
            Thread.sleep(1000)
            if (running && SystemClock.uptimeMillis() - lastPacketMs > SILENCE_MS) {
                listener.connectionTerminated(-1)
                return
            }
        }
    }

    /** Set by the stream menu's rest-mode tile: [stop] puts the console in rest mode (ctrl GOTO_BED, 4.5) before it disconnects. */
    @Volatile var restOnStop = false

    override fun stop() {
        running = false
        connectThread?.interrupt()
        feeder?.stop(); feeder = null
        // The audio renderer is set up only when the console sent an audio header (AudioSource)
        val hadAudio = audioSource != null
        audioSource?.close(); audioSource = null
        if (restOnStop) runCatching { ctrl?.send(PsCtrl.GOTO_BED) }.onFailure { Log.w(TAG, "rest mode: ${it.message}") }
        stream?.close(); stream = null
        ctrl?.close(); ctrl = null
        if (started) {
            video?.stop(); video?.cleanup()
            started = false
        }
        if (hadAudio) { audio?.stop(); audio?.cleanup() }
    }

    override fun getEstimatedRttInfo(): Long = rttInfo.get()

    /** The jitter buffer is the point of the audio queue, not a backlog: the renderer must play every frame it hands over. */
    override fun getPendingAudioDurationMs(): Int = 0

    override fun sendKeepAlive() { stream?.ping() }

    override fun getServerAppVersion(): String? = null
    override fun reportsMoonlightPortFailures() = false
    override fun supportsRuntimeBitrate() = false
    override fun setRuntimeBitrate(bitrateKbps: Int): Int = throw java.io.FileNotFoundException("PlayStation streams keep their bitrate")
    override fun supportsKeyboardAndMouse() = false

    override fun sendExecServerCmd(cmdId: Int) {}
    override fun sendMouseMove(deltaX: Short, deltaY: Short) {}
    override fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short) {}
    override fun sendMouseMoveAsMousePosition(deltaX: Short, deltaY: Short, referenceWidth: Short, referenceHeight: Short) {}
    override fun sendMouseButtonDown(mouseButton: Byte) {}
    override fun sendMouseButtonUp(mouseButton: Byte) {}
    override fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {}
    override fun sendMouseScroll(scrollClicks: Byte) {}
    override fun sendMouseHScroll(scrollClicks: Byte) {}
    override fun sendMouseHighResScroll(scrollAmount: Short) {}
    override fun sendMouseHighResHScroll(scrollAmount: Short) {}
    override fun sendTouchEvent(eventType: Byte, pointerId: Int, x: Float, y: Float, pressureOrDistance: Float, contactAreaMajor: Float, contactAreaMinor: Float, rotation: Short) = MoonBridge.LI_ERR_UNSUPPORTED
    override fun sendPenEvent(eventType: Byte, toolType: Byte, penButtons: Byte, x: Float, y: Float, pressureOrDistance: Float, contactAreaMajor: Float, contactAreaMinor: Float, rotation: Short, tilt: Byte) = MoonBridge.LI_ERR_UNSUPPORTED
    override fun sendUtf8Text(text: String) {}
    override fun sendControllerArrivalEvent(controllerNumber: Byte, activeGamepadMask: Short, type: Byte, supportedButtonFlags: Int, capabilities: Short) = 0
    override fun sendControllerBatteryEvent(controllerNumber: Byte, batteryState: Byte, batteryPercentage: Byte) {}

    // --- Controller --------------------------------------------------------------------------------------------------------------

    private val padLock = Any()
    private var pad = PsControllerState()
    private var orientation = floatArrayOf(0f, 0f, 0f, 1f)
    private var lastGyroNs = 0L
    private val touches = LinkedHashMap<Int, PsTouch>()
    @Volatile private var ps5 = true

    /** The first controller plays; Remote Play announces one controller (6.3). */
    override fun sendControllerInput(controllerNumber: Short, activeGamepadMask: Short, buttonFlags: Int, leftTrigger: Byte, rightTrigger: Byte,
                                     leftStickX: Short, leftStickY: Short, rightStickX: Short, rightStickY: Short) {
        if (controllerNumber.toInt() != 0) return
        var buttons = 0
        for ((flag, button) in BUTTONS) if (buttonFlags and flag != 0) buttons = buttons or button.mask
        update { it.copy(buttons = buttons, l2 = leftTrigger.toInt() and 0xFF, r2 = rightTrigger.toInt() and 0xFF,
            lx = leftStickX, ly = flip(leftStickY), rx = rightStickX, ry = flip(rightStickY)) }
    }

    /** Gyro in deg/s and accel in m/s² from the controller handler, as rad/s and g with an orientation integrated from the gyro. */
    override fun sendControllerMotionEvent(controllerNumber: Byte, motionType: Byte, x: Float, y: Float, z: Float): Int {
        if (controllerNumber.toInt() != 0) return 0
        when (motionType) {
            MoonBridge.LI_MOTION_TYPE_GYRO -> {
                val gx = Math.toRadians(x.toDouble()).toFloat(); val gy = Math.toRadians(y.toDouble()).toFloat(); val gz = Math.toRadians(z.toDouble()).toFloat()
                update {
                    val now = System.nanoTime()
                    val dt = if (lastGyroNs == 0L) 0f else ((now - lastGyroNs) / 1e9f).coerceAtMost(.1f)
                    lastGyroNs = now
                    integrate(gx, gy, gz, dt)
                    it.copy(gyroX = gx, gyroY = gy, gyroZ = gz, orientX = orientation[0], orientY = orientation[1], orientZ = orientation[2], orientW = orientation[3])
                }
            }
            MoonBridge.LI_MOTION_TYPE_ACCEL -> update { it.copy(accelX = x / G, accelY = y / G, accelZ = z / G) }
        }
        return 0
    }

    /** Touchpad pointers (0..1 of the pad) as the console's touch coordinates (8.3). */
    override fun sendControllerTouchEvent(controllerNumber: Byte, eventType: Byte, pointerId: Int, x: Float, y: Float, pressure: Float): Int {
        if (controllerNumber.toInt() != 0) return 0
        val id = pointerId and 0x7F
        synchronized(padLock) {
            when (eventType) {
                MoonBridge.LI_TOUCH_EVENT_DOWN, MoonBridge.LI_TOUCH_EVENT_MOVE ->
                    touches[id] = PsTouch(id, (x * (TOUCH_WIDTH - 1)).toInt().coerceIn(0, TOUCH_WIDTH - 1), (y * touchHeight()).toInt().coerceIn(0, touchHeight()))
                MoonBridge.LI_TOUCH_EVENT_UP, MoonBridge.LI_TOUCH_EVENT_CANCEL -> touches.remove(id)
                MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL -> touches.clear()
                else -> return 0
            }
        }
        update { it.copy(touches = touches.values.toList()) }
        return 0
    }

    private fun touchHeight() = if (ps5) TOUCH_HEIGHT_DUALSENSE else TOUCH_HEIGHT_DS4

    private fun update(change: (PsControllerState) -> PsControllerState) {
        val next = synchronized(padLock) { change(pad).also { pad = it } }
        stream?.updateController(next)
    }

    /** A press of [button] from the stream menu (PS or Create, which a handheld's controls may not have): down, then up 120 ms later. */
    fun tapButton(button: PsButton) {
        update { it.copy(buttons = it.buttons or button.mask) }
        thread(name = "ps-button", isDaemon = true) {
            Thread.sleep(120)
            update { it.copy(buttons = it.buttons and button.mask.inv()) }
        }
    }

    private fun integrate(gx: Float, gy: Float, gz: Float, dt: Float) {
        val angle = sqrt(gx * gx + gy * gy + gz * gz) * dt
        if (angle <= 0f) return
        val axis = sqrt(gx * gx + gy * gy + gz * gz)
        val s = sin(angle / 2) / axis
        val dq = floatArrayOf(gx * s, gy * s, gz * s, cos(angle / 2))
        val q = orientation
        val r = floatArrayOf(
            q[3] * dq[0] + q[0] * dq[3] + q[1] * dq[2] - q[2] * dq[1],
            q[3] * dq[1] - q[0] * dq[2] + q[1] * dq[3] + q[2] * dq[0],
            q[3] * dq[2] + q[0] * dq[1] - q[1] * dq[0] + q[2] * dq[3],
            q[3] * dq[3] - q[0] * dq[0] - q[1] * dq[1] - q[2] * dq[2],
        )
        val n = sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2] + r[3] * r[3])
        orientation = floatArrayOf(r[0] / n, r[1] / n, r[2] / n, r[3] / n)
    }

    // --- Video -------------------------------------------------------------------------------------------------------------------

    /**
     * Hands frames to the decoder renderer on its own thread, in the shape moonlight-common-c does (callbacks.c): before each keyframe
     * its parameter sets one by one with their buffer type, then the picture. Frame numbers are the console's 16-bit indices unwrapped
     * to a counter from 1. When the decoder needs a keyframe, or the queue overflows, the console is asked for one and frames are
     * dropped until it comes.
     */
    private inner class VideoFeeder(private val renderer: VideoDecoderRenderer, private val hevc: Boolean, header: ByteArray, depth: Int) {
        private val queue = ArrayBlockingQueue<Pair<ByteArray, Int>>(depth)
        private val parameterSets = LinkedHashMap<Int, ByteArray>()
        @Volatile private var active = true
        @Volatile private var waitForKeyframe = true
        private var lastIndex = -1
        private var frameNumber = 0
        private val worker = Thread(::run, "ps-video-feeder")

        init { for (nal in Nal.split(header)) if (isParameterSet(nal)) parameterSets[nalType(nal)] = nal }

        fun start() = worker.start()

        fun offer(frame: ByteArray, index: Int) {
            if (queue.offer(frame to index)) return
            queue.clear()
            keyframeNeeded()
        }

        private fun keyframeNeeded() {
            if (!waitForKeyframe) Log.i(TAG, "waiting for a keyframe")
            waitForKeyframe = true
            stream?.requestKeyframe()
        }

        private fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            while (active) {
                val (frame, index) = try { queue.poll(100, TimeUnit.MILLISECONDS) ?: continue } catch (_: InterruptedException) { return }
                frameNumber += if (lastIndex < 0) 1 else ((index - lastIndex) and 0xFFFF).coerceAtLeast(1)
                lastIndex = index
                submit(frame, frameNumber)
            }
        }

        private fun submit(frame: ByteArray, number: Int) {
            val nals = Nal.split(frame)
            val picture = ArrayList<ByteArray>(nals.size)
            var keyframe = false
            for (nal in nals) {
                if (isParameterSet(nal)) { parameterSets[nalType(nal)] = nal; continue }
                if (isKeyframe(nal)) keyframe = true
                picture += nal
            }
            if (picture.isEmpty()) return
            if (waitForKeyframe && !keyframe) return
            if (keyframe) waitForKeyframe = false
            val now = SystemClock.uptimeMillis() * 1000
            val type = if (keyframe) MoonBridge.FRAME_TYPE_IDR else MoonBridge.FRAME_TYPE_PFRAME
            if (keyframe) for ((nalType, nal) in parameterSets) {
                val result = renderer.submitDecodeUnit(nal, nal.size, bufferType(nalType), number, type, 0.toChar(), now, now)
                if (result == MoonBridge.DR_NEED_IDR) { keyframeNeeded(); return }
            }
            val data = if (picture.size == 1) picture[0] else ByteArray(picture.sumOf { it.size }).also { out -> var at = 0; for (p in picture) { p.copyInto(out, at); at += p.size } }
            if (renderer.submitDecodeUnit(data, data.size, MoonBridge.BUFFER_TYPE_PICDATA, number, type, 0.toChar(), now, now) == MoonBridge.DR_NEED_IDR) keyframeNeeded()
        }

        private fun nalType(nal: ByteArray): Int {
            val b = nal[Nal.payloadOffset(nal)].toInt()
            return if (hevc) (b shr 1) and 0x3F else b and 0x1F
        }

        private fun isParameterSet(nal: ByteArray) = nalType(nal).let { if (hevc) it in 32..34 else it == 7 || it == 8 }

        private fun isKeyframe(nal: ByteArray) = nalType(nal).let { if (hevc) it in 16..21 else it == 5 }

        private fun bufferType(nalType: Int) = when (nalType) {
            32 -> MoonBridge.BUFFER_TYPE_VPS
            33, 7 -> MoonBridge.BUFFER_TYPE_SPS
            else -> MoonBridge.BUFFER_TYPE_PPS
        }

        fun stop() {
            active = false
            worker.interrupt()
            runCatching { worker.join(500) }
        }
    }

    // --- Audio -------------------------------------------------------------------------------------------------------------------

    /**
     * The console's Opus units (7.4) into the audio renderer: a jitter buffer ordered by frame index (PsPrefs.AUDIO_BUFFER), played one
     * frame per unit on its own thread, a missing frame concealed by Opus; mono becomes stereo, and the channels can be swapped.
     */
    private inner class AudioSource(header: ByteArray) : AutoCloseable {
        private val channels = u(header[0])
        private val rate = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int
        private val frameSamples = ByteBuffer.wrap(header, 6, 4).order(ByteOrder.BIG_ENDIAN).int.takeIf { it > 0 } ?: 480
        private val opus = PsOpus(rate, channels)
        private val queue = TreeMap<Int, ByteArray>()
        private val startFrames: Int
        private val maxFrames: Int
        private val swap = psPrefs.getBoolean(PsPrefs.SWAP_CHANNELS, false)
        private var next = -1
        @Volatile private var active = true
        private val worker = Thread(::run, "ps-audio")

        init {
            PsPrefs.audioBuffer(psPrefs).let { (start, max) -> startFrames = start; maxFrames = max }
            val renderer = audio ?: throw IllegalStateException("no audio renderer")
            if (renderer.setup(MoonBridge.AUDIO_CONFIGURATION_STEREO, rate, frameSamples) != 0) throw IllegalStateException("audio renderer setup")
            renderer.start()
        }

        fun start() {
            worker.priority = Thread.MAX_PRIORITY
            worker.start()
        }

        fun offer(packet: ByteArray, frameIndex: Int) {
            synchronized(queue) {
                if (next >= 0 && ((frameIndex - next) and 0xFFFF) > 0x8000) return
                queue.putIfAbsent(frameIndex, packet)
                while (queue.size > maxFrames) queue.pollFirstEntry()?.let { if (next >= 0) next = (it.key + 1) and 0xFFFF }
                (queue as Object).notifyAll()
            }
        }

        private fun run() {
            val decoded = ShortArray(frameSamples * channels)
            val stereo = ShortArray(frameSamples * 2)
            while (active) {
                val packet: ByteArray?
                synchronized(queue) {
                    if (next < 0) {
                        while (active && queue.size < startFrames) (queue as Object).wait(50)
                        if (!active) return
                        next = queue.firstKey()
                    }
                    // Wait one frame for the next unit; a missing one is concealed
                    if (!queue.containsKey(next) && queue.isNotEmpty()) (queue as Object).wait(frameSamples * 1000L / rate)
                    if (queue.isEmpty()) { next = -1; continue }
                    packet = queue.remove(next)
                    next = (next + 1) and 0xFFFF
                }
                val samples = opus.decode(packet, decoded, frameSamples)
                if (samples <= 0) continue
                for (i in 0 until samples) {
                    val l = decoded[i * channels]
                    val r = if (channels > 1) decoded[i * channels + 1] else l
                    stereo[i * 2] = if (swap) r else l
                    stereo[i * 2 + 1] = if (swap) l else r
                }
                audio?.playDecodedAudio(if (samples == frameSamples) stereo else stereo.copyOf(samples * 2))
            }
        }

        override fun close() {
            active = false
            synchronized(queue) { (queue as Object).notifyAll() }
            runCatching { worker.join(300) }
            opus.close()
        }
    }

    companion object {
        /** The intent extra of DelosStreamActivity that makes it a PlayStation stream: the console's id in [PsConsoleStore]. */
        const val EXTRA_CONSOLE = "ps_console"

        private const val TAG = "PsConnection"
        // The stage names the loader tells a line for (StreamLoader.STAGE_LINES)
        private const val STAGE_WAKE = "PlayStation wake"
        private const val STAGE_STREAM = "Remote Play session"
        private const val PS_PORT = 9295
        private const val SILENCE_MS = 10_000L
        private const val MOTION_RATE_HZ: Short = 200
        private const val G = 9.80665f
        private const val TOUCH_WIDTH = 1920
        private const val TOUCH_HEIGHT_DUALSENSE = 1079
        private const val TOUCH_HEIGHT_DS4 = 941

        /** The stream sizes Remote Play offers (9), smallest first. */
        private val RESOLUTIONS = listOf(640 to 360, 960 to 540, 1280 to 720, 1920 to 1080)

        private val BUTTONS = listOf(
            ControllerPacket.A_FLAG to PsButton.CROSS, ControllerPacket.B_FLAG to PsButton.CIRCLE,
            ControllerPacket.X_FLAG to PsButton.SQUARE, ControllerPacket.Y_FLAG to PsButton.TRIANGLE,
            ControllerPacket.UP_FLAG to PsButton.DPAD_UP, ControllerPacket.DOWN_FLAG to PsButton.DPAD_DOWN,
            ControllerPacket.LEFT_FLAG to PsButton.DPAD_LEFT, ControllerPacket.RIGHT_FLAG to PsButton.DPAD_RIGHT,
            ControllerPacket.LB_FLAG to PsButton.L1, ControllerPacket.RB_FLAG to PsButton.R1,
            ControllerPacket.LS_CLK_FLAG to PsButton.L3, ControllerPacket.RS_CLK_FLAG to PsButton.R3,
            ControllerPacket.PLAY_FLAG to PsButton.OPTIONS, ControllerPacket.BACK_FLAG to PsButton.SHARE,
            ControllerPacket.SPECIAL_BUTTON_FLAG to PsButton.PS, ControllerPacket.TOUCHPAD_FLAG to PsButton.TOUCHPAD,
        )

        /** Moonlight's stick Y is positive up; the console's is positive down. */
        private fun flip(y: Short): Short = (-y.toInt()).coerceIn(-32768, 32767).toShort()
    }
}

/** Annex-B NAL units: split on start codes, each unit keeping its own start code (as the decoder renderer expects). */
internal object Nal {
    fun split(data: ByteArray): List<ByteArray> {
        val starts = ArrayList<Int>()
        var i = 0
        while (i + 3 <= data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0 && data[i + 2].toInt() == 1) {
                starts += if (i > 0 && data[i - 1].toInt() == 0) i - 1 else i
                i += 3
            } else i++
        }
        if (starts.isEmpty()) return if (data.isEmpty()) emptyList() else listOf(data)
        return starts.mapIndexed { n, from -> data.copyOfRange(from, if (n + 1 < starts.size) starts[n + 1] else data.size) }
            .filter { it.size > payloadOffset(it) }
    }

    /** The first byte after the start code. */
    fun payloadOffset(nal: ByteArray): Int = if (nal.size > 3 && nal[2].toInt() == 0) 4 else 3
}
