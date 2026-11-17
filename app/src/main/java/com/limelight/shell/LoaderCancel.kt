package com.limelight.shell

import android.view.KeyEvent

/** Where the stream loader is in its life; [CANCELLED] and [GONE] are terminal. */
enum class LoaderPhase { CONNECTING, STREAMING, CANCELLED, GONE }

/**
 * What cancelling the loader does. It only ends this client's attempt: [quitGame] is always false (the game keeps
 * running on the PC, whether it was already running or started for this attempt), and no result overlay is shown.
 */
data class CancelPlan(
    val stopConnection: Boolean,
    val quitGame: Boolean = false,
    val showResult: Boolean = false,
    val returnToLibrary: Boolean = true,
)

/** The phases of the stream loader and the one-shot cancel request that is valid in the first two of them. */
internal class LoaderCancel {
    var phase = LoaderPhase.CONNECTING
        private set

    /** The loader can still be cancelled: it is on screen and nobody has cancelled it yet. */
    val cancellable: Boolean get() = phase == LoaderPhase.CONNECTING || phase == LoaderPhase.STREAMING

    /** The connection is established; the opening animation follows. */
    fun streaming() {
        if (phase == LoaderPhase.CONNECTING) phase = LoaderPhase.STREAMING
    }

    /** The loader view is removed. A cancelled loader stays cancelled. */
    fun gone() {
        if (phase != LoaderPhase.CANCELLED) phase = LoaderPhase.GONE
    }

    /** True exactly once while [cancellable]; false afterwards and once the loader is gone. */
    fun request(): Boolean {
        if (!cancellable) return false
        phase = LoaderPhase.CANCELLED
        return true
    }

    companion object {
        /** [connecting]/[connected] are the activity's flags: before the connection starts (or in the hand-off gap) there is nothing to stop. */
        fun planFor(phase: LoaderPhase, connecting: Boolean, connected: Boolean) =
            CancelPlan(stopConnection = phase != LoaderPhase.GONE && (connecting || connected))
    }
}

/** What the loader does with a key. */
enum class LoaderKey { NONE, CONSUME, CANCEL }

/** The key policy of a cancellable loader, callable from Java. */
object LoaderKeys {
    /**
     * Back, the gamepad's B and Escape cancel on release; both edges are consumed so the game never sees them, and a
     * cancelled release (the key was taken away) consumes without cancelling.
     */
    @JvmStatic
    internal fun policy(keyCode: Int, action: Int, canceled: Boolean): LoaderKey = when (keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_ESCAPE ->
            if (action == KeyEvent.ACTION_UP && !canceled) LoaderKey.CANCEL else LoaderKey.CONSUME
        else -> LoaderKey.NONE
    }

    @JvmStatic
    fun decide(event: KeyEvent): LoaderKey = policy(event.keyCode, event.action, event.isCanceled)
}
