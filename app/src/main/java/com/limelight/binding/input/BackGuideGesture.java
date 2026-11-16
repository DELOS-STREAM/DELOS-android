package com.limelight.binding.input;

/**
 * "Hold Back for Guide" (issue #90): with the setting on, Android's Back button of a gamepad sends Guide to the PC when it
 * is held for {@link #HOLD_MS}, and a short press still sends Back. Because a short press can only be told apart from a hold
 * when the button comes up, a short press reaches the PC as a tap on release. Pure state, no Android types: the controller
 * handler feeds it the button edges and the hold timer and does what it returns.
 */
public final class BackGuideGesture {
    /** How long Back must be held to become Guide. */
    public static final int HOLD_MS = 500;

    /** What the controller handler sends to the PC for an edge or the timer. */
    public enum Action {
        /** Nothing is sent now (the press is still undecided, or the edge belongs to no press). */
        NONE,
        /** Press and release Back at once: the button came up before the hold time. */
        TAP_BACK,
        /** Press Guide and keep it down: the hold time has passed with the button still down. */
        GUIDE_DOWN,
        /** Release Guide: the button that became Guide came up. */
        GUIDE_UP,
    }

    private boolean down;
    private boolean guide;

    /** Back went down (a repeat of a held button changes nothing). The caller starts the hold timer when this returns true. */
    public boolean onDown() {
        if (down) return false;
        down = true;
        guide = false;
        return true;
    }

    /** The hold timer of the current press fired. */
    public Action onHoldTimeout() {
        if (!down || guide) return Action.NONE;
        guide = true;
        return Action.GUIDE_DOWN;
    }

    /** Back came up. The caller cancels the hold timer. */
    public Action onUp() {
        if (!down) return Action.NONE;
        down = false;
        if (guide) {
            guide = false;
            return Action.GUIDE_UP;
        }
        return Action.TAP_BACK;
    }

    /** Forgets a press in progress (the controller went away or the stream stopped); returns whether Guide was down. */
    public boolean reset() {
        boolean wasGuide = guide;
        down = false;
        guide = false;
        return wasGuide;
    }

    public boolean isGuideDown() {
        return guide;
    }
}
