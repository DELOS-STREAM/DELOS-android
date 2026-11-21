package com.limelight.shell.ps

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.limelight.R
import com.limelight.ps.PsAudioPlayer
import com.limelight.ps.PsButton
import com.limelight.ps.PsCodec
import com.limelight.ps.PsControllerState
import com.limelight.ps.PsCtrl
import com.limelight.ps.PsDiscovery
import com.limelight.ps.PsStream
import com.limelight.ps.PsStreamListener
import com.limelight.ps.PsStreamRequest
import com.limelight.shell.ui.Figtree
import com.limelight.shell.ui.HyperspaceTunnel
import com.limelight.shell.ui.LocalDelosColors
import com.limelight.shell.ui.Stage
import com.limelight.shell.ui.Unbounded
import com.limelight.shell.ui.px
import com.limelight.shell.ui.tpx
import java.net.InetAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Streams a paired PlayStation (issue #25, first version on the branch): the console's picture on a SurfaceView, its sound, the
 * controller. While it connects, DELOS's hyperspace tunnel runs in PlayStation blue and opens onto the picture with its first frame.
 * The full stream path (DELOS's decoder, pacing, upscalers, stream menu) comes after this works end to end.
 */
class PsStreamActivity : Activity() {
    private lateinit var surface: SurfaceView
    private var ctrl: PsCtrl? = null
    private var stream: PsStream? = null
    private var audio: PsAudioPlayer? = null
    private var codec: MediaCodec? = null
    private val pending = LinkedBlockingQueue<Pair<ByteArray, Int>>()
    private val freeInputs = LinkedBlockingQueue<Int>()
    private var pad = PsControllerState()
    private val progress = mutableFloatStateOf(0.05f)
    private val status = mutableStateOf("")
    private val firstFrame = mutableStateOf(false)
    private var videoHeader: ByteArray? = null
    private var size = 1920 to 1080

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(AndroidColor.BLACK) }
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val loader = ComposeView(this).apply { setContent { Loader() } }
        root.addView(loader, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        surface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { videoHeader?.let { startDecoder(holder, it) } }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) { runCatching { codec?.release() }; codec = null }
        })
        val console = PsConsoleStore.get(this).consoles.value.firstOrNull { it.id == intent.getStringExtra(EXTRA_CONSOLE) }
        if (console == null) { finish(); return }
        status.value = getString(R.string.ps_connecting, console.name)
        thread(name = "ps-connect") { connect(console) }
    }

    @androidx.compose.runtime.Composable
    private fun Loader() {
        CompositionLocalProvider(LocalDelosColors provides playStationColors()) {
            Stage(textScale = 1f) {
                val opening = remember { Animatable(0f) }
                val clock = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(Unit) { val start = withFrameNanos { it }; while (true) withFrameNanos { clock.floatValue = (it - start) / 1e9f } }
                LaunchedEffect(firstFrame.value) { if (firstFrame.value) opening.animateTo(1f, tween(900)) }
                if (opening.value >= 1f) return@Stage
                Box(Modifier.fillMaxSize()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HyperspaceTunnel({ progress.floatValue }, { opening.value }, { clock.floatValue }, Modifier.fillMaxSize())
                    }
                    val colors = LocalDelosColors.current
                    Column(Modifier.align(Alignment.BottomStart).padding(start = 96.px, bottom = 110.px).graphicsLayer { alpha = 1f - opening.value }) {
                        BasicText("PLAYSTATION REMOTE PLAY", style = TextStyle(color = colors.accent2, fontFamily = Figtree, fontWeight = FontWeight.Bold, fontSize = 20.tpx))
                        BasicText(status.value, style = TextStyle(color = colors.text, fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 44.tpx))
                    }
                }
            }
        }
    }

    private fun connect(console: PsConsole) {
        try {
            val address = InetAddress.getByName(console.address)
            status.value = getString(R.string.ps_connect_waking)
            runCatching { PsDiscovery.wake(address, console.console.target.ps5, console.console.registKey) }
            progress.floatValue = .25f
            status.value = getString(R.string.ps_connect_session)
            // A console in rest mode takes a few seconds before the session starts; retry while it wakes
            var c: PsCtrl? = null
            var lastError: Exception? = null
            for (attempt in 0 until 15) {
                try { c = PsCtrl(address, PS_PORT, console.console, PsCodec.HEVC).also { it.open() }; break } catch (e: Exception) { lastError = e; Thread.sleep(2000) }
            }
            val ctrl = c ?: throw lastError ?: IllegalStateException("no session")
            this.ctrl = ctrl
            progress.floatValue = .5f
            var sessionId: String? = null
            val ready = Object()
            thread(name = "ps-ctrl") {
                runCatching { ctrl.readLoop { type, payload -> if (type == PsCtrl.SESSION_ID) { sessionId = PsCtrl.sessionId(payload); synchronized(ready) { ready.notifyAll() } } } }
            }
            synchronized(ready) { if (sessionId == null) ready.wait(10_000) }
            val id = sessionId ?: throw IllegalStateException("no session id")
            ctrl.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); ctrl.send(PsCtrl.MIC_TOGGLE, byteArrayOf(0, 1, 1, 0x59)); ctrl.send(PsCtrl.DISPLAY_DEVICES, ByteArray(4))
            status.value = getString(R.string.ps_connect_video)
            val s = PsStream(address, console.console.target, id, ctrl.crypt, PsStreamRequest(), listener)
            stream = s
            s.start()
            progress.floatValue = .8f
        } catch (e: Exception) {
            Log.w(TAG, "connect", e)
            runOnUiThread {
                android.widget.Toast.makeText(this, getString(R.string.ps_connect_failed, e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private val listener = object : PsStreamListener {
        override fun onStreamInfo(width: Int, height: Int, videoHeader: ByteArray, audioHeader: ByteArray?) {
            size = width to height
            this@PsStreamActivity.videoHeader = videoHeader
            audioHeader?.let { h -> runCatching { audio = PsAudioPlayer(h) }.onFailure { Log.w(TAG, "audio: ${it.message}") } }
            runOnUiThread { if (surface.holder.surface.isValid) startDecoder(surface.holder, videoHeader) }
        }
        override fun onVideoFrame(frame: ByteArray, index: Int) { pending.add(frame to 0); pump() }
        override fun onAudio(opus: ByteArray, frameIndex: Int) { audio?.play(opus, frameIndex) }
        override fun onEnded(reason: String) { runOnUiThread { finish() } }
    }

    private fun startDecoder(holder: SurfaceHolder, header: ByteArray) {
        if (codec != null) return
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, size.first, size.second)
        format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        c.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) { freeInputs.add(index); pump() }
            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                codec.releaseOutputBuffer(index, true)
                if (!firstFrame.value) { progress.floatValue = 1f; firstFrame.value = true }
            }
            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) { Log.w(TAG, "decoder: ${e.diagnosticInfo}") }
            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
        })
        c.configure(format, holder.surface, null, 0)
        codec = c
        pending.add(header to MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
        c.start()
    }

    @Synchronized
    private fun pump() {
        val c = codec ?: return
        while (pending.isNotEmpty() && freeInputs.isNotEmpty()) {
            val (data, flags) = pending.poll() ?: return
            val i = freeInputs.poll() ?: return
            runCatching { c.getInputBuffer(i)!!.apply { clear(); put(data) }; c.queueInputBuffer(i, 0, data.size, System.nanoTime() / 1000, flags) }
        }
    }

    // Controller → console: a standard Android gamepad on the PlayStation layout; the D-pad comes as the hat axes on most pads
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val s = stream ?: return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
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
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var b = pad.buttons and (PsButton.DPAD_LEFT.mask or PsButton.DPAD_RIGHT.mask or PsButton.DPAD_UP.mask or PsButton.DPAD_DOWN.mask).inv()
        if (hx < -0.5f) b = b or PsButton.DPAD_LEFT.mask
        if (hx > 0.5f) b = b or PsButton.DPAD_RIGHT.mask
        if (hy < -0.5f) b = b or PsButton.DPAD_UP.mask
        if (hy > 0.5f) b = b or PsButton.DPAD_DOWN.mask
        pad = pad.copy(
            buttons = b,
            lx = axis(MotionEvent.AXIS_X), ly = axis(MotionEvent.AXIS_Y), rx = axis(MotionEvent.AXIS_Z), ry = axis(MotionEvent.AXIS_RZ),
            l2 = trig(MotionEvent.AXIS_LTRIGGER).coerceAtLeast(trig(MotionEvent.AXIS_BRAKE)), r2 = trig(MotionEvent.AXIS_RTRIGGER).coerceAtLeast(trig(MotionEvent.AXIS_GAS)),
        )
        s.updateController(pad)
        return true
    }

    override fun onDestroy() {
        thread { stream?.close(); ctrl?.close() }
        audio?.close()
        runCatching { codec?.release() }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONSOLE = "console"
        private const val TAG = "PsStreamActivity"
    }
}
