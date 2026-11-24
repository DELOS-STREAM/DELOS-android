package com.limelight.shell.settings

import android.content.Context

/** Completion belongs to the device, not to whichever profile is currently selected. */
class TutorialStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("delos_tutorial", Context.MODE_PRIVATE)

    val seen: Boolean get() = prefs.getBoolean("tutorial_seen", false)

    fun markSeen() {
        prefs.edit().putBoolean("tutorial_seen", true).apply()
    }
}
