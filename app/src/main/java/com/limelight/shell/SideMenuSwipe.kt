package com.limelight.shell

import kotlin.math.abs

/** A rectangle in window pixels (left/top inclusive, right/bottom exclusive). */
internal data class EdgeBand(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The touch swipe of the side menu (docs/gui/MENUS_AND_SETTINGS.md, "Side menu by touch"): a drag in from the left edge opens
 * it, a drag to the left on the open menu closes it. The panel follows the finger as a progress, 0 = closed and 1 = fully open;
 * on release the distance and the speed decide. Pure arithmetic in pixels, so it is tested without a screen.
 */
internal object SideMenuSwipe {
    /** How far from the left edge an opening drag may start, dp. */
    const val EDGE_DP = 40

    /** Android grants an app at most this much height of gesture exclusion per edge, dp; the band is centred on the edge. */
    const val MAX_EXCLUSION_DP = 200

    /** The share of the panel width a drag has to cover for the menu to open (or, from open, to close). */
    const val THRESHOLD = .4f

    /** A release at least this fast (dp per second) goes the way it moves, whatever the distance. */
    const val FLING_DP_PER_S = 600f

    /** How far past fully open the panel can be pulled, as a share of its width; the pull is damped towards it. */
    const val RUBBER = .06f

    fun startsAtEdge(x: Float, edge: Float): Boolean = x >= 0f && x < edge

    /**
     * The part of the left edge kept from the system's Back gesture: [edge] wide and at most [maxHeight] high, centred
     * vertically on a window [height] high.
     */
    fun exclusion(height: Float, edge: Float, maxHeight: Float): EdgeBand {
        val h = minOf(height, maxHeight)
        val top = (height - h) / 2f
        return EdgeBand(0f, top, edge, top + h)
    }

    /**
     * Whether a drag of ([dx], [dy]) from its start belongs to the menu: past the touch [slop], more sideways than up or down,
     * and to the right for [opening] (to the left for closing). Null while it is still undecided, false once it is clearly
     * something else (a scroll, a drag the other way).
     */
    fun claims(opening: Boolean, dx: Float, dy: Float, slop: Float): Boolean? {
        val towards = if (opening) dx else -dx
        return when {
            abs(dy) > slop && abs(dy) >= abs(dx) -> false
            towards > slop && towards > abs(dy) -> true
            -towards > slop -> false
            else -> null
        }
    }

    /**
     * The progress for a drag that moved [travel] pixels to the right since it began at [start] (0 opening, 1 closing) on a panel
     * [width] wide: never below 0, and damped past 1 so it never passes 1 + [RUBBER].
     */
    fun progress(start: Float, travel: Float, width: Float): Float {
        if (width <= 0f) return start
        val raw = start + travel / width
        if (raw <= 1f) return raw.coerceAtLeast(0f)
        val over = raw - 1f
        return 1f + RUBBER * over / (over + RUBBER)
    }

    /**
     * Whether the menu ends open after a release at [progress] with a horizontal [velocity] (pixels per second, to the right
     * positive). A release as fast as [fling] follows its direction; otherwise an opening drag opens past [THRESHOLD] of the
     * width and a closing drag closes past the same share.
     */
    fun settlesOpen(opening: Boolean, progress: Float, velocity: Float, fling: Float): Boolean = when {
        velocity >= fling -> true
        velocity <= -fling -> false
        opening -> progress > THRESHOLD
        else -> 1f - progress <= THRESHOLD
    }

    /** Reduced motion: the panel does not follow; the drag only toggles the menu once it has covered [THRESHOLD] of the width. */
    fun reducedToggles(opening: Boolean, travel: Float, width: Float): Boolean =
        (if (opening) travel else -travel) >= width * THRESHOLD
}
