package com.limelight.shell.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Streaming time per profile, PC and app, in the profile's own preferences file like the per-game settings
 * (key `ui_playtime_<uuid of the PC uuid>_<appId>`, a Long in milliseconds). It stays on the device, is never uploaded and has no reset;
 * deleting a profile removes its times with its file. [prefsFor] returns a profile's preferences (null: the unprofiled default).
 */
class PlaytimeStore(private val prefsFor: (String?) -> SharedPreferences) {
    constructor(context: Context) : this({ id -> ProfileStore.settingsOf(context, id) })

    fun totalMillis(profileId: String?, pcUuid: String, appId: Int): Long = prefsFor(profileId).getLong(keyFor(pcUuid, appId), 0L)

    /** Adds [millis] to one app's total and tells the observers. */
    fun add(key: PlaytimeKey, millis: Long) {
        if (millis <= 0) return
        val prefs = prefsFor(key.profileId)
        val name = keyFor(key.pcUuid, key.appId)
        prefs.edit().putLong(name, prefs.getLong(name, 0L) + millis).apply()
        revision.value++
    }

    /** The total of one app, emitted again whenever any time is added. */
    fun totalFlow(profileId: String?, pcUuid: String, appId: Int): Flow<Long> =
        revision.map { totalMillis(profileId, pcUuid, appId) }.distinctUntilChanged()

    private companion object {
        val revision = MutableStateFlow(0)
        fun keyFor(pcUuid: String, appId: Int) =
            "ui_playtime_${UUID.nameUUIDFromBytes(pcUuid.toByteArray(Charsets.UTF_8))}_$appId"
    }
}
