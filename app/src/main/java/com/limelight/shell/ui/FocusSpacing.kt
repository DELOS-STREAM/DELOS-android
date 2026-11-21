package com.limelight.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import kotlin.math.ceil
import kotlin.math.max

/**
 * The focus spacing rule (docs/GUI.md, "Focus spacing"): a focused element's ring, glow and lift never touch or overlap the
 * next focusable element. Two focusable siblings sit at least [FocusLook.minGap] apart: twice the [FocusLook.reach] of their
 * focus look plus [CLEAR] stage pixels of clear space. Every group of focusable siblings takes its gap from here (a [FocusGap]
 * or [FocusLook.minGap]), and FocusSpacingTest checks the gaps against the looks.
 */
object FocusSpacing {
    /** Clear space that always stays between a focused element's look and its neighbour, stage pixels. */
    const val CLEAR = 8f

    /** [focusEffects]' default look: 1.055, a 5 px band 5 px outside (the covers). */
    val TILE = FocusLook(scale = 1.055f, outset = 5f, band = 5f)

    /** A [PillButton]: 1.08, a 4 px band 4 px outside. */
    val PILL = FocusLook(scale = 1.08f, outset = 4f, band = 4f)

    /** The overview's shared ring: a 4 px band [RingStyle] pad outside the part's scaled bounds (screens/OverviewFocus.kt). */
    fun overview(scale: Float, pad: Float) = FocusLook(scale = scale, outset = pad, band = 4f, ringScales = false)
}

/**
 * How far a focused element's look reaches past its edge at rest: it grows to [scale] around its center and draws a ring
 * [band] wide, [outset] outside its edge (a negative outset: inside it). [ringScales]: the ring is drawn inside the element's
 * scaled layer and grows with it ([focusEffects]); otherwise it is drawn around the scaled bounds (a gliding ring or plate).
 * Sizes in stage pixels.
 */
@Immutable
data class FocusLook(val scale: Float, val outset: Float = 0f, val band: Float = 0f, val ringScales: Boolean = true) {
    /** The reach toward a neighbour of an element [size] long along that axis (an inset ring: negative). */
    fun reach(size: Float): Float {
        val ring = if (band > 0f) outset + band else 0f
        return (scale - 1f) * size / 2f + (if (ringScales) scale else 1f) * ring
    }

    /** The smallest gap between two such elements, the longer one [size] long along the axis. */
    fun minGap(size: Float): Float = 2f * reach(size) + FocusSpacing.CLEAR
}

/** A group of focusable siblings with a fixed [gap]: their [look] and the longest of them, [size], along the axis. */
@Immutable
data class FocusGap(val look: FocusLook, val size: Float, val gap: Float) {
    val minGap: Float get() = look.minGap(size)
}

/**
 * A row of focusable siblings whose width depends on their content (pills with a label): the gap between them is at least
 * [gap] and grows with the widest of them to [FocusLook.minGap], so a long label's focus growth never reaches its neighbour.
 * Children are centered vertically. [gap] in stage pixels.
 */
@Composable
fun FocusRow(look: FocusLook, gap: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val k = stageUnit()
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val widest = placeables.maxOfOrNull { it.width } ?: 0
        val space = ceil(max(gap, look.minGap(widest / k)) * k).toInt()
        val width = placeables.sumOf { it.width } + space * (placeables.size - 1).coerceAtLeast(0)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            var x = 0
            placeables.forEach {
                it.place(x, Alignment.CenterVertically.align(it.height, height))
                x += it.width + space
            }
        }
    }
}
