package com.limelight.ps

import android.content.Context
import android.content.SharedPreferences

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

    /** Int 0 | 1 | 5 | 15, default 0: minutes the stream stays connected after leaving DELOS. */
    const val BACKGROUND_MINUTES = "ps_background_minutes"

    fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

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

    fun backgroundMillis(prefs: SharedPreferences): Long = prefs.getInt(BACKGROUND_MINUTES, 0) * 60_000L
}
