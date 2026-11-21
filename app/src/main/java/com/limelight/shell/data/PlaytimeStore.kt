package com.limelight.shell.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What is recorded of one app: the summed connected time and when time was last added (both zero: never streamed). */
data class Playtime(val totalMillis: Long, val lastStreamedMillis: Long) {
    companion object { val NONE = Playtime(0L, 0L) }
}

/**
 * Streaming time per profile, PC and app, in the profile's own preferences file like the per-game settings:
 * `ui_playtime_<uuid of the PC uuid>_<appId>` is the total (a Long in milliseconds) and `ui_playtime_last_<uuid of the PC uuid>_<appId>` the
 * wall-clock time (epoch milliseconds, from [wallClock]) of the last addition. Both stay on the device, are never uploaded and have
 * no reset; deleting a profile removes its times with its file. [prefsFor] returns a profile's preferences (null: the unprofiled default).
 */
class PlaytimeStore(
    private val prefsFor: (String?) -> SharedPreferences,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this({ id -> ProfileStore.settingsOf(context, id) })

    fun playtime(profileId: String?, pcUuid: String, appId: Int): Playtime {
        val prefs = prefsFor(profileId)
        return Playtime(prefs.getLong(totalKey(pcUuid, appId), 0L), prefs.getLong(lastKey(pcUuid, appId), 0L))
    }

    /** A profile's whole streaming time (the side menu's summary): every app's total on every PC added up, and the latest of their last-streamed times. */
    fun total(profileId: String?): Playtime {
        var total = 0L
        var last = 0L
        for ((key, value) in prefsFor(profileId).all) {
            if (value !is Long) continue
            when {
                key.startsWith(LAST_PREFIX) -> last = maxOf(last, value)
                key.startsWith(TOTAL_PREFIX) -> total += value.coerceAtLeast(0L)
            }
        }
        return Playtime(total, last)
    }

    /** Adds [millis] to one app's total, stamps its last-streamed time and tells the observers. */
    fun add(key: PlaytimeKey, millis: Long) {
        if (millis <= 0) return
        val prefs = prefsFor(key.profileId)
        val total = totalKey(key.pcUuid, key.appId)
        prefs.edit()
            .putLong(total, prefs.getLong(total, 0L) + millis)
            .putLong(lastKey(key.pcUuid, key.appId), wallClock())
            .apply()
        revision.value++
    }

    /** Counts every addition (any app): a reader that shows [playtime] reads it again when this changes. */
    val changes: StateFlow<Int> get() = revision

    internal companion object {
        private val revision = MutableStateFlow(0)
        private fun uuidOf(pcUuid: String) = UUID.nameUUIDFromBytes(pcUuid.toByteArray(Charsets.UTF_8))
        // A total's key never starts with the last-streamed prefix: the PC part is a UUID, never "last"
        private const val TOTAL_PREFIX = "ui_playtime_"
        private const val LAST_PREFIX = "ui_playtime_last_"
        fun totalKey(pcUuid: String, appId: Int) = "$TOTAL_PREFIX${uuidOf(pcUuid)}_$appId"
        fun lastKey(pcUuid: String, appId: Int) = "$LAST_PREFIX${uuidOf(pcUuid)}_$appId"
    }
}
