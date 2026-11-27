package com.limelight.shell.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf
import com.limelight.R
import com.limelight.binding.audio.PcmPlayback
import com.limelight.binding.audio.VolumeController
import java.util.concurrent.Executors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.collect

enum class Cue { MOVE, SELECT, BACK, OPEN, ERROR, TOGGLE, STEP, TAB, UNDO, SCREENSHOT }

/**
 * UI sounds (res/raw: Open Sounds by Romain Raynal, see docs/CREDITS.md), the intro's sound track and
 * haptics. Created by the activity, released in onDestroy. While a DualSense is plugged in by USB, the sounds stay
 * on the device ([com.limelight.binding.audio.DeviceOutput]), not on the controller's speaker.
 */
class Feedback(context: Context) {
    private val appContext = context.applicationContext
    var soundsEnabled = true
        set(value) { field = value; updateHold() }
    var hapticsEnabled = true

    /** The view used for haptic feedback; set by the activity once the content view exists. */
    var view: View? = null

    // USAGE_GAME plays on the media stream, so the volume keys and a muted media volume apply. The earlier
    // USAGE_ASSISTANCE_SONIFICATION used the system stream, which ignores the media volume.
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val volume = VolumeController.get(appContext)
    private val audioWorkers = Executors.newFixedThreadPool(6)
    // Restarting a cue's track is three calls into the audio service: on the main thread they held up the frame of every focus
    // move (measured on the Odin in the stream menu: a 30 ms frame per move). One thread, so the cues keep their order.
    private val cuePlayer = Executors.newSingleThreadExecutor()

