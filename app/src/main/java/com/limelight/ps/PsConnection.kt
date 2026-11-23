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
    // The session's stream info and request, kept so a parked session can set up the decoder again (PsSessionPark)
    @Volatile private var streamInfo: PsStreamInfo? = null
    @Volatile private var sessionRequest: PsStreamRequest? = null

    /**
     * Set by the stream activity when it goes to the background without the user leaving the stream: [stop] parks the session
     * (PsSessionPark) instead of closing it, so starting this console again resumes it (#121).
     */
    @Volatile var parkOnStop = false

    /** The console this stream plays, for the activity's title and the loader. */
    val console: com.limelight.shell.ps.PsConsole? = PsConsoleStore.get(appContext).consoles.value.firstOrNull { it.id == consoleId }

    @Volatile private var consoleTargetKbps = -1
    @Volatile private var keyframeWaits = 0

    /** The figures the statistics panel shows for a console stream (F9); counted over this stream's sessions. */
    fun consoleStats(): com.limelight.shell.ConsoleStats {
        val s = stream
        return com.limelight.shell.ConsoleStats(consoleTargetKbps, s?.repairedTotal?.get() ?: 0, s?.lostTotal?.get() ?: 0, keyframeWaits)
    }

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
            if (resumeParked(c)) return
            openSession(c, ctrlTries = 15) {
                listener.stageComplete(STAGE_WAKE)
                listener.stageStarting(STAGE_STREAM)
            }
        } catch (e: Exception) {
            Log.w(TAG, "connect", e)
            abandonSession()
            if (running) listener.stageFailed(if (stream == null) STAGE_WAKE else STAGE_STREAM, 0, -1)
        }
    }

    /** Closes what an unfinished or failed start opened (ctrl, stream): no console session may outlive [stop] or a failure. */
    private fun abandonSession() {
        runCatching { stream?.close() }; stream = null
        runCatching { ctrl?.close() }; ctrl = null
    }

    /** A session this console left in the background: it streams again at once, from a keyframe (#121). */
    private fun resumeParked(c: com.limelight.shell.ps.PsConsole): Boolean {
        val (s, session, saved) = PsSessionPark.take(c.id) ?: return false
        // A parked session that went quiet (the console or the Wi-Fi gone while it waited) is not taken back
        if (!s.alive || s.quietMs > PARK_QUIET_MS) { runCatching { s.close() }; runCatching { session?.close() }; return false }
        val (request, info) = saved
        ctrl = session
        stream = s
        ps5 = c.console.target.ps5
        listener.stageComplete(STAGE_WAKE)
        listener.stageStarting(STAGE_STREAM)
        val l = streamListener(request)
        s.listener = l
        l.onStreamInfo(info.width, info.height, info.videoHeader, info.audioHeader)
        s.requestKeyframe()
        return true
    }

    /**
     * Finds and wakes the console, opens the ctrl session (retried [ctrlTries] times, 2 s apart, while a console in rest mode wakes),
     * then the stream; [beforeStream] runs once the session id is there. Throws when a step fails.
     */
    private fun openSession(c: com.limelight.shell.ps.PsConsole, ctrlTries: Int, stepDown: Boolean = false, beforeStream: () -> Unit = {}) {
        val address = locate(c)
        runCatching { PsDiscovery.wake(address, c.console.target.ps5, c.console.registKey) }
        val request = request(c.console.target, stepDown)
        var opened: PsCtrl? = null
        var lastError: Exception? = null
        for (attempt in 0 until ctrlTries) {
            if (!running) { opened?.close(); return }
            try { opened = PsCtrl(address, PS_PORT, c.console, request.codec).also { it.open() }; break }
            catch (e: Exception) { lastError = e; Thread.sleep(2000) }
        }
        val session = opened ?: throw lastError ?: PsException("no session")
        ctrl = session
        if (!running) { abandonSession(); return }
        var sessionId: String? = null
        val ready = Object()
        thread(name = "ps-ctrl") {
            runCatching { session.readLoop { type, payload -> if (type == PsCtrl.SESSION_ID) { sessionId = PsCtrl.sessionId(payload); synchronized(ready) { ready.notifyAll() } } } }
        }
        synchronized(ready) { if (sessionId == null) ready.wait(10_000) }
        val id = sessionId ?: throw PsException("the console sent no session id")
        session.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); session.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); session.send(PsCtrl.DISPLAY_DEVICES, ByteArray(4))
        if (!running) { abandonSession(); return }
        beforeStream()
        ps5 = c.console.target.ps5
        // The network test (6.4, #122): once per console while the app runs (a failure too, so a console that does not answer it
        // costs its wait once); a reconnect reuses the result
        val net = synchronized(NETWORK) { NETWORK[c.id] } ?: PsSenkusha(address).run().also { n -> synchronized(NETWORK) { NETWORK[c.id] = n } }
        val measured = request.copy(mtuIn = net.mtuIn, mtuOut = net.mtuOut, rttMs = net.rttMs, dontFragment = net.measured)
        if (!running) { abandonSession(); return }
        val s = PsStream(address, c.console.target, id, session.crypt, measured, streamListener(measured),
            keyframeOnLoss = psPrefs.getBoolean(PsPrefs.KEYFRAME_ON_LOSS, false), holdBitrate = !PsPrefs.autoBitrate(psPrefs),
            visibleLossOnly = psPrefs.getBoolean(PsPrefs.VISIBLE_LOSS, true))
        stream = s
        s.start()
        // stop() ran meanwhile: it closed what it saw, this session is closed here
        if (!running) abandonSession()
    }

    /**
     * After [SILENCE_MS] without a packet (Wi-Fi gone for a while, a roam): the old session is closed and a new one opened for up to
     * [RECONNECT_MS], keeping the stream screen, the decoder and the audio; the feeder waits for the new session's keyframe (#120).
     */
    private fun reconnect(): Boolean {
        val c = console ?: return false
        // A session that opens but never sends a packet is not tried for ever: three such reconnects in a row end the stream
        consecutiveReconnects = if (packetSinceReconnect) 1 else consecutiveReconnects + 1
        if (consecutiveReconnects > MAX_QUIET_RECONNECTS) { Log.i(TAG, "no packet after $MAX_QUIET_RECONNECTS reconnects"); return false }
        packetSinceReconnect = false
        Log.i(TAG, "no packet for ${SILENCE_MS / 1000} s: reconnecting")
        abandonSession()
        // The path may have changed (another access point): Senkusha measures it again, and don't-fragment follows the new result
        synchronized(NETWORK) { NETWORK.remove(c.id) }
        synchronized(FOUND) { FOUND.remove(c.id) }
        val until = SystemClock.uptimeMillis() + RECONNECT_MS
        while (running && SystemClock.uptimeMillis() < until) {
            try {
                // Automatic step-down (F8): a second reconnect in a row asks for 720p at most (SGSR upscales it); the
                // decoder takes the new size from the session's parameter sets
                val stepDown = consecutiveReconnects >= 2
                if (stepDown) Log.i(TAG, "reconnecting at 720p at most after repeated drops")
                openSession(c, ctrlTries = 3, stepDown = stepDown)
                if (stream != null) {
                    lastPacketMs = SystemClock.uptimeMillis()
                    feeder?.lost()
                    // The new ctrl session has no microphone until it is told again (8.5 step 1)
                    microphone?.let { mic ->
                        runCatching { ctrl?.send(PsCtrl.MIC_CONNECT, byteArrayOf(0, 0)) }
                        setMicrophoneMuted(mic.muted)
                    }
                    Log.i(TAG, "reconnected")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "reconnect: ${e.message}")
                abandonSession()
                Thread.sleep(1000)
            }
        }
        return false
    }

    // Reconnects in a row that brought no packet, and whether one came since the last reconnect
    private var consecutiveReconnects = 0
    @Volatile private var packetSinceReconnect = true

    /**
     * Where the console is now: a broadcast search that stops at its answer (its host id is the paired MAC), so a console that
     * moved to another address (Wi-Fi to Ethernet, a new lease; owner, 2026-11-22) is still found and remembered there. A
     * console in rest mode answers too. Without an answer within 1.5 s the stored address is used. An answer the start screen
     * found in the last minute ([prefetch], F6) is used without searching again.
     */
    private fun locate(c: com.limelight.shell.ps.PsConsole): InetAddress {
        synchronized(FOUND) { FOUND[c.id] }?.let { (address, at) ->
            if (SystemClock.uptimeMillis() - at < FOUND_MS) return address
        }
        return search(appContext, c) ?: InetAddress.getByName(c.address)
    }

    /**
     * The stream the settings ask for, within what Remote Play offers (docs/research/ps-remote-play-protocol.md, 9). The bitrate is the
     * console's ceiling: a fixed one is held, the automatic one is the most this size takes and the console moves below it.
     */
    private fun request(target: PsTarget, stepDown: Boolean = false): PsStreamRequest {
        val size = RESOLUTIONS.lastOrNull { it.second <= prefs.height } ?: RESOLUTIONS.first()
        val maxHeight = if (stepDown) 720 else if (target.ps5) 1080 else 720
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
            // A new session after a reconnect: the decoder keeps running, the feeder takes the parameter sets and waits for a keyframe
            if (started) { feeder?.profile(width, height, videoHeader); feeder?.lost(); return }
            streamInfo = PsStreamInfo(width, height, videoHeader, audioHeader)
            sessionRequest = request
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
            startMicrophone()
        }

        override fun onVideoFrame(frame: ByteArray, index: Int, receivedUs: Long) {
            lastPacketMs = SystemClock.uptimeMillis()
            packetSinceReconnect = true
            feeder?.offer(frame, index, receivedUs)
        }

        override fun onVideoLost() { feeder?.lost() }

        override fun onVideoProfile(width: Int, height: Int, videoHeader: ByteArray) { feeder?.profile(width, height, videoHeader) }

        override fun onAudio(opus: ByteArray, frameIndex: Int) {
            lastPacketMs = SystemClock.uptimeMillis()
            packetSinceReconnect = true
            audioSource?.offer(opus, frameIndex)
        }

        override fun onRumble(left: Int, right: Int) = listener.rumble(0, (left * 257).toShort(), (right * 257).toShort())

        // Both triggers' effects to a DualSense DELOS drives over USB (#125): 0x04 right, 0x08 left, as a PC host's 0x5503
        override fun onTriggerEffects(leftMode: Int, rightMode: Int, left: ByteArray, right: ByteArray) =
            listener.setAdaptiveTriggers(0, 0x0C, leftMode.toByte(), rightMode.toByte(), left, right)

        override fun onPadInfo(red: Int, green: Int, blue: Int, motionReset: Boolean) {
            listener.setControllerLED(0, red.toByte(), green.toByte(), blue.toByte())
            if (motionReset) synchronized(padLock) { orientation = floatArrayOf(0f, 0f, 0f, 1f) }
        }

        // The console's own rtt figure does not match the link (about 190 for a 6 ms ping on the Odin, 2026-11-22): it is logged;
        // the network latency shown is the round trip DELOS measures on the Takion acks
        override fun onConnectionQuality(rtt: Double, targetKbps: Int) {
            if (qualityLogs < 3) { qualityLogs++; Log.i(TAG, "connection quality: rtt=$rtt target=$targetKbps") }
            // The field carries bits per second (48543000 seen at 1080p, 2026-11-22)
            consoleTargetKbps = targetKbps / 1000
        }

        override fun onRtt(rttMs: Int, varianceMs: Int) {
            rttInfo.set((rttMs.toLong() shl 32) or (varianceMs.toLong() and 0xFFFFFFFFL))
        }

        override fun onEnded(reason: String) {
            Log.i(TAG, "ended: $reason")
            if (running && started) listener.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        }
    }

    /**
     * A console that goes quiet (Wi-Fi gone, a roam): after [POOR_MS] the poor-connection notice shows, after [SILENCE_MS] a new
     * session is tried ([reconnect]), and only when that fails the stream ends.
     */
    private fun watchdog() {
        var poor = false
        while (running) {
            Thread.sleep(500)
            if (!running) return
            val silent = SystemClock.uptimeMillis() - lastPacketMs
            if (silent > POOR_MS != poor) {
                poor = !poor
                listener.connectionStatusUpdate(if (poor) MoonBridge.CONN_STATUS_POOR else MoonBridge.CONN_STATUS_OKAY)
            }
            if (silent > SILENCE_MS) {
                if (!reconnect()) { if (running) listener.connectionTerminated(-1); return }
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
        microphone?.close(); microphone = null
        if (restOnStop) runCatching { ctrl?.send(PsCtrl.GOTO_BED) }.onFailure { Log.w(TAG, "rest mode: ${it.message}") }
        val s = stream; val info = streamInfo; val request = sessionRequest
        if (parkOnStop && !restOnStop && s != null && s.alive && info != null && request != null) {
            // Nothing stays held in the game while nobody plays: sticks centred, buttons and triggers released
            synchronized(padLock) { pad = PsControllerState(); touches.clear() }
            s.updateController(PsControllerState())
            PsSessionPark.park(consoleId, s, ctrl, request, info)
        }
        else { s?.close(); ctrl?.close() }
        stream = null
        ctrl = null
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
    /**
     * The screen as the DualSense touchpad (F4, owner 2026-11-23: gestures, not a click): fingers on the stream move as touches on
     * the console's touchpad, so swipes reach the game; pressing the pad is the touchpad button (Select on the Odin).
     */
    override fun sendTouchEvent(eventType: Byte, pointerId: Int, x: Float, y: Float, pressureOrDistance: Float, contactAreaMajor: Float, contactAreaMinor: Float, rotation: Short): Int {
        if (eventType == MoonBridge.LI_TOUCH_EVENT_HOVER || eventType == MoonBridge.LI_TOUCH_EVENT_HOVER_LEAVE) return 0
        return sendControllerTouchEvent(0, eventType, pointerId, x.coerceIn(0f, 1f), y.coerceIn(0f, 1f), pressureOrDistance)
    }
    override fun sendPenEvent(eventType: Byte, toolType: Byte, penButtons: Byte, x: Float, y: Float, pressureOrDistance: Float, contactAreaMajor: Float, contactAreaMinor: Float, rotation: Short, tilt: Byte) = MoonBridge.LI_ERR_UNSUPPORTED
    override fun sendUtf8Text(text: String) {}
    override fun sendControllerArrivalEvent(controllerNumber: Byte, activeGamepadMask: Short, type: Byte, supportedButtonFlags: Int, capabilities: Short): Int {
        if (controllerNumber.toInt() != 0) return 0
        firstPadIsPlayStation = type == MoonBridge.LI_CTYPE_PS
        // A pad that arrives after the stream started (the Odin's comes a few seconds later) has no motion listeners yet: ask
        // for its gyro and accelerometer again, as a PC host does on an arrival (gyro aiming and the console's motion need them)
        if (started) android.os.Handler(android.os.Looper.getMainLooper()).post {
            listener.setMotionEventState(0, MoonBridge.LI_MOTION_TYPE_GYRO, MOTION_RATE_HZ)
            listener.setMotionEventState(0, MoonBridge.LI_MOTION_TYPE_ACCEL, MOTION_RATE_HZ)
        }
        return 0
    }

    // Whether the controller that plays is a PlayStation pad (it has its own Create button and touchpad)
    @Volatile private var firstPadIsPlayStation = false
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
        // A pad without Create and a touchpad (a handheld's built-in controls, owner 2026-11-23): Select (the upper left menu
        // button) clicks the touchpad, and Guide is Create: on the Odin that is holding the lower right Back button (a tap opens
        // the stream menu, as on a PC stream); its Home button never reaches apps. A PlayStation pad keeps its own buttons.
        val table = if (firstPadIsPlayStation) BUTTONS else HANDHELD_BUTTONS
        var buttons = 0
        for ((flag, button) in table) if (buttonFlags and flag != 0) buttons = buttons or button.mask
        baseRx = rightStickX; baseRy = flip(rightStickY); aimHeld = (leftTrigger.toInt() and 0xFF) > AIM_TRIGGER
        update { it.copy(buttons = buttons, l2 = leftTrigger.toInt() and 0xFF, r2 = rightTrigger.toInt() and 0xFF,
            lx = leftStickX, ly = flip(leftStickY), rx = aimedX(), ry = aimedY()) }
    }

    // Gyro aiming (F5, owner 2026-11-23): the device's turn added to the right stick, always or while L2 is held, for any game.
    // The right stick as the pad has it, whether L2 aims, and the current offset from the gyro (console stick units)
    @Volatile private var baseRx: Short = 0
    @Volatile private var baseRy: Short = 0
    @Volatile private var aimHeld = false
    @Volatile private var aimX = 0f
    @Volatile private var aimY = 0f
    private val gyroAim = psPrefs.getString(PsPrefs.GYRO_AIM, "off") ?: "off"
    // Stick units per deg/s: full deflection at 120 deg/s at the normal sensitivity
    private val aimScale = 32767f / 120f * when (psPrefs.getString(PsPrefs.GYRO_AIM_SENSITIVITY, "normal")) { "low" -> .6f; "high" -> 1.6f; else -> 1f }

    private fun aiming() = gyroAim == "always" || (gyroAim == "aim" && aimHeld)
    private fun aimedX(): Short = (baseRx + (if (aiming()) aimX else 0f)).coerceIn(-32768f, 32767f).toInt().toShort()
    private fun aimedY(): Short = (baseRy + (if (aiming()) aimY else 0f)).coerceIn(-32768f, 32767f).toInt().toShort()

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
                if (gyroAim != "off") {
                    // DualShock axes (the controller handler's): y turns left, x tilts up; the stick's x is right, its y (console) down
                    aimX = -y * aimScale
                    aimY = -x * aimScale
                    if (aiming()) update { it.copy(rx = aimedX(), ry = aimedY()) }
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

    // --- Microphone (8.5, #124) ------------------------------------------------------------------------------------------------

    @Volatile private var microphone: PsMicrophone? = null

    /** Whether the microphone is on for this stream (the setting and the permission). */
    val microphoneAvailable: Boolean get() = microphone != null
    val microphoneMuted: Boolean get() = microphone?.muted != false

    /** The setting and the record-audio permission allow it: the console is told (MIC_CONNECT) and capture starts, unmuted. */
    private fun startMicrophone() {
        if (!psPrefs.getBoolean(PsPrefs.MICROPHONE, false)) return
        if (appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "microphone on in the settings but not permitted")
            return
        }
        runCatching { ctrl?.send(PsCtrl.MIC_CONNECT, byteArrayOf(0, 0)) }
        microphone = PsMicrophone { frame -> stream?.sendMicrophone(frame) }.also { it.start() }
    }

    /** Mutes or unmutes the microphone: capture stops sending and the console shows the state (MIC_TOGGLE, 4.5). */
    fun setMicrophoneMuted(muted: Boolean) {
        val mic = microphone ?: return
        mic.muted = muted
        runCatching { ctrl?.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, (if (muted) 0 else 1).toByte(), 0x59)) }
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
     * to a counter from 1. When a frame is lost, the decoder needs a keyframe, or the queue overflows, frames are dropped until a
     * keyframe (an IDR, or a picture whose first slice is an I slice) comes, and the console is asked again every second (7.3).
     */
    private inner class VideoFeeder(private val renderer: VideoDecoderRenderer, private val hevc: Boolean, header: ByteArray, depth: Int) {
        private val queue = ArrayBlockingQueue<Queued>(depth)
        private val parameterSets = LinkedHashMap<Int, ByteArray>()
        // HEVC: the PPS's num_extra_slice_header_bits, read to reach a slice's type
        @Volatile private var extraSliceBits = 0
        @Volatile private var active = true
        @Volatile private var waitForKeyframe = true
        // While waiting: when it began and when a keyframe was last asked for, and how often
        @Volatile private var waitSinceMs = 0L
        @Volatile private var askedAtMs = 0L
        private var requests = 0
        private var lastIndex = -1
        private var frameNumber = 0
        private val worker = Thread(::run, "ps-video-feeder")

        // HEVC reference rewrite (spec 7.3.1): after a loss, P pictures are bridged to an older held picture instead of waiting
        private val refs: PsHevcRefs? = if (hevc) PsHevcRefs() else null
        @Volatile private var bridgingSinceMs = 0L
        private var droppedInRow = 0
        private var rewriteFailures = 0
        private var refsLogMs = 0L
        init { setParameterSets(header) }

        fun start() = worker.start()

        fun offer(frame: ByteArray, index: Int, receivedUs: Long) {
            if (queue.offer(Queued(frame, index, receivedUs, System.nanoTime() / 1000))) return
            queue.clear()
            keyframeNeeded()
        }

        /**
         * The stream lost a frame and asked for a keyframe already: with the HEVC rewrite usable the following pictures are bridged
         * until it comes; otherwise pictures are dropped until one comes.
         */
        fun lost() {
            if (refs != null && refs.usable && rewriteFailures < 3 && !waitForKeyframe) {
                if (bridgingSinceMs == 0L) bridgingSinceMs = SystemClock.uptimeMillis()
                return
            }
            startWaiting(asked = true)
        }

        /** Another adaptive profile: its parameter sets replace the current ones before the next keyframe. */
        fun profile(width: Int, height: Int, header: ByteArray) {
            Log.i(TAG, "video profile ${width}x$height")
            setParameterSets(header)
        }

        private fun setParameterSets(header: ByteArray) {
            synchronized(parameterSets) { for (nal in Nal.split(header)) if (isParameterSet(nal)) parameterSets[nalType(nal)] = nal }
            parameterSets[34]?.let { extraSliceBits = hevcExtraSliceBits(it) }
            refs?.let { r -> parameterSets[33]?.let { r.parameterSet(it, 33) }; parameterSets[34]?.let { r.parameterSet(it, 34) } }
        }

        private fun keyframeNeeded() {
            startWaiting(asked = false)
            stream?.requestKeyframe()
        }

        private fun startWaiting(asked: Boolean) {
            val now = SystemClock.uptimeMillis()
            if (!waitForKeyframe) { Log.i(TAG, "waiting for a keyframe"); waitSinceMs = now; requests = 0; keyframeWaits++ }
            waitForKeyframe = true
            askedAtMs = now
            requests++
            if (asked && waitSinceMs == 0L) waitSinceMs = now
        }

        private fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            while (active) {
                val next = try { queue.poll(100, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { return }
                // Still waiting a second after the last request: the request or its answer was lost, ask again
                if (waitForKeyframe && SystemClock.uptimeMillis() - askedAtMs >= 1000) {
                    askedAtMs = SystemClock.uptimeMillis()
                    requests++
                    stream?.requestKeyframe()
                }
                val q = next ?: continue
                frameNumber += if (lastIndex < 0) 1 else ((q.index - lastIndex) and 0xFFFF).coerceAtLeast(1)
                lastIndex = q.index
                submit(q.frame, frameNumber, q.receivedUs, q.completeUs)
            }
        }

        /** [receivedUs] the first unit's arrival, [completeUs] the frame complete (System.nanoTime / 1000, the decoder's clock). */
        private fun submit(frame: ByteArray, number: Int, receivedUs: Long, completeUs: Long) {
            val nals = Nal.split(frame)
            val picture = ArrayList<ByteArray>(nals.size)
            var keyframe = false
            var firstSlice = true
            for (nal in nals) {
                if (isParameterSet(nal)) {
                    synchronized(parameterSets) { parameterSets[nalType(nal)] = nal }
                    if (hevc && nalType(nal) == 34) extraSliceBits = hevcExtraSliceBits(nal)
                    refs?.parameterSet(nal, nalType(nal))
                    continue
                }
                if (isSlice(nal) && firstSlice) { firstSlice = false; if (isKeyframe(nal)) keyframe = true }
                picture += nal
            }
            if (picture.isEmpty()) return
            if (waitForKeyframe && !keyframe) return
            if (keyframe) bridgingSinceMs = 0
            var rewritten = false
            if (refs != null && refs.usable && rewriteFailures < 3) {
                val before = refs.rewritten
                if (!refs.process(picture)) {
                    // No listed picture is held: drop it; after 3 in a row or 200 ms of bridging wait for the keyframe
                    droppedInRow++
                    val since = bridgingSinceMs
                    if (droppedInRow >= 3 || (since > 0 && SystemClock.uptimeMillis() - since > 200)) { droppedInRow = 0; bridgingSinceMs = 0; keyframeNeeded() }
                    return
                }
                droppedInRow = 0
                rewritten = refs.rewritten != before
                val now = SystemClock.uptimeMillis()
                if (now - refsLogMs >= 10_000 && (refs.rewritten + refs.dropped + refs.consoleRecovered) > 0) {
                    refsLogMs = now
                    Log.i(TAG, "reference rewrite 10 s: ${refs.rewritten} rewritten, ${refs.dropped} dropped, ${refs.consoleRecovered} recovered by the console")
                    refs.rewritten = 0; refs.dropped = 0; refs.consoleRecovered = 0
                }
            }
            if (keyframe && waitForKeyframe) {
                waitForKeyframe = false
                if (waitSinceMs > 0) Log.i(TAG, "keyframe after ${SystemClock.uptimeMillis() - waitSinceMs} ms, $requests requests")
                waitSinceMs = 0
            }
            val type = if (keyframe) MoonBridge.FRAME_TYPE_IDR else MoonBridge.FRAME_TYPE_PFRAME
            if (keyframe) for ((nalType, nal) in synchronized(parameterSets) { parameterSets.entries.map { it.key to it.value } }) {
                val result = renderer.submitDecodeUnit(nal, nal.size, bufferType(nalType), number, type, 0.toChar(), receivedUs, completeUs)
                if (result == MoonBridge.DR_NEED_IDR) { keyframeNeeded(); return }
            }
            val data = if (picture.size == 1) picture[0] else ByteArray(picture.sumOf { it.size }).also { out -> var at = 0; for (p in picture) { p.copyInto(out, at); at += p.size } }
            if (renderer.submitDecodeUnit(data, data.size, MoonBridge.BUFFER_TYPE_PICDATA, number, type, 0.toChar(), receivedUs, completeUs) == MoonBridge.DR_NEED_IDR) {
                if (rewritten && ++rewriteFailures >= 3) Log.w(TAG, "reference rewrite off for this stream: the decoder rejected rewritten pictures")
                keyframeNeeded()
            }
        }

        private fun nalType(nal: ByteArray): Int {
            val b = nal[Nal.payloadOffset(nal)].toInt()
            return if (hevc) (b shr 1) and 0x3F else b and 0x1F
        }

        private fun isParameterSet(nal: ByteArray) = nalType(nal).let { if (hevc) it in 32..34 else it == 7 || it == 8 }

        private fun isSlice(nal: ByteArray) = nalType(nal).let { if (hevc) it in 0..21 else it in 1..5 }

        /** An IDR (or HEVC IRAP), or an I slice in another NAL type, which the console may answer a keyframe request with (7.3). */
        private fun isKeyframe(nal: ByteArray): Boolean {
            val type = nalType(nal)
            if (if (hevc) type in 16..21 else type == 5) return true
            return runCatching { Nal.sliceType(nal, hevc, extraSliceBits) }.getOrNull() == Nal.SLICE_I
        }

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

    /** HEVC PPS: num_extra_slice_header_bits (after pps id, sps id and two flags), needed to reach a slice header's type. */
    private fun hevcExtraSliceBits(pps: ByteArray): Int = runCatching {
        val r = Nal.BitReader(Nal.rbsp(pps, Nal.payloadOffset(pps) + 2))
        r.ue(); r.ue(); r.bit(); r.bit(); r.bits(3)
    }.getOrDefault(0)

    // --- Audio -------------------------------------------------------------------------------------------------------------------

    /**
     * The console's Opus units (7.4) into the audio renderer: a jitter buffer keyed by the 16-bit frame index (PsPrefs.AUDIO_BUFFER),
     * filled to its start depth once and then played continuously, one frame per unit on its own thread (the renderer's blocking
     * write paces it). A unit that is late by more than a frame is concealed by Opus when later units are there; only after
     * [AUDIO_GAP_MS] without any unit does the buffer start over. Mono becomes stereo, and the channels can be swapped.
     */
    private inner class AudioSource(header: ByteArray) : AutoCloseable {
        private val channels = u(header[0])
        private val rate = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int
        private val frameSamples = ByteBuffer.wrap(header, 6, 4).order(ByteOrder.BIG_ENDIAN).int.takeIf { it > 0 } ?: 480
        private val frameMs = (frameSamples * 1000L / rate).coerceAtLeast(1)
        private val opus = PsOpus(rate, channels)
        // Keyed by the index modulo 2^16 (the console's counter wraps): order is the distance from [next], not the key
        private val queue = HashMap<Int, ByteArray>()
        private val startFrames: Int
        private val maxFrames: Int
        private val swap = psPrefs.getBoolean(PsPrefs.SWAP_CHANNELS, false)
        private var next = -1
        private var lastUnitMs = 0L
        // For the log, per 10 s: concealed frames and restarts of the buffer
        private var concealed = 0
        private var restarts = 0
        private var statsAtMs = 0L
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

        private fun distance(index: Int) = (index - next) and 0xFFFF

        fun offer(packet: ByteArray, frameIndex: Int) {
            val index = frameIndex and 0xFFFF
            synchronized(queue) {
                lastUnitMs = SystemClock.uptimeMillis()
                // Behind the frame being played: too late
                if (next >= 0 && distance(index) > 0x8000) return
                queue.putIfAbsent(index, packet)
                // Too deep (the console's clock runs ahead): the oldest units go
                while (queue.size > maxFrames) {
                    val oldest = if (next >= 0) queue.keys.minByOrNull(::distance)!! else queue.keys.first()
                    queue.remove(oldest)
                    if (next >= 0 && oldest == next) next = (next + 1) and 0xFFFF
                }
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
                        // The oldest unit: the one the others are furthest ahead of
                        next = queue.keys.minByOrNull { k -> queue.keys.maxOf { (it - k) and 0xFFFF } }!!
                    }
                    // The next unit may be a little late: wait up to one frame
                    if (!queue.containsKey(next)) (queue as Object).wait(frameMs)
                    if (!queue.containsKey(next) && queue.isEmpty()) {
                        // Nothing at all: a short hiccup is waited out frame by frame, a real gap starts the buffer over
                        if (SystemClock.uptimeMillis() - lastUnitMs > AUDIO_GAP_MS) { next = -1; restarts++ }
                        continue
                    }
                    packet = queue.remove(next)
                    if (packet == null) concealed++
                    next = (next + 1) and 0xFFFF
                }
                val samples = opus.decode(packet, decoded, frameSamples)
                if (samples > 0) {
                    for (i in 0 until samples) {
                        val l = decoded[i * channels]
                        val r = if (channels > 1) decoded[i * channels + 1] else l
                        stereo[i * 2] = if (swap) r else l
                        stereo[i * 2 + 1] = if (swap) l else r
                    }
                    audio?.playDecodedAudio(if (samples == frameSamples) stereo else stereo.copyOf(samples * 2))
                }
                logStats()
            }
        }

        private fun logStats() {
            val now = SystemClock.uptimeMillis()
            if (statsAtMs == 0L) statsAtMs = now
            if (now - statsAtMs < 10_000) return
            if (concealed > 0 || restarts > 0) Log.i(TAG, "audio 10 s: $concealed frames concealed, $restarts restarts, depth ${synchronized(queue) { queue.size }}")
            concealed = 0; restarts = 0; statsAtMs = now
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
        /** No packet for this long shows the poor-connection notice. */
        private const val POOR_MS = 2_500L
        /** How long a reconnect after silence keeps trying. */
        private const val RECONNECT_MS = 20_000L
        /** Reconnects in a row without a packet before the stream ends. */
        private const val MAX_QUIET_RECONNECTS = 3
        /** A parked session silent for longer than this is closed instead of taken back. */
        private const val PARK_QUIET_MS = 5_000L

        /** Closes a console session parked in the background: another stream (a PC's) starts and the console's must not compete. */
        @JvmStatic
        fun closeParkedSession() = PsSessionPark.closeAny()
        /** Audio: this long without any unit is a real gap, after which the jitter buffer fills to its start depth again. */
        private const val AUDIO_GAP_MS = 200L
        /** Senkusha's result per console id, for this process. */
        private val NETWORK = HashMap<String, PsNetwork>()
        private const val MOTION_RATE_HZ: Short = 200

        /** Consoles the start screen found ([prefetch]): the address and when (uptime ms). */
        private val FOUND = HashMap<String, Pair<InetAddress, Long>>()
        private const val FOUND_MS = 60_000L

        /**
         * The start screen shows [consoleId] (F6, faster start): it is searched for now, so tapping it opens the session without
         * the 1.5 s search. Off the main thread; the console is not woken.
         */
        @JvmStatic
        fun prefetch(context: Context, consoleId: String) {
            val c = PsConsoleStore.get(context).consoles.value.firstOrNull { it.id == consoleId } ?: return
            val address = search(context.applicationContext, c) ?: return
            synchronized(FOUND) { FOUND[c.id] = address to SystemClock.uptimeMillis() }
        }

        /** The broadcast search for [c] (see [locate]); stores a new address. Null without an answer. */
        private fun search(context: Context, c: com.limelight.shell.ps.PsConsole): InetAddress? {
            val answers = runCatching {
                PsDiscovery.search(InetAddress.getByName("255.255.255.255"), 1500) { it.hostId.equals(c.id, ignoreCase = true) }
            }.getOrDefault(emptyList())
            val found = answers.firstOrNull { it.hostId.equals(c.id, ignoreCase = true) }
            if (found == null) {
                Log.i(TAG, "console not found by search (${answers.size} answers, host ids ${answers.map { it.hostId }}, paired ${c.id})")
                return null
            }
            val now = found.address.hostAddress.orEmpty()
            if (now != c.address) {
                Log.i(TAG, "console found at a new address")
                PsConsoleStore.get(context).moved(c.id, now)
            }
            return found.address
        }
        /** L2 past this (of 255) is aiming, for gyro aiming while L2 is held. */
        private const val AIM_TRIGGER = 64
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

        /** [BUTTONS] for a pad without Create and a touchpad: Select is the touchpad click, Guide (Back held on the Odin) is Create. */
        private val HANDHELD_BUTTONS = BUTTONS.map { (flag, button) ->
            flag to when (flag) {
                ControllerPacket.BACK_FLAG -> PsButton.TOUCHPAD
                ControllerPacket.SPECIAL_BUTTON_FLAG -> PsButton.SHARE
                else -> button
            }
        }

        /** Moonlight's stick Y is positive up; the console's is positive down. */
        private fun flip(y: Short): Short = (-y.toInt()).coerceIn(-32768, 32767).toShort()
    }
}

/** A complete frame waiting for the decoder: its index, its first unit's arrival and when it was complete (µs, monotonic). */
private class Queued(val frame: ByteArray, val index: Int, val receivedUs: Long, val completeUs: Long)

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

    /** The slice type I (H.264 2 and 7, HEVC 2) as [sliceType] reports it. */
    const val SLICE_I = 2

    /**
     * The slice type of a first slice: H.264 first_mb_in_slice then slice_type (mod 5); HEVC first_slice_segment_in_pic_flag, for
     * IRAP types no_output_of_prior_pics_flag, the PPS id, [extraBits] reserved bits, then slice_type. Null when it is not the
     * picture's first slice (HEVC) or cannot be read.
     */
    fun sliceType(nal: ByteArray, hevc: Boolean, extraBits: Int): Int? {
        val start = payloadOffset(nal)
        val r = BitReader(rbsp(nal, start + if (hevc) 2 else 1))
        if (!hevc) { r.ue(); return r.ue() % 5 }
        val type = (nal[start].toInt() shr 1) and 0x3F
        if (r.bit() == 0) return null
        if (type in 16..23) r.bit()
        r.ue()
        r.bits(extraBits)
        return r.ue()
    }

    /** The payload from [from] without emulation-prevention bytes (00 00 03 → 00 00), at most the first 64 bytes. */
    fun rbsp(nal: ByteArray, from: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var zeros = 0
        var i = from
        while (i < nal.size && out.size() < 64) {
            val b = nal[i].toInt() and 0xFF
            if (zeros >= 2 && b == 3) { zeros = 0; i++; continue }
            zeros = if (b == 0) zeros + 1 else 0
            out.write(b)
            i++
        }
        return out.toByteArray()
    }

    /** Reads bits and Exp-Golomb numbers from the start of [data]; past the end it throws. */
    class BitReader(private val data: ByteArray) {
        var position = 0
            private set

        fun bit(): Int {
            if (position >= data.size * 8) throw IndexOutOfBoundsException("past the end")
            val b = (data[position / 8].toInt() shr (7 - position % 8)) and 1
            position++
            return b
        }

        fun bits(n: Int): Int { var v = 0; repeat(n) { v = (v shl 1) or bit() }; return v }

        fun ue(): Int {
            var zeros = 0
            while (bit() == 0) { zeros++; if (zeros > 31) throw IllegalStateException("bad Exp-Golomb code") }
            return (1 shl zeros) - 1 + bits(zeros)
        }
    }
}
