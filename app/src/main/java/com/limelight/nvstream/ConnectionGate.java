package com.limelight.nvstream;

/**
 * Makes stopping one connection safe at any moment of its start. The start runs on its own thread
 * (HTTP launch, then the native connection, which takes the single connection permit); the stop
 * comes from the UI whenever the stream is left, also in the middle of that. Without this, a stop
 * during the start either did nothing (the native session went on with no activity) or released a
 * permit that was never taken.
 *
 * {@link #requestStop()} may be called from any thread. The other methods are called while holding
 * the bridge lock ({@code synchronized (MoonBridge.class)}), which orders them against each other.
 */
final class ConnectionGate {
    private volatile boolean stopRequested;
    private boolean started;

    /** From now on nothing more of this connection is started. */
    void requestStop() {
        stopRequested = true;
    }

    boolean isStopRequested() {
        return stopRequested;
    }

    /**
     * The start thread holds the permit and is about to start the native connection.
     * False: a stop came first, so it must give the permit back and end.
     */
    boolean mayStart() {
        return !stopRequested;
    }

    /** The native start returned; a failed one has already been cleaned up and its permit given back. */
    void onStartResult(boolean ok) {
        started = ok;
    }

    /**
     * True exactly once, for a connection whose native start succeeded: the caller then stops the
     * native connection and gives the permit back. False when there is nothing to stop (never
     * started, failed to start, or already stopped).
     */
    boolean takeStarted() {
        boolean wasStarted = started;
        started = false;
        return wasStarted;
    }
}
