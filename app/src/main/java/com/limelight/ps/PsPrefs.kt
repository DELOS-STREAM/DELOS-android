package com.limelight.ps

import android.content.Context
import android.content.SharedPreferences
import com.limelight.preferences.PreferenceConfiguration

/**
 * The PlayStation stream settings, separate from every PC profile: their own preferences file. The classic stream keys
 * (`list_resolution`, `frame_pacing`, `checkbox_sgsr`, …) live here too and the stream reads them with
 * `PreferenceConfiguration.readPreferences(context, PsPrefs.prefs(context))`; the keys below exist only for PlayStation streams.
 */
object PsPrefs {
    const val FILE = "ps_settings"

    /** Boolean, default false: ask the console for a keyframe whenever a frame is lost, not only when one cannot be rebuilt. */
    const val KEYFRAME_ON_LOSS = "ps_keyframe_on_loss"

    /** "low" | "normal" | "high", default "low": the audio jitter buffer (frames buffered before playback starts, and at most). */
    const val AUDIO_BUFFER = "ps_audio_buffer"

    /** Boolean, default false: left and right audio channels swapped. */
    const val SWAP_CHANNELS = "ps_swap_channels"

    /** Boolean, default false: the device microphone goes to the console's voice chat (needs the record-audio permission). */
    const val MICROPHONE = "ps_microphone"

    /** "few" | "normal" | "many", default "few": video frames that may wait for the decoder before the oldest are dropped. */
    const val VIDEO_QUEUE = "ps_video_queue"

    /** Boolean, default true: the console sets the bitrate itself from the loss DELOS reports, up to [autoCeilingKbps]. */
    const val AUTO_BITRATE = "ps_auto_bitrate"

    /** Boolean, default true: automatic bitrate reacts only to loss FEC could not repair (the loss the picture shows). */
    const val VISIBLE_LOSS = "ps_visible_loss"

    /** Boolean, default false: a fixed bitrate may go past [MAX_BITRATE_KBPS], up to [UNLOCKED_MAX_BITRATE_KBPS]. */
    const val UNLOCK_BITRATE = "ps_unlock_bitrate"

    /**
     * The range of a fixed bitrate. Remote Play takes the bitrate as a ceiling; a PS5 at 1080p60 is reported to send about 30–50 Mbit/s
     * at most (protocol spec, 9 [UNVERIFIED]), so more is not guaranteed to change the picture: it is offered only unlocked.
     */
    const val MIN_BITRATE_KBPS = 2_000
    const val MAX_BITRATE_KBPS = 50_000
    const val UNLOCKED_MAX_BITRATE_KBPS = 200_000

    fun autoBitrate(prefs: SharedPreferences): Boolean = prefs.getBoolean(AUTO_BITRATE, true)

    /** The most a fixed bitrate may be: [MAX_BITRATE_KBPS], or [UNLOCKED_MAX_BITRATE_KBPS] when unlocked. */
    fun maxBitrateKbps(prefs: SharedPreferences): Int =
        if (prefs.getBoolean(UNLOCK_BITRATE, false)) UNLOCKED_MAX_BITRATE_KBPS else MAX_BITRATE_KBPS

    /** The ceiling of the automatic bitrate for a stream [height] lines high: the most that size uses. */
    fun autoCeilingKbps(height: Int): Int = when {
        height <= 360 -> 4_000
        height <= 540 -> 8_000
        height <= 720 -> 20_000
        else -> MAX_BITRATE_KBPS
    }

    /**
     * The bitrate up to which a stream [height] lines high is sure to get what it asks for: the low end of what a PS5 is reported to
     * send at 1080p (30 Mbit/s, protocol spec, 9 [UNVERIFIED]), scaled down for smaller sizes, above the reference presets.
     */
    fun idealBitrateKbps(height: Int): Int = when {
        height <= 360 -> 3_000
        height <= 540 -> 6_000
        height <= 720 -> 12_000
        else -> 30_000
    }

    private const val RESOLUTION = "list_resolution"
    private const val FPS = "list_fps"
    private const val FORMAT = "video_format"
    private const val BITRATE = "seekbar_bitrate_kbps"

    /**
     * The settings file, with the PlayStation defaults written the first time: 1080p, 60 fps, HEVC and the bitrate DELOS
     * recommends for them (the classic keys' own defaults are a PC's).
     */
    fun prefs(context: Context): SharedPreferences {
        val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!prefs.contains(RESOLUTION)) {
            prefs.edit()
                .putString(RESOLUTION, "1920x1080")
                .putString(FPS, "60")
                .putString(FORMAT, "forceh265")
                .putInt(BITRATE, PreferenceConfiguration.getDefaultBitrate("1920x1080", "60", "forceh265", false, false)
                    .coerceIn(MIN_BITRATE_KBPS, MAX_BITRATE_KBPS))
                .apply()
        }
        // A handheld's built-in controls may announce themselves as an external pad without a vibrator (the Odin in Xbox mode):
        // the device vibrates for them, so a console's rumble and haptics are felt (owner, 2026-11-23). Written once, a setting.
        if (!prefs.contains(VIBRATE_FALLBACK)) prefs.edit().putBoolean(VIBRATE_FALLBACK, true).apply()
        return prefs
    }

    private const val VIBRATE_FALLBACK = "checkbox_vibrate_fallback"

    /** Audio frames (10 ms each) buffered before playback starts, and the most kept before the oldest are dropped. */
    fun audioBuffer(prefs: SharedPreferences): Pair<Int, Int> = when (prefs.getString(AUDIO_BUFFER, "low")) {
        "normal" -> 3 to 8
        "high" -> 6 to 12
        else -> 2 to 6
    }

    /** Frames waiting for the decoder at most. */
    fun videoQueue(prefs: SharedPreferences): Int = when (prefs.getString(VIDEO_QUEUE, "few")) {
        "normal" -> 4
        "many" -> 8
        else -> 2
    }
}
