package com.limelight.shell.settings

import android.content.SharedPreferences

/**
 * The opacity of the on-screen controls (`seekbar_osc_opacity`, read into `PreferenceConfiguration.oscOpacity`). The Controls row of
 * Settings and the slider of the stream menu's More page read and write the same value of the current profile.
 */
object OscOpacity {
    const val KEY = "seekbar_osc_opacity"

    /** `PreferenceConfiguration.DEFAULT_OPACITY` and the XML default. */
    const val DEFAULT = 90
    const val MIN = 0
    const val MAX = 100
    const val STEP = 5

    fun read(prefs: SharedPreferences): Int = prefs.getInt(KEY, DEFAULT).coerceIn(MIN, MAX)

    /** Saves [percent] (kept in range) and returns the stored value. */
    fun write(prefs: SharedPreferences, percent: Int): Int = percent.coerceIn(MIN, MAX).also { prefs.edit().putInt(KEY, it).apply() }
}
