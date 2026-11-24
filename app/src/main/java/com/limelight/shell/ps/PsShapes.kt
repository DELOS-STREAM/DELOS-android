package com.limelight.shell.ps

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * The four decorative PlayStation shapes (owner-approved A2 set, issue #128; `tools/fonts/ps-alt/round3/a2` and its NOTES):
 * a broken equilateral triangle with an upward arrow, four corner brackets with outward arrows, a ring of four arcs with
 * inward chevrons, and an X with arrowheads on all four ends. Drawn as centre lines (round caps and joins) in a unit box: the
 * 256-unit source grid around (128, 128) divided by 96, y down. The launch tunnel's shader draws the same geometry
 * (HyperspaceTunnel.kt).
 */
internal object PsShapes {
    const val COUNT = 4

    /** The centre lines of shape [kind] (0 triangle, 1 circle, 2 cross, 3 square), to be stroked. */
    fun path(kind: Int): Path = paths[kind]

    private val paths = Array(COUNT) { build(it) }

    private const val S = 1f / 96f
    private fun u(v: Float) = v * S

    private fun build(kind: Int): Path = Path().apply {
        when (kind) {
            0 -> { // triangle: apex arms and centre shaft, two lower corner pieces
                val arm = 44f / 1.7320508f
                moveTo(u(-arm), u(92f - 128f)); lineTo(0f, u(48f - 128f)); lineTo(u(arm), u(92f - 128f))
                moveTo(0f, u(48f - 128f)); lineTo(0f, u(208f - 128f))
                val base = 160f / 1.7320508f
                val sideY = 127.4f
                val sideX = (sideY - 48f) * .57735f
                for (sx in intArrayOf(-1, 1)) {
                    moveTo(u(sx * sideX), u(sideY - 128f)); lineTo(u(sx * base), u(80f)); lineTo(u(sx * 24f), u(80f))
                }
            }
            1 -> { // circle: four arcs (15°..75° in each quarter) and four inward chevrons
                val r = 80f
                val oval = Rect(u(-r), u(-r), u(r), u(r))
                for (q in 0 until 4) arcTo(oval, 15f + q * 90f, 60f, forceMoveTo = true)
                for ((dx, dy) in arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    moveTo(u(56f * dx - 10f * -dy), u(56f * dy - 10f * dx))
                    lineTo(u(46f * dx), u(46f * dy))
                    lineTo(u(56f * dx + 10f * -dy), u(56f * dy + 10f * dx))
                }
            }
            2 -> { // cross: four rays with outward heads
                val h = 16f * .70710677f
                for (dx in intArrayOf(-1, 1)) for (dy in intArrayOf(-1, 1)) {
                    val px = 72f * dx; val py = 72f * dy
                    moveTo(0f, 0f); lineTo(u(px), u(py))
                    moveTo(u(px - 2f * h * dx), u(py)); lineTo(u(px), u(py)); lineTo(u(px), u(py - 2f * h * dy))
                }
            }
            else -> { // square: four corner brackets, each with a shaft out to its corner
                for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                    moveTo(u(80f * sx), u(16f * sy)); lineTo(u(80f * sx), u(80f * sy)); lineTo(u(16f * sx), u(80f * sy))
                    moveTo(u(52f * sx), u(52f * sy)); lineTo(u(80f * sx), u(80f * sy))
                }
            }
        }
    }
}
