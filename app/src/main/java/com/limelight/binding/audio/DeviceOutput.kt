package com.limelight.binding.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Where DELOS's sound goes while a DualSense is plugged in by USB (owner, 2026-11-25). Android routes all media to the controller's
 * USB audio interface then; DELOS keeps its sound on the device instead: headphones (wired, Bluetooth, another USB headset) first,
 * else the speaker. Without a DualSense it is null: the system's own routing applies.
 */
object DeviceOutput {
    fun offDualSense(context: Context): AudioDeviceInfo? {
        val outputs = context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return null
        if (outputs.none(DualSenseHaptics::isDualSense)) return null
        return outputs.filterNot(DualSenseHaptics::isDualSense).minByOrNull { rank(it) }?.takeIf { rank(it) < Int.MAX_VALUE }
    }

    private fun rank(d: AudioDeviceInfo): Int = when (d.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_USB_HEADSET -> 0
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 2
        else -> Int.MAX_VALUE
    }
}
