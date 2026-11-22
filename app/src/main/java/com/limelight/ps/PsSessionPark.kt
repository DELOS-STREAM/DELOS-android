package com.limelight.ps

import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.thread

/** What the stream activity needs to set up its decoder and audio again for a session it did not open itself. */
internal class PsStreamInfo(val width: Int, val height: Int, val videoHeader: ByteArray, val audioHeader: ByteArray?)

/**
 * A PlayStation session kept alive while the stream activity is in the background (#121): the Takion stream keeps its heartbeats,
 * acks and controller state, the ctrl connection answers its heartbeats, and pictures and sound are dropped. Starting the same
 * console again within [KEEP_MS] takes the session back without waking the console or a new session; after that, or when a
 * session for another console starts, the parked one is closed. One session at most.
 */
internal object PsSessionPark {
    private class Parked(val consoleId: String, val stream: PsStream, val ctrl: PsCtrl?, val request: PsStreamRequest, val info: PsStreamInfo,
                         val at: Long)

    private var parked: Parked? = null

    /** Keeps [stream] and [ctrl] for [consoleId]; a session parked before is closed. */
    @Synchronized
    fun park(consoleId: String, stream: PsStream, ctrl: PsCtrl?, request: PsStreamRequest, info: PsStreamInfo) {
        closeParked()
        stream.listener = object : PsStreamListener {
            override fun onEnded(reason: String) { Log.i(TAG, "parked session ended: $reason"); drop(stream) }
        }
        val p = Parked(consoleId, stream, ctrl, request, info, SystemClock.uptimeMillis())
        parked = p
        Log.i(TAG, "session parked")
        thread(name = "ps-park", isDaemon = true) {
            Thread.sleep(KEEP_MS)
            synchronized(this) { if (parked === p) { Log.i(TAG, "parked session closed after ${KEEP_MS / 60_000} min"); closeParked() } }
        }
    }

    /** The parked session of [consoleId], taken out of the park; null when there is none (any other parked session is closed). */
    @Synchronized
    fun take(consoleId: String): Triple<PsStream, PsCtrl?, Pair<PsStreamRequest, PsStreamInfo>>? {
        val p = parked ?: return null
        parked = null
        if (p.consoleId != consoleId) { close(p); return null }
        Log.i(TAG, "parked session taken back after ${(SystemClock.uptimeMillis() - p.at) / 1000} s")
        return Triple(p.stream, p.ctrl, p.request to p.info)
    }

    @Synchronized
    private fun drop(stream: PsStream) { if (parked?.stream === stream) closeParked() }

    /** Closes the parked session, if any (another stream starts). */
    @Synchronized
    fun closeAny() { if (parked != null) { Log.i(TAG, "parked session closed: another stream starts"); closeParked() } }

    private fun closeParked() { parked?.let(::close); parked = null }

    private fun close(p: Parked) {
        runCatching { p.stream.close() }
        runCatching { p.ctrl?.close() }
    }

    private const val TAG = "PsSessionPark"
    private const val KEEP_MS = 5 * 60_000L
}