    /** The output DELOS's own sounds go to (null: the system's routing), followed as devices come and go. */
    private var output: AudioDeviceInfo? = volume.preferredDevice()
    private val main = Handler(Looper.getMainLooper())
    private val rerouteLater = Runnable { reroute() }
    // A track re-pointed after a device change kept playing on the controller (restored by the system on the old route), so the
    // tracks are built anew instead, once the audio policy has settled
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = scheduleReroute()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = scheduleReroute()
    }
    init { audioManager?.registerAudioDeviceCallback(devices, main) }

    private fun scheduleReroute() {
        main.removeCallbacks(rerouteLater)
        main.postDelayed(rerouteLater, REROUTE_DELAY_MS)
    }

    fun reroute() {
        val next = volume.preferredDevice()
        if (next?.id == output?.id) return
        output = next
        tracks.values.forEach { it.release() }
        releaseHold()
        updateHold()
        tracks = buildTracks()
        intro?.route(output)
    }

    private var intro: PcmPlayback? = null
    private val introClip by lazy { decode(R.raw.intro_fx) }

    /**
     * The intro's sound track (owner's res/raw/intro_fx.wav, 13.2 s). The intro is timed to it, so the sound is its clock
     * ([introPositionMs]): start-up stalls and the audio output's latency cannot pull the two apart. Not with the UI sounds
     * off. It plays to its end, also into the screen after the intro; leaving the app stops it. Returns whether it plays.
     */
    fun playIntro(): Boolean {
        stopIntro()
        if (!soundsEnabled) return false
        val clip = introClip ?: return false
        intro = PcmPlayback(clip.pcm, clip.format, attributes, volume, audioWorkers).apply {
            route(output)
            play()
        }
        return intro != null
    }

    /**
     * Where the intro's sound is now, in milliseconds of what has reached the speaker (0 until it is heard), or null once it
     * has ended or when it does not play. Between the player's timestamps it runs on the system clock, so it moves smoothly.
     */
    fun introPositionMs(): Float? {
        return intro?.positionMs()
    }

    fun stopIntro() {
        intro?.release()
        intro = null
    }

    /** One UI sound decoded once: float PCM and its format. */
    private class Clip(val pcm: FloatArray, val format: AudioFormat)

    private val clips: Map<Cue, Clip> = mapOf(
        Cue.MOVE to R.raw.ui_move, Cue.SELECT to R.raw.ui_select, Cue.BACK to R.raw.ui_back, Cue.OPEN to R.raw.ui_open, Cue.ERROR to R.raw.ui_error,
        Cue.TOGGLE to R.raw.ui_toggle, Cue.STEP to R.raw.ui_step,
        Cue.TAB to R.raw.ui_tab, Cue.UNDO to R.raw.ui_undo,
        Cue.SCREENSHOT to R.raw.ui_screenshot,
    ).mapNotNull { (cue, res) -> decode(res)?.let { cue to it } }.toMap()

    /**
     * Each cue a static track (restarted from its start on every play; a streaming track per cue dropped the short sounds on the
     * Odin), with the volume controller's gain: attenuation as the track volume, amplification as an amplified copy of the PCM,
     * written again only when the gain changes.
     */
    private inner class CueTrack(private val clip: Clip) {
        val track: android.media.AudioTrack = android.media.AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(clip.format)
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(clip.pcm.size * 4)
            .build()
            .also { it.preferredDevice = output; it.write(clip.pcm, 0, clip.pcm.size, android.media.AudioTrack.WRITE_BLOCKING) }
        private var loaded = 1f

        fun play(gain: Float) {
            runCatching {
                track.stop()
                val wanted = maxOf(1f, gain)
                if (wanted != loaded) {
                    val pcm = if (wanted > 1f) FloatArray(clip.pcm.size) { (clip.pcm[it] * wanted).coerceIn(-1f, 1f) } else clip.pcm
                    track.write(pcm, 0, pcm.size, android.media.AudioTrack.WRITE_BLOCKING)
                    loaded = wanted
                }
                track.setVolume(minOf(1f, gain))
                track.reloadStaticData()
                track.play()
            }
        }

        fun release() = track.release()
    }

    private var tracks: Map<Cue, CueTrack> = buildTracks()
    private val routeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    init { routeScope.launch { volume.state.map { it.preferredId }.distinctUntilChanged().collect { reroute() } } }

    private fun buildTracks(): Map<Cue, CueTrack> = clips.mapNotNull { (cue, clip) ->
        runCatching { CueTrack(clip) }.getOrNull()?.let { cue to it }
    }.toMap()

    /** A 16- or 24-bit PCM WAV in res/raw as float PCM. */
    private fun decode(res: Int): Clip? = runCatching {
        val bytes = appContext.resources.openRawResource(res).use { it.readBytes() }
        val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        var channels = 2; var rate = 48_000; var bits = 16
        var data: FloatArray? = null
        b.position(12)
        while (b.remaining() >= 8 && data == null) {
            val id = String(bytes, b.position(), 4, Charsets.US_ASCII); b.position(b.position() + 4)
            val size = b.int
            val start = b.position()
            when (id) {
                "fmt " -> { b.short; channels = b.short.toInt(); rate = b.int; b.int; b.short; bits = b.short.toInt() }
                "data" -> {
                    val step = bits / 8
                    data = FloatArray(size / step) { i ->
                        val o = start + i * step
                        if (step == 3) ((bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8) or (bytes[o + 2].toInt() shl 16)) / 8388608f
                        else b.getShort(o) / 32768f
                    }
                }
            }
            b.position(start + size + (size and 1))
        }
        val pcm = data ?: return@runCatching null
        Clip(pcm, AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
            .setChannelMask(if (channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO).build())
    }.getOrNull()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)

    /**
     * Proper haptic actuators (LRAs) report their resonant frequency and play the system's short,
     * crisp effects well. Plain rumble motors (e.g. the AYN Odin) report none: the system ticks are
     * too short to be felt on them, so they get longer, stronger pulses instead. The frequency can only
     * be read on Android 14+; older versions always get the pulses, which also work on LRAs.
     */
    private val rumbleMotorOnly: Boolean =
        vibrator?.hasVibrator() == true &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || vibrator.resonantFrequency.isNaN())

    private var focusSilenced = true

    /** The next focus change is not announced: the initial focus of a screen is silent, like in the prototype. */
    fun silenceNextFocus() {
        focusSilenced = true
    }

    /** Called whenever an element gains focus. */
    fun focusMoved() {
        if (focusSilenced) focusSilenced = false else play(Cue.MOVE)
    }

    fun play(cue: Cue) {
        if (soundsEnabled) tracks[cue]?.let { track ->
            val gain = volume.gain
            runCatching { cuePlayer.execute { track.play(gain) } } // refused after release()
        }
        if (hapticsEnabled && cue != Cue.UNDO) {
            if (rumbleMotorOnly) pulse(cue) else view?.performHapticFeedback(hapticFor(cue))
        }
    }

    /**
     * The device's output closes 2 s after the last sound (Android's standby) and the Odin's speaker then played the first cue
     * after a pause louder than the next ones (owner, 2026-11-25). While the shell is in the foreground a silent looping track
     * keeps the output open, so every cue sounds the same. Not with the UI sounds off.
     */
    fun holdOutput(on: Boolean) {
        holdWanted = on
        updateHold()
    }

    private var holdWanted = false
    private var hold: android.media.AudioTrack? = null

    private fun updateHold() {
        if (!(holdWanted && soundsEnabled)) { releaseHold(); return }
        if (hold != null) return
        hold = runCatching {
            val frames = 4_800
            android.media.AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(48_000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setTransferMode(android.media.AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 4)
                .build()
                .apply {
                    preferredDevice = output
                    write(ShortArray(frames * 2), 0, frames * 2)
                    setLoopPoints(0, frames, -1)
                    play()
                }
        }.getOrNull()
    }

    private fun releaseHold() {
        hold?.release()
        hold = null
    }

    fun release() {
        stopIntro()
        main.removeCallbacks(rerouteLater)
        audioManager?.unregisterAudioDeviceCallback(devices)
        tracks.values.forEach { it.release() }
        releaseHold()
        audioWorkers.shutdown()
        cuePlayer.shutdown()
        routeScope.cancel()
    }

    private fun hapticFor(cue: Cue): Int = when (cue) {
        Cue.MOVE, Cue.BACK, Cue.STEP, Cue.TAB -> HapticFeedbackConstants.CLOCK_TICK
        Cue.SELECT, Cue.OPEN, Cue.TOGGLE, Cue.SCREENSHOT ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Cue.ERROR ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Cue.UNDO -> HapticFeedbackConstants.VIRTUAL_KEY
    }

    /**
     * Rumble motor fallback: pulses long enough for the motor to spin up. Tested on the Odin:
     * 40 ms at 70 % cannot be felt, 60 ms at 86 % barely, 80 ms at full strength clearly;
     * 70 / 100 ms at full strength felt too strong, 60 / 80 ms still a bit too much.
     */
    private fun pulse(cue: Cue) {
        val v = vibrator ?: return
        val full = if (v.hasAmplitudeControl()) 255 else VibrationEffect.DEFAULT_AMPLITUDE
        val effect = when (cue) {
            Cue.MOVE, Cue.BACK, Cue.STEP, Cue.TAB -> VibrationEffect.createOneShot(50, full)
            Cue.SELECT, Cue.OPEN, Cue.TOGGLE, Cue.SCREENSHOT -> VibrationEffect.createOneShot(70, full)
            Cue.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), intArrayOf(0, full, 0, full), -1)
            Cue.UNDO -> return
        }
        // Touch usage: follows the system's touch feedback intensity, like the regular haptics
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build())
        }
    }

    private companion object {
        /** How long after a device comes or goes the sounds are rerouted: the audio policy switches its routes first. */
        const val REROUTE_DELAY_MS = 400L
    }
}

val LocalFeedback = staticCompositionLocalOf<Feedback> { error("Feedback not provided") }
