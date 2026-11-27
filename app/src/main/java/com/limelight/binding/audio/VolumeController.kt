package com.limelight.binding.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/** A process-wide volume scale shared by the shell, stream and all audible DELOS tracks. */
class VolumeController private constructor(context: Context) {
    data class State(val level: Int, val route: VolumeRoute, val gain: Float, val previousLevel: Float,
                     val press: Long = 0, val preferredId: Int? = null)

    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val routeAttributes = android.media.AudioAttributes.Builder()
        .setUsage(android.media.AudioAttributes.USAGE_GAME).build()
    private val prefs = app.getSharedPreferences("delos_volume", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private var keepOnDevice = true
    private var forced = false
    private var reference = -1
    private var muteUnlocked = false
    private var routeId = -1
    private var routeType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    private var controllerRoute = false
    private var lastIndex = -1
    private var lastAudible = VolumeLevels.UNKNOWN_LEVEL
    private val mutable = MutableStateFlow(State(VolumeLevels.UNKNOWN_LEVEL, VolumeRoute.SPEAKER, 1f, 9f))
    val state = mutable.asStateFlow()
    @Volatile var gain: Float = 1f
        private set

    private val refreshLater = Runnable { refresh() }
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = reroute()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = reroute()
    }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh() }
    }

    init {
        refresh()
        audio.registerAudioDeviceCallback(devices, main)
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(changes, filter, Context.RECEIVER_EXPORTED)
        else app.registerReceiver(changes, filter)
    }

    /** The foreground window supplies its PC/profile or PlayStation device-audio preference. */
    fun keepSoundOnDevice(enabled: Boolean) {
        if (keepOnDevice == enabled) return
        keepOnDevice = enabled
        refresh()
    }

    private fun reroute() {
        main.removeCallbacks(refreshLater)
        main.postDelayed(refreshLater, 400)
    }

    /** Preferred physical output while bypassing the controller, or the system route otherwise. */
    fun preferredDevice(): AudioDeviceInfo? = if (keepOnDevice) DeviceOutput.offDualSense(app) else null

    private fun systemOutput(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT >= 33) {
            audio.getAudioDevicesForAttributes(routeAttributes).firstOrNull()?.let { return it }
        }
        val outputs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        if (!keepOnDevice) outputs.firstOrNull(DualSenseHaptics::isDualSense)?.let { return it }
        return outputs.filterNot(DualSenseHaptics::isDualSense).minByOrNull {
            when (it.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> 0
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 2
                else -> 3
            }
        }
    }

    private fun glyph(type: Int): VolumeRoute = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_BROADCAST -> VolumeRoute.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> VolumeRoute.HEADPHONES
        else -> VolumeRoute.SPEAKER
    }

    /**
     * A wired DualSense's voice coils follow the media volume Android keeps for it (USB headset), often low (2 of 15 on the
     * Odin): its haptics, PC (Vibepollo waveform) and PlayStation, were then too weak to feel (owner, 2026-11-27: always at
     * the maximum). Android sets that volume only while media is routed to the controller, which it is when it is plugged in;
     * the index is kept for later. DELOS's own sound stays on the device ([DeviceOutput]), so this never makes it louder.
     */
    private fun dualSenseVolumeToMax() {
        if (Build.VERSION.SDK_INT < 33) return
        val route = audio.getAudioDevicesForAttributes(routeAttributes).firstOrNull() ?: return
        if (!DualSenseHaptics.isDualSense(route)) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < max) {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            android.util.Log.i("VolumeController", "DualSense media volume set to the maximum for its haptics")
        }
    }

    private fun refresh() {
        val offController = preferredDevice()
        val output = offController ?: systemOutput()
        val type = output?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        controllerRoute = output != null && DualSenseHaptics.isDualSense(output)
        val id = output?.id ?: 0
        // USB haptics share Android's media stream: never touch its index while the controller is present, except below.
        val nextForced = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any(DualSenseHaptics::isDualSense)
        if (nextForced) dualSenseVolumeToMax()
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val changed = forced != nextForced || id != routeId
        val old = mutable.value
        var level = old.level
        if (nextForced) {
            if (changed) {
                reference = if (offController != null) prefs.getInt("index_$type", -1)
                    else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                val savedMax = prefs.getInt("max_$type", max)
                if (offController != null && reference >= 0 && savedMax != max && savedMax > 0)
                    reference = (reference * max.toFloat() / savedMax).roundToInt()
                muteUnlocked = false
                level = if (reference < 0) VolumeLevels.UNKNOWN_LEVEL else VolumeLevels.fromIndex(reference, max)
            }
            if (offController == null) {
                val activeIndex = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (activeIndex != reference && activeIndex == 0) muteUnlocked = false
                reference = activeIndex
            }
        } else {
            val index = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (changed || index != lastIndex) level = VolumeLevels.fromIndex(index, max)
            lastIndex = index
            if (!controllerRoute && (prefs.getInt("index_$type", -1) != index || prefs.getInt("max_$type", -1) != max))
                prefs.edit().putInt("index_$type", index).putInt("max_$type", max).apply()
        }
        forced = nextForced
        routeId = id
        routeType = type
        publish(level, old.previousLevel, old.press)
    }

    private fun publish(level: Int, previous: Float, press: Long) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        gain = if (!forced) 1f else VolumeLevels.gain(level, reference, max, muteUnlocked) {
            audio.getStreamVolumeDb(AudioManager.STREAM_MUSIC, it, routeType).toDouble()
        }
        mutable.value = State(level, glyph(routeType), gain, previous, press,
            if (keepOnDevice && forced && !controllerRoute) routeId else null)
    }

    /** Both edges are consumed. Repeat-down events are real volume steps; no system panel is requested. */
    fun key(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code != KeyEvent.KEYCODE_VOLUME_UP && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE) return false
        if (event.action != KeyEvent.ACTION_DOWN || event.isCanceled) return true
        if (code == KeyEvent.KEYCODE_VOLUME_MUTE && event.repeatCount > 0) return true
        refresh()
        val old = mutable.value
        val level = when (code) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeLevels.clamp(old.level + 1)
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeLevels.clamp(old.level - 1)
            else -> if (old.level == 0) lastAudible else 0
        }
        if (old.level > 0) lastAudible = old.level
        if (forced) {
            if (code == KeyEvent.KEYCODE_VOLUME_UP) muteUnlocked = true
            if (reference < 0 && level != old.level) {
                // The owner chooses a neutral unknown baseline at 9/15; never persist this assumption as a real reading.
                reference = VolumeLevels.toIndex(VolumeLevels.UNKNOWN_LEVEL, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            }
        } else {
            lastIndex = VolumeLevels.toIndex(level, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, lastIndex, 0)
            if (!controllerRoute) prefs.edit().putInt("index_$routeType", lastIndex)
                .putInt("max_$routeType", audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).apply()
        }
        publish(level, old.level.toFloat(), old.press + 1)
        return true
    }

    companion object {
        @Volatile private var instance: VolumeController? = null
        @JvmStatic fun get(context: Context): VolumeController = instance ?: synchronized(this) {
            instance ?: VolumeController(context).also { instance = it }
        }
    }
}
