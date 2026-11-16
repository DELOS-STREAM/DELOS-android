package com.limelight.update

import android.content.SharedPreferences

/**
 * The updater's device-wide state in the default shared preferences (not per profile: the setting is about this device and
 * its connection). Nothing here leaves the device.
 */
class UpdateStore(private val prefs: SharedPreferences) {
    /** "Check for updates" (Settings > About): on by default. Off means the updater makes no request at all. */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    /** When GitHub was last asked successfully (epoch milliseconds), 0 when never. */
    var lastCheck: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) { prefs.edit().putLong(KEY_LAST_CHECK, value).apply() }

    /** The ETag of the last answer, sent as `If-None-Match`; null without a [cached] answer to go with it. */
    val etag: String? get() = if (cached == null) null else prefs.getString(KEY_ETAG, null)

    val cached: CachedLatest? get() = CachedLatest.fromJson(prefs.getString(KEY_CACHED, null))

    fun saveAnswer(etag: String?, latest: CachedLatest, now: Long) {
        prefs.edit().putString(KEY_ETAG, etag).putString(KEY_CACHED, latest.toJson()).putLong(KEY_LAST_CHECK, now).apply()
    }

    /** The tag "Later" was pressed for, and until when it stays hidden. */
    val laterTag: String? get() = prefs.getString(KEY_LATER_TAG, null)
    val laterUntil: Long get() = prefs.getLong(KEY_LATER_UNTIL, 0L)

    fun later(tag: String, until: Long) {
        prefs.edit().putString(KEY_LATER_TAG, tag).putLong(KEY_LATER_UNTIL, until).apply()
    }

    /** The server the debug build asks instead of GitHub (docs/UPDATES.md "Testing"); ignored by a release build. */
    var debugServer: String?
        get() = prefs.getString(KEY_DEBUG_SERVER, null)?.takeIf { it.isNotBlank() }
        set(value) { prefs.edit().putString(KEY_DEBUG_SERVER, value?.trim()?.takeIf { it.isNotEmpty() }).apply() }

    companion object {
        const val KEY_ENABLED = "update_check_enabled"
        private const val KEY_LAST_CHECK = "update_last_check"
        private const val KEY_ETAG = "update_etag"
        private const val KEY_CACHED = "update_cached_latest"
        private const val KEY_LATER_TAG = "update_later_tag"
        private const val KEY_LATER_UNTIL = "update_later_until"
        private const val KEY_DEBUG_SERVER = "update_debug_server"
    }
}
