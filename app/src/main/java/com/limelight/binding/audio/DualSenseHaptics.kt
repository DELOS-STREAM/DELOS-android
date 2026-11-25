package com.limelight.binding.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The voice-coil haptics of a DualSense on USB (#125). Its USB audio interface has four output channels at 48 kHz: 1–2 are the
 * speaker and headset jack, 3–4 the left and right actuators. A PS5 sends haptics as signed 16-bit stereo PCM at 6 kHz (90 frames
 * every 15 ms, measured on the Odin 2025-11-25; PsStream, audio marker 0x02); [play] stretches each unit to 48 kHz and writes it to
 * channels 3–4, silence on 1–2.
 * [open] returns null when no DualSense audio output with four channels is there.
 */
class DualSenseHaptics private constructor(private val track: AudioTrack, private val audio: AudioManager?, private val restoreVolume: Int) {
    private var lastLeft = 0
    private var lastRight = 0
    /** Reused between units; channels 1–2 stay silent, they are never written. */
    private var buffer = ShortArray(0)
    private var units = 0
    private var firstUnitNs = 0L

    /** One unit from the console; never blocks (a full buffer drops the unit rather than delay the next). */
    fun play(pcm: ByteArray) {
        val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val pairs = pcm.size / 4
        if (pairs == 0) return
        // Once: how fast units come (spec Q6: 300 units of 360 bytes took 4.5 s on the Odin, so 6 kHz)
        if (units == 0) firstUnitNs = System.nanoTime()
        if (++units == 300) Log.i(TAG, "300 units of ${pcm.size} bytes in ${(System.nanoTime() - firstUnitNs) / 1_000_000} ms")
        if (buffer.size != pairs * UPSAMPLE * 4) buffer = ShortArray(pairs * UPSAMPLE * 4)
        val out = buffer
        var o = 0
        repeat(pairs) {
            val left = input.short.toInt()
            val right = input.short.toInt()
            // Linear steps from the last sample to this one, so the actuators get no 6 kHz staircase
            for (k in 1..UPSAMPLE) {
                out[o + 2] = (lastLeft + (left - lastLeft) * k / UPSAMPLE).toShort()
                out[o + 3] = (lastRight + (right - lastRight) * k / UPSAMPLE).toShort()
                o += 4
            }
            lastLeft = left
            lastRight = right
        }
        track.write(out, 0, out.size, AudioTrack.WRITE_NON_BLOCKING)
    }

    fun close() {
        runCatching { track.stop() }
        track.release()
        if (restoreVolume >= 0) runCatching { audio?.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVolume, 0) }
    }

    companion object {
        private const val TAG = "DualSenseHaptics"
        private const val RATE = 48_000
        /** 6 kHz from the console to the DualSense's 48 kHz. */
        private const val UPSAMPLE = 8

        /** The DualSense's USB audio output, if one is connected. */
        fun device(context: Context): AudioDeviceInfo? =
            context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull(::isDualSense)

        fun isDualSense(d: AudioDeviceInfo): Boolean =
            (d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_HEADSET) &&
                d.productName?.toString()?.contains("DualSense", ignoreCase = true) == true

        fun open(context: Context): DualSenseHaptics? {
            val device = device(context) ?: return null
            if (device.channelCounts.isNotEmpty() && device.channelCounts.none { it >= 4 }) {
                Log.i(TAG, "DualSense audio has no four channels: ${device.channelCounts.joinToString()}")
                return null
            }
            val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            return runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE)
                        .setChannelIndexMask(0b1111).build())
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setBufferSizeInBytes(RATE / 1000 * 40 * 4 * 2)
                    .build()
                track.preferredDevice = device
                // The actuators follow the media volume of the controller's audio output, which is often low (it was turned down
                // when Android put the sound there); with the controller as the media route it goes to full for the stream and back
                // after it. Never when media plays elsewhere (headphones): that volume is not the controller's
                val audio = context.getSystemService(AudioManager::class.java)
                var restore = -1
                val routes = if (audio != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
                    audio.getAudioDevicesForAttributes(attributes) else emptyList()
                if (audio != null && routes.any { it.type == device.type }) {
                    restore = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
                }
                Log.i(TAG, "Media route: ${routes.joinToString { "${it.type}:${it.address}" }}; the controller ${device.type}:${device.address}")
                track.play()
                Log.i(TAG, "DualSense haptics on ${device.productName} (${device.channelCounts.joinToString()} channels), routed to ${track.routedDevice?.productName}, volume ${if (restore >= 0) "raised from $restore" else "unchanged"}")
                DualSenseHaptics(track, audio, restore)
            }.onFailure { Log.w(TAG, "DualSense haptics unavailable", it) }.getOrNull()
        }
    }
}
