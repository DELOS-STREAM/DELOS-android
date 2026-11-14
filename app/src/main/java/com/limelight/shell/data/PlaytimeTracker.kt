package com.limelight.shell.data

/** Whose time it is: a profile (null: the unprofiled default settings), a PC and an app. Remote Desktop/Input/Monitor are apps like any other. */
data class PlaytimeKey(val profileId: String?, val pcUuid: String, val appId: Int)

/**
 * Accumulates the time one stream is actually connected. Pure logic: [clock] must be monotonic (milliseconds) and [sink] receives
 * every elapsed slice as it is flushed, so the stored total is always the sum of the slices written so far. There is no open
 * "session" record: a crash or kill loses at most the time since the last [flush], and a restart can neither lose nor double count anything.
 * Thread safe: stream callbacks arrive on native threads.
 */
class PlaytimeTracker(private val clock: () -> Long, private val sink: Sink) {
    /** Receives each elapsed slice. */
    fun interface Sink { fun add(key: PlaytimeKey, millis: Long) }

    private var key: PlaytimeKey? = null
    private var connected = false
    private var suspended = false
    private var mark = 0L

    /** Whether time is counting now. */
    @get:Synchronized val running: Boolean get() = key != null && connected && !suspended

    /** A stream of [key] exists; nothing counts until [setConnected]. A previous session is ended first. */
    @Synchronized fun begin(key: PlaytimeKey) {
        end()
        this.key = key
        connected = false
        suspended = false
    }

    /** The stream connected (true) or the connection is down (false). */
    @Synchronized fun setConnected(value: Boolean) = change { connected = value }

    /** The stream is suspended (true) or active again (false). */
    @Synchronized fun setSuspended(value: Boolean) = change { suspended = value }

    /** Writes the time since the last flush; call periodically (about every 30 s). */
    @Synchronized fun flush() {
        val current = key ?: return
        if (!running) return
        val now = clock()
        val delta = (now - mark).coerceAtLeast(0)
        mark = now
        if (delta > 0) sink.add(current, delta)
    }

    /** The stream ended (disconnect, quit, error): the rest is written and the session is gone. */
    @Synchronized fun end() {
        flush()
        key = null
        connected = false
        suspended = false
    }

    private inline fun change(block: () -> Unit) {
        flush()
        block()
        if (running) mark = clock()
    }
}
