package com.limelight.shell.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
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

enum class Cue { MOVE, SELECT, BACK, OPEN, ERROR }

/**
 * UI sounds (res/raw: Open Sounds by Romain Raynal, see docs/CREDITS.md), the intro's sound track and
 * haptics. Created by the activity, released in onDestroy. While a DualSense is plugged in by USB, the sounds stay
 * on the device ([com.limelight.binding.audio.DeviceOutput]), not on the controller's speaker.
 */
class Feedback(context: Context) {
    private val appContext = context.applicationContext
    var soundsEnabled = true
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

    /** The output DELOS's own sounds go to (null: the system's routing), followed as devices come and go. */
    private var output: AudioDeviceInfo? = com.limelight.binding.audio.DeviceOutput.offDualSense(appContext)
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

    private fun reroute() {
        output = com.limelight.binding.audio.DeviceOutput.offDualSense(appContext)
        tracks.values.forEach { it.release() }
        tracks = buildTracks()
        intro?.preferredDevice = output
    }

    private var intro: MediaPlayer? = null

    /**
     * The intro's sound track (owner's res/raw/intro_fx.wav, 13.2 s). The intro is timed to it, so the sound is its clock
     * ([introPositionMs]): start-up stalls and the audio output's latency cannot pull the two apart. Not with the UI sounds
     * off. It plays to its end, also into the screen after the intro; leaving the app stops it. Returns whether it plays.
     */
    fun playIntro(): Boolean {
        stopIntro()
        if (!soundsEnabled) return false
        val session = appContext.getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: return false
        intro = MediaPlayer.create(appContext, R.raw.intro_fx, attributes, session)?.apply {
            setOnCompletionListener { if (intro === it) intro = null; it.release() }
            preferredDevice = output
            start()
        }
        return intro != null
    }

    /**
     * Where the intro's sound is now, in milliseconds of what has reached the speaker (0 until it is heard), or null once it
     * has ended or when it does not play. Between the player's timestamps it runs on the system clock, so it moves smoothly.
     */
    fun introPositionMs(): Float? {
        val player = intro ?: return null
        val stamp = player.timestamp ?: return 0f
        if (stamp.mediaClockRate <= 0f) return stamp.anchorMediaTimeUs / 1000f
        return (stamp.anchorMediaTimeUs + (System.nanoTime() - stamp.anchorSystemNanoTime) / 1000f * stamp.mediaClockRate) / 1000f
    }

    fun stopIntro() {
        intro?.release()
        intro = null
    }

    /** One UI sound decoded once: float PCM and its format. */
    private class Clip(val pcm: FloatArray, val format: AudioFormat)

    private val clips: Map<Cue, Clip> = mapOf(
        Cue.MOVE to R.raw.ui_move, Cue.SELECT to R.raw.ui_select, Cue.BACK to R.raw.ui_back, Cue.OPEN to R.raw.ui_open, Cue.ERROR to R.raw.ui_error,
    ).mapNotNull { (cue, res) -> decode(res)?.let { cue to it } }.toMap()

    /** Each cue a static track (SoundPool cannot be routed past the controller), restarted from its start on every play. */
    private var tracks: Map<Cue, AudioTrack> = buildTracks()

    private fun buildTracks(): Map<Cue, AudioTrack> = clips.mapNotNull { (cue, clip) ->
        runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(clip.format)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(clip.pcm.size * 4)
                .build()
                .also { it.preferredDevice = output; it.write(clip.pcm, 0, clip.pcm.size, AudioTrack.WRITE_BLOCKING) }
        }.getOrNull()?.let { cue to it }
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
        if (soundsEnabled) tracks[cue]?.let { t -> runCatching { t.stop(); t.reloadStaticData(); t.play() } }
        if (hapticsEnabled) {
            if (rumbleMotorOnly) pulse(cue) else view?.performHapticFeedback(hapticFor(cue))
        }
    }

    fun release() {
        stopIntro()
        main.removeCallbacks(rerouteLater)
        audioManager?.unregisterAudioDeviceCallback(devices)
        tracks.values.forEach { it.release() }
    }

    private fun hapticFor(cue: Cue): Int = when (cue) {
        Cue.MOVE, Cue.BACK -> HapticFeedbackConstants.CLOCK_TICK
        Cue.SELECT, Cue.OPEN ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Cue.ERROR ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
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
            Cue.MOVE, Cue.BACK -> VibrationEffect.createOneShot(50, full)
            Cue.SELECT, Cue.OPEN -> VibrationEffect.createOneShot(70, full)
            Cue.ERROR -> VibrationEffect.createWaveform(longArrayOf(0, 60, 80, 60), intArrayOf(0, full, 0, full), -1)
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
