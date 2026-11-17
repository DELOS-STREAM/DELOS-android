package com.limelight.shell

import kotlin.math.abs

/** The part of the window height (top inclusive, bottom exclusive, pixels) where the open-the-menu swipe may start. */
internal data class SwipeZone(val top: Float, val bottom: Float)

/**
 * The swipe up that opens the stream menu. It starts in a band above the bottom edge, not at the edge itself: the strip
 * the system reserves there for its gesture bar (home/recents) stays out of it.
 */
internal object StreamMenuSwipe {
    /** The band's share of the window height, and how far up from the start the swipe has to go. */
    const val BAND = .18f
    const val DISTANCE = .12f

    /** A flat minimum for the dead strip: immersive apps often report no gesture inset even with gesture navigation. */
    const val MIN_DEAD_EDGE_DP = 24

    fun zone(height: Int, gestureInset: Int, minDeadEdge: Int): SwipeZone {
        val h = height.toFloat()
        val bottom = (h - maxOf(gestureInset, minDeadEdge)).coerceAtLeast(0f)
        return SwipeZone((h * (1f - BAND)).coerceAtMost(bottom), bottom)
    }

    fun starts(zone: SwipeZone, y: Float): Boolean = y >= zone.top && y < zone.bottom

    /** Up by [DISTANCE] of the height from the start, and more up than sideways. */
    fun opens(startX: Float, startY: Float, x: Float, y: Float, height: Int): Boolean {
        val up = startY - y
        return up >= height * DISTANCE && up > abs(x - startX)
    }
}
