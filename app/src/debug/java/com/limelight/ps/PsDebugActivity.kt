package com.limelight.ps

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.InetAddress
import java.util.Base64
import kotlin.concurrent.thread

/**
 * Debug-build bench for PlayStation Remote Play (issue #25): search the network, register with a PIN, connect and show the
 * console's video. Not in release builds. Start it with
 * `adb shell am start -n io.github.meitoncz.delos.debug/com.limelight.ps.PsDebugActivity`.
 */
class PsDebugActivity : Activity() {
    private lateinit var log: TextView
    private lateinit var accountField: EditText
    private lateinit var pinField: EditText
    private lateinit var surface: SurfaceView
    private lateinit var panel: View
    private var hosts: List<PsHost> = emptyList()
    private var ctrl: PsCtrl? = null
    private var stream: PsStream? = null
    private var codec: MediaCodec? = null
    private var pad = PsControllerState()
    private var web: WebView? = null
    private lateinit var root: FrameLayout
    private var audio: PsAudioPlayer? = null

    /** Sony's sign-in page in a WebView; the redirect with the code is caught here and never loaded (spec 3.1). */
    private fun signIn() {
        CookieManager.getInstance().removeAllCookies(null)
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val code = PsOAuth.codeFrom(request.url.toString()) ?: return false
                closeWeb()
                thread {
                    try {
                        val id = PsAccountId.toBase64(PsOAuth.accountId(code))
                        prefs.edit().putString("account", id).apply()
                        runOnUiThread { accountField.setText(id) }
                        say("Signed in: account id $id")
                    } catch (e: Exception) { say("PSN sign-in failed: ${e.message}") }
                }
                return true
            }
        }
        web = w
        root.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        w.loadUrl(PsOAuth.authorizeUrl())
        say("Sign in on Sony's page; DELOS only receives the account id")
    }

    private fun closeWeb() = runOnUiThread { web?.let { root.removeView(it); it.destroy() }; web = null }

    override fun onBackPressed() { if (web != null) closeWeb() else @Suppress("DEPRECATION") super.onBackPressed() }

    private val prefs by lazy { getSharedPreferences("ps_debug", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(2, 11, 30)) }
        surface = SurfaceView(this).apply { visibility = View.GONE }
        root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 32) }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; setOnClickListener { action() }; column.addView(this) }
        column.addView(TextView(this).apply { text = "PlayStation Remote Play · debug"; textSize = 22f; setTextColor(Color.WHITE) })
        accountField = EditText(this).apply { hint = "PSN account id (base64, 12 characters)"; setText(prefs.getString("account", "")); setTextColor(Color.WHITE) }
        pinField = EditText(this).apply { hint = "8-digit PIN from the console"; setTextColor(Color.WHITE) }
        column.addView(accountField); column.addView(pinField)
        button("Sign in to PSN (fills the account id)") { signIn() }
        button("Search the network") { search() }
        button("Register (Settings › System › Remote Play › Pair Device open on the PS5)") { register() }
        button("Wake the registered console") { wake() }
        button("Connect and stream") { connect() }
        button("Stop") { stop() }
        log = TextView(this).apply { setTextColor(Color.rgb(198, 216, 242)); textSize = 14f }
        column.addView(log)
        panel = ScrollView(this).apply { addView(column) }
        root.addView(panel)
        this.root = root
        setContentView(root)
        say("Saved console: ${prefs.getString("nickname", "none")}")
    }

    private fun say(text: String) = runOnUiThread { log.append("$text\n") }

    private fun broadcast(): InetAddress = InetAddress.getByName("255.255.255.255")

    private fun search() = thread {
        say("Searching…")
        hosts = runCatching { PsDiscovery.search(broadcast(), 3000) }.getOrElse { say("search failed: ${it.message}"); emptyList() }
        if (hosts.isEmpty()) say("No console answered.")
        for (h in hosts) say("${h.name} ${h.address.hostAddress} ${if (h.ps5) "PS5" else "PS4"} ${if (h.awake) "on" else if (h.standby) "rest mode" else "status ${h.status}"} fw ${h.systemVersion} → ${h.target}")
    }

    private fun saved(): Pair<InetAddress, PsRegisteredConsole>? {
        val p = prefs
        val address = p.getString("address", null) ?: return null
        val target = PsTarget.valueOf(p.getString("target", null) ?: return null)
        val dec = Base64.getDecoder()
        return InetAddress.getByName(address) to PsRegisteredConsole(target, dec.decode(p.getString("regist", "")), dec.decode(p.getString("morning", "")),
            dec.decode(p.getString("mac", "")), p.getString("nickname", null), null)
    }

    private fun register() = thread {
        if (hosts.isEmpty()) hosts = runCatching { PsDiscovery.search(broadcast(), 3000) }.getOrDefault(emptyList())
        val host = hosts.firstOrNull() ?: return@thread say("No console found on the network.")
        val account = PsAccountId.fromBase64(accountField.text.toString()) ?: return@thread say("The account id must be 8 bytes in base64.")
        val pin = pinField.text.toString().trim().toIntOrNull() ?: return@thread say("Enter the 8-digit PIN.")
        say("Registering with ${host.name} (${host.target})…")
        try {
            val c = PsRegistrationClient.register(host.address, REQUEST_PORT, host.target, account, pin)
            val enc = Base64.getEncoder()
            prefs.edit().putString("account", accountField.text.toString()).putString("address", host.address.hostAddress).putString("target", c.target.name)
                .putString("regist", enc.encodeToString(c.registKey)).putString("morning", enc.encodeToString(c.morning)).putString("mac", enc.encodeToString(c.mac))
                .putString("nickname", c.nickname ?: host.name).apply()
            say("Registered: ${c.nickname}, key type ${c.keyType}")
        } catch (e: Exception) { say("Registration failed: ${e.message}") }
    }

    private fun wake() = thread {
        val (address, console) = saved() ?: return@thread say("Register first.")
        runCatching { PsDiscovery.wake(address, console.target.ps5, console.registKey) }.onFailure { say("wake failed: ${it.message}") }
        say("Wake sent to $address")
    }

    private fun connect() = thread {
        val (address, console) = saved() ?: return@thread say("Register first.")
        try {
            say("Session init and ctrl…")
            val c = PsCtrl(address, REQUEST_PORT, console, PsCodec.HEVC)
            c.open()
            ctrl = c
            say("ctrl open, server type ${c.serverType}")
            var sessionId: String? = null
            val ready = Object()
            thread(name = "ps-ctrl") {
                runCatching {
                    c.readLoop { type, payload ->
                        when (type) {
                            PsCtrl.SESSION_ID -> { sessionId = PsCtrl.sessionId(payload); synchronized(ready) { ready.notifyAll() } }
                            PsCtrl.LOGIN_PIN_REQ -> say("The console asks for a sign-in passcode (not supported yet)")
                            else -> say("ctrl message 0x${type.toString(16)} (${payload.size} bytes)")
                        }
                    }
                }.onFailure { say("ctrl closed: ${it.message}") }
            }
            synchronized(ready) { if (sessionId == null) ready.wait(10_000) }
            val id = sessionId ?: return@thread say("No session id from the console.")
            say("Session id received; starting the stream")
            c.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); c.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); c.send(PsCtrl.DISPLAY_DEVICES, ByteArray(4))
            val s = PsStream(address, console.target, id, c.crypt, PsStreamRequest(), listener)
            stream = s
            s.start()
        } catch (e: Exception) { say("Connect failed: ${e.message}") }
    }

    private val listener = object : PsStreamListener {
        override fun onStatus(text: String) = say(text)
        override fun onEnded(reason: String) { say("Stream ended: $reason"); runOnUiThread { surface.visibility = View.GONE; panel.visibility = View.VISIBLE } }
        override fun onStreamInfo(width: Int, height: Int, videoHeader: ByteArray, audioHeader: ByteArray?) {
            say("STREAMINFO ${width}x$height, video header ${videoHeader.size} bytes, audio ${audioHeader?.let { PsHex.encode(it) }}")
            audioHeader?.let { h -> runCatching { audio = PsAudioPlayer(h) }.onFailure { say("audio: ${it.message}") } }
            runOnUiThread {
                panel.visibility = View.GONE
                surface.visibility = View.VISIBLE
                immersive(true)
                surface.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) = startDecoder(holder, width, height, videoHeader)
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                    override fun surfaceDestroyed(holder: SurfaceHolder) { codec?.release(); codec = null }
                })
                if (surface.holder.surface.isValid) startDecoder(surface.holder, width, height, videoHeader)
            }
        }
        override fun onVideoFrame(frame: ByteArray, index: Int) = feed(frame)
        override fun onAudio(opus: ByteArray, frameIndex: Int) { audio?.play(opus, frameIndex) }
    }

    private fun startDecoder(holder: SurfaceHolder, width: Int, height: Int, header: ByteArray) {
        if (codec != null) return
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC).apply {
            configure(format, holder.surface, null, 0)
            start()
        }
        feed(header, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
    }

    private fun feed(data: ByteArray, flags: Int = 0) {
        val c = codec ?: return
        runCatching {
            val i = c.dequeueInputBuffer(10_000)
            if (i >= 0) { c.getInputBuffer(i)!!.apply { clear(); put(data) }; c.queueInputBuffer(i, 0, data.size, System.nanoTime() / 1000, flags) }
            val info = MediaCodec.BufferInfo()
            var o = c.dequeueOutputBuffer(info, 0)
            while (o >= 0) { c.releaseOutputBuffer(o, true); o = c.dequeueOutputBuffer(info, 0) }
        }
    }

    private fun stop() = thread {
        stream?.close(); stream = null
        ctrl?.close(); ctrl = null
        audio?.close(); audio = null
        say("Stopped")
        runOnUiThread { surface.visibility = View.GONE; panel.visibility = View.VISIBLE; immersive(false) }
    }

    /** The stream fills the screen: system bars hidden (a swipe shows them for a moment). */
    private fun immersive(on: Boolean) {
        val c = window.insetsController ?: return
        if (on) {
            c.hide(android.view.WindowInsets.Type.systemBars())
            c.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else c.show(android.view.WindowInsets.Type.systemBars())
    }

    // Controller → console. The debug bench maps a standard Android gamepad onto the PlayStation layout.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val s = stream ?: return super.dispatchKeyEvent(event)
        val button = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> PsButton.CROSS; KeyEvent.KEYCODE_BUTTON_B -> PsButton.CIRCLE
            KeyEvent.KEYCODE_BUTTON_X -> PsButton.SQUARE; KeyEvent.KEYCODE_BUTTON_Y -> PsButton.TRIANGLE
            KeyEvent.KEYCODE_BUTTON_L1 -> PsButton.L1; KeyEvent.KEYCODE_BUTTON_R1 -> PsButton.R1
            KeyEvent.KEYCODE_BUTTON_THUMBL -> PsButton.L3; KeyEvent.KEYCODE_BUTTON_THUMBR -> PsButton.R3
            KeyEvent.KEYCODE_BUTTON_START -> PsButton.OPTIONS; KeyEvent.KEYCODE_BUTTON_SELECT -> PsButton.SHARE
            KeyEvent.KEYCODE_BUTTON_MODE -> PsButton.PS
            KeyEvent.KEYCODE_DPAD_UP -> PsButton.DPAD_UP; KeyEvent.KEYCODE_DPAD_DOWN -> PsButton.DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> PsButton.DPAD_LEFT; KeyEvent.KEYCODE_DPAD_RIGHT -> PsButton.DPAD_RIGHT
            else -> return super.dispatchKeyEvent(event)
        }
        if (event.repeatCount > 0) return true
        val down = event.action == KeyEvent.ACTION_DOWN
        pad = pad.copy(buttons = if (down) pad.buttons or button.mask else pad.buttons and button.mask.inv())
        s.updateController(pad)
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val s = stream ?: return super.onGenericMotionEvent(event)
        if (event.source and InputDevice.SOURCE_JOYSTICK == 0) return super.onGenericMotionEvent(event)
        fun axis(a: Int) = (event.getAxisValue(a) * 32767).toInt().coerceIn(-32768, 32767).toShort()
        fun trig(a: Int) = (event.getAxisValue(a) * 255).toInt().coerceIn(0, 255)
        pad = pad.copy(lx = axis(MotionEvent.AXIS_X), ly = axis(MotionEvent.AXIS_Y), rx = axis(MotionEvent.AXIS_Z), ry = axis(MotionEvent.AXIS_RZ),
            l2 = trig(MotionEvent.AXIS_LTRIGGER).coerceAtLeast(trig(MotionEvent.AXIS_BRAKE)), r2 = trig(MotionEvent.AXIS_RTRIGGER).coerceAtLeast(trig(MotionEvent.AXIS_GAS)))
        // The Odin's D-pad (and most gamepads) report it as the hat axes, not as key events
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var b = pad.buttons and (PsButton.DPAD_LEFT.mask or PsButton.DPAD_RIGHT.mask or PsButton.DPAD_UP.mask or PsButton.DPAD_DOWN.mask).inv()
        if (hx < -0.5f) b = b or PsButton.DPAD_LEFT.mask
        if (hx > 0.5f) b = b or PsButton.DPAD_RIGHT.mask
        if (hy < -0.5f) b = b or PsButton.DPAD_UP.mask
        if (hy > 0.5f) b = b or PsButton.DPAD_DOWN.mask
        pad = pad.copy(buttons = b)
        s.updateController(pad)
        return true
    }

    override fun onDestroy() { stop(); super.onDestroy() }

    companion object {
        /** The reference always uses 9295 for registration, session and ctrl, whatever host-request-port says (spec 5.9 (3)). */
        const val REQUEST_PORT = 9295
    }
}
