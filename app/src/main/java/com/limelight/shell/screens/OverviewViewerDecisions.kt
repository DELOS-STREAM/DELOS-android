package com.limelight.shell.screens

import kotlin.math.abs

/** Pure decisions shared by pointer browsing, button browsing and overlay auto-hide. */
internal object OverviewViewerDecisions {
    fun canBrowse(index: Int, shown: Int, count: Int, closing: Boolean): Boolean =
        !closing && index in 0 until count && index != shown

    fun neighbor(shown: Int, x: Float): Int = shown + if (x < 0f) 1 else -1

    fun follow(shown: Int, count: Int, x: Float): Float =
        if (neighbor(shown, x) in 0 until count) x else x * .25f

    fun slideTarget(shown: Int, count: Int, x: Float, unit: Float, closing: Boolean): Int? =
        neighbor(shown, x).takeIf { abs(x) > 90f * unit && canBrowse(it, shown, count, closing) }

    /** A slide completion must not revive a closing viewer or overwrite a newer browse. */
    fun canCommit(startedAt: Int, shown: Int, closing: Boolean): Boolean = !closing && startedAt == shown

    fun swapInPlace(slidIn: Boolean, closing: Boolean): Boolean = slidIn && !closing

    fun closesDrag(x: Float, y: Float): Boolean = y > 120f && abs(y) > abs(x)

    fun canHide(held: Boolean): Boolean = !held
}
