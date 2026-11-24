package com.limelight.binding.input;

/**
 * Guide/Home and Back during a stream (issue #102): a tap opens the Delos stream menu and sends nothing to the PC; a hold of
 * {@link #HOLD_MS} sends Guide to the PC (Steam overlay, Game Bar), pressed as soon as the hold time passes, while the button
 * is still down, and released with the button. Pure state with the caller's clock, no Android types: the controller handler
 * feeds it the button edges and the hold timer (one instance per button) and does what it returns.
 * The optional Windows Back mode returns a short Guide pulse request on tap or Task View on hold; its caller gates
 * that mode to PC streams and owns pulse timing and keyboard delivery.
 */
public final class GuideTapHold {
    /** How long the button must be held to become Guide. */
    public static final int HOLD_MS = 500;

    /** How long Back must be held in the Windows Back mode to be Task View: shorter (owner, 2026-11-24), a tap stays well under it. */
    public static final int WINDOWS_HOLD_MS = 300;

    /** The hold time of the press in progress. */
    public int holdMs() {
        return windowsBack ? WINDOWS_HOLD_MS : HOLD_MS;
    }

    /** What the controller handler does for an edge or the timer. */
    public enum Action {
        /** Nothing now (the press is still undecided, or the edge belongs to no press). */
        NONE,
        /** Open the stream menu: the button came up before the hold time. Nothing reaches the PC. */
        TAP_MENU,
        /** Press Guide on the PC and keep it down: the hold time has passed with the button still down. */
        GUIDE_DOWN,
        /** Release Guide on the PC: the button that became Guide came up. */
        GUIDE_UP,
        /** A Windows Back tap requests an independently timed Guide pulse. */
        GUIDE_TAP,
        /** A Windows Back hold requests Win+Tab, never the stream menu. */
        TASK_VIEW,
    }

    private boolean down;
    private boolean guide;
    private boolean windowsBack;
    private boolean decided;
    private long downAt;

    /** The button went down at {@code nowMs} (a repeat of a held button changes nothing). The caller starts the hold timer when this returns true. */
    public boolean onDown(long nowMs) {
        return onDown(nowMs, false);
    }

    /** The optional Windows-only Back mapping is captured for the duration of this press. */
    public boolean onDown(long nowMs, boolean windowsBack) {
        if (down) return false;
        down = true;
        guide = false;
        this.windowsBack = windowsBack;
        decided = false;
        downAt = nowMs;
        return true;
    }

    /** The hold timer fired at {@code nowMs}; a timer that runs early, late after a release or twice does nothing. */
    public Action onHoldTimeout(long nowMs) {
        if (!down || decided || nowMs - downAt < holdMs()) return Action.NONE;
        decided = true;
        if (windowsBack) return Action.TASK_VIEW;
        guide = true;
        return Action.GUIDE_DOWN;
    }

    /** The button came up. The caller cancels the hold timer. */
    public Action onUp() {
        if (!down) return Action.NONE;
        down = false;
        if (guide) {
            guide = false;
            return Action.GUIDE_UP;
        }
        return decided ? Action.NONE : windowsBack ? Action.GUIDE_TAP : Action.TAP_MENU;
    }

    /** Forgets a press in progress (the controller went away or the stream stopped); returns whether Guide was down. */
    public boolean reset() {
        boolean wasGuide = guide;
        down = false;
        guide = false;
        decided = false;
        return wasGuide;
    }

    public boolean isGuideDown() {
        return guide;
    }
}
