package com.limelight.diag

import android.content.res.Resources
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.nvstream.jni.MoonBridge

/**
 * What a stream encrypts, as the connection negotiated it (S-01). Delos asks for everything to be encrypted on
 * devices with AES acceleration, but the PC's settings decide about video and audio, so the result is shown
 * (Debug info, the full statistics panel) and written to the diagnostic log rather than assumed.
 *
 * [video] and [audio] are null when this build's native library cannot report them. [control] covers the
 * control and input stream: hosts with app version 7.1.431 or newer (Sunshine and its forks) always encrypt it.
 */
data class StreamEncryption(val video: Boolean?, val audio: Boolean?, val control: Boolean) {
    /** The English line of the diagnostic log. */
    fun logLine(): String = "Stream encryption: video ${word(video)}, audio ${word(audio)}, control ${word(control)}"

    /** The localized readout: "Video: On · Audio: Off · Control: On". */
    fun text(res: Resources): String =
        res.getString(R.string.stream_encryption_value, word(res, video), word(res, audio), word(res, control))

    private fun word(value: Boolean?) = when (value) {
        true -> "on"
        false -> "off"
        null -> "unknown"
    }

    private fun word(res: Resources, value: Boolean?) = res.getString(
        when (value) {
            true -> R.string.common_on
            false -> R.string.common_off
            null -> R.string.stream_encryption_unknown
        }
    )

    companion object {
        /** `MoonBridge.getStreamEncryption()`: bit 0 video, bit 1 audio. */
        const val FLAG_VIDEO = 1
        const val FLAG_AUDIO = 2

        /** The last stream of this process, null before the first one. */
        @Volatile
        @JvmStatic
        var last: StreamEncryption? = null
            private set

        /** From the native flags ([flags] null: not reported) and the host's `appversion`. */
        @JvmStatic
        fun of(flags: Int?, serverAppVersion: String?) = StreamEncryption(
            video = flags?.let { it and FLAG_VIDEO != 0 },
            audio = flags?.let { it and FLAG_AUDIO != 0 },
            control = controlEncrypted(serverAppVersion),
        )

        /** moonlight-common-c encrypts the control stream for app version 7.1.431 and newer. */
        internal fun controlEncrypted(serverAppVersion: String?): Boolean {
            val parts = serverAppVersion?.split('.')?.map { it.toIntOrNull() ?: return false } ?: return false
            if (parts.size < 3) return false
            val (major, minor, patch) = parts
            return major > 7 || (major == 7 && (minor > 1 || (minor == 1 && patch >= 431)))
        }

        /** Reads what the connection that has just started negotiated and remembers it as [last]. */
        @JvmStatic
        fun read(serverAppVersion: String?): StreamEncryption {
            val flags = try {
                MoonBridge.getStreamEncryption()
            } catch (e: UnsatisfiedLinkError) {
                // A native library built without the getter: video and audio stay unknown
                LimeLog.warning("Stream encryption is not reported by this native library")
                null
            }
            return of(flags, serverAppVersion).also { last = it }
        }
    }
}
