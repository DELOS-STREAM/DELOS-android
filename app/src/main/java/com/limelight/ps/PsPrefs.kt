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

    /** "few" | "normal" | "many", default "few": video frames that may wait for the decoder before the oldest are dropped. */
    const val VIDEO_QUEUE = "ps_video_queue"

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
                .putInt(BITRATE, PreferenceConfiguration.getDefaultBitrate("1920x1080", "60", "forceh265", false, false))
                .apply()
        }
        return prefs
    }

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
