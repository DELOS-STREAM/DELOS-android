package com.limelight.nvstream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Leaving a stream while it is still being set up (C-03): what start and stop do in every order. */
public class ConnectionGateTest {
    private final ConnectionGate gate = new ConnectionGate();

    @Test
    public void aStartedConnectionIsStoppedExactlyOnce() {
        assertTrue(gate.mayStart());
        gate.onStartResult(true);

        gate.requestStop();
        assertTrue(gate.takeStarted());
        // A second stop (onStop after a terminated connection) has nothing to stop and no permit to give back
        assertFalse(gate.takeStarted());
    }

    @Test
    public void aStopBeforeTheNativeStartKeepsItFromStarting() {
        gate.requestStop();
        // The stop itself finds nothing to stop and releases no permit...
        assertFalse(gate.takeStarted());
        // ...and the start thread, arriving later with the permit, gives it back instead of starting
        assertTrue(gate.isStopRequested());
        assertFalse(gate.mayStart());
    }

    @Test
    public void aStopAfterAFailedStartReleasesNothing() {
        assertTrue(gate.mayStart());
        gate.onStartResult(false);

        gate.requestStop();
        assertFalse(gate.takeStarted());
    }

    @Test
    public void aStopThatArrivesWhileTheNativeStartRunsStopsItAfterwards() {
        assertTrue(gate.mayStart());
        // requestStop() is not ordered by the bridge lock: it lands while the native start is running
        gate.requestStop();
        gate.onStartResult(true);
        assertTrue(gate.takeStarted());
    }

    @Test
    public void nothingIsStoppedBeforeAnythingStarted() {
        assertFalse(gate.isStopRequested());
        assertFalse(gate.takeStarted());
    }
}
