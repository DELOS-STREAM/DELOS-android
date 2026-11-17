package com.limelight.shell.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * THE caret of every glass card that points at something: the context menu of the dashboard (covers, PC circles), the settings' option list and slider
 * popup, and the stream menu's popover, on every side they use. One spec, one drawing ([cardCaretPath]); Apple copies the numbers 1:1 (docs/gui/MENUS_AND_SETTINGS.md,
 * "The caret"). It is the average, in whole stage pixels, of the two carets the app had: the stream popover's (60 wide, a straight flank, a tip 20 wide
 * 18.7 out, 23 high) and the context menu's (60 wide, a rounded base, a tip 12 wide 13 out, 24 high).
 *
 * The caret is a curve in (u, v): u along the card's edge from the caret's centre, v out of the edge; the card's outline and the caret are ONE path.
 */
internal object CardCaret {
    /** Where the caret starts and ends on the edge: [WIDTH] wide at the base. */
    const val WIDTH = 60f

    /** How far the tip stands out of the edge. */
    const val DEPTH = 24f

    /** The curve's points, (u, v) for u > 0 (mirrored for u < 0): the base end, the two controls of the flank, where the flank meets the tip, and the tip's control. */
    const val BASE_U = 30f
    const val FLANK_C1_U = 21f
    const val FLANK_C1_V = 3f
    const val FLANK_C2_U = 14f
    const val FLANK_C2_V = 8f
    const val TIP_JOIN_U = 8f
    const val TIP_JOIN_V = 16f
    const val TIP_CONTROL_V = 32f

    /**
     * The caret's nine points in pixels, clockwise around the card (the card's body is [left], [top], [w] x [h]; [at] is the caret's centre along the
     * edge [side] from the body's top-left corner; [k] is pixels per stage pixel): the start on the edge, flank control 1 and 2, the tip's join,
     * the tip's control, the tip's other join, flank control 3 and 4 and the end on the edge. x, y pairs.
     */
    fun points(side: CaretSide, left: Float, top: Float, w: Float, h: Float, at: Float, k: Float): FloatArray {
        // Travelling clockwise, the top and right edges run in the direction of u, the bottom and left edges against it
        val s = if (side == CaretSide.Top || side == CaretSide.Right) 1f else -1f
        val u = floatArrayOf(-BASE_U * s, -FLANK_C1_U * s, -FLANK_C2_U * s, -TIP_JOIN_U * s, 0f, TIP_JOIN_U * s, FLANK_C2_U * s, FLANK_C1_U * s, BASE_U * s)
        val v = floatArrayOf(0f, FLANK_C1_V, FLANK_C2_V, TIP_JOIN_V, TIP_CONTROL_V, TIP_JOIN_V, FLANK_C2_V, FLANK_C1_V, 0f)
        val out = FloatArray(18)
        for (i in 0 until 9) {
            val du = u[i] * k
            val dv = v[i] * k
            when (side) {
                CaretSide.Top -> { out[2 * i] = left + at + du; out[2 * i + 1] = top - dv }
                CaretSide.Bottom -> { out[2 * i] = left + at + du; out[2 * i + 1] = top + h + dv }
                CaretSide.Left -> { out[2 * i] = left - dv; out[2 * i + 1] = top + at + du }
                CaretSide.Right -> { out[2 * i] = left + w + dv; out[2 * i + 1] = top + at + du }
                CaretSide.None -> error("no caret")
            }
        }
        return out
    }
}

private fun Path.caret(p: FloatArray) {
    lineTo(p[0], p[1])
    cubicTo(p[2], p[3], p[4], p[5], p[6], p[7])
    quadraticTo(p[8], p[9], p[10], p[11])
    cubicTo(p[12], p[13], p[14], p[15], p[16], p[17])
}

/** Just the caret's curve (for a lit edge), with the same points as [cardCaretPath]. */
internal fun caretCurvePath(side: CaretSide, left: Float, top: Float, w: Float, h: Float, at: Float, k: Float): Path {
    val p = CardCaret.points(side, left, top, w, h, at, k)
    return Path().apply {
        moveTo(p[0], p[1])
        cubicTo(p[2], p[3], p[4], p[5], p[6], p[7])
        quadraticTo(p[8], p[9], p[10], p[11])
        cubicTo(p[12], p[13], p[14], p[15], p[16], p[17])
    }
}

/**
 * The card's outline and its caret as ONE path (pixels): a rounded rectangle, the body [left], [top], [w] x [h] with corner [radius], and the caret on
 * [side] (none for [CaretSide.None]) centred [at] along that edge from the body's top-left corner.
 */
internal fun cardCaretPath(side: CaretSide, left: Float, top: Float, w: Float, h: Float, radius: Float, at: Float, k: Float): Path {
    val r = radius
    val p = if (side == CaretSide.None) null else CardCaret.points(side, left, top, w, h, at, k)
    return Path().apply {
        moveTo(left + r, top)
        if (side == CaretSide.Top) caret(p!!)
        lineTo(left + w - r, top)
        arcTo(Rect(left + w - 2 * r, top, left + w, top + 2 * r), -90f, 90f, false)
        if (side == CaretSide.Right) caret(p!!)
        lineTo(left + w, top + h - r)
        arcTo(Rect(left + w - 2 * r, top + h - 2 * r, left + w, top + h), 0f, 90f, false)
        if (side == CaretSide.Bottom) caret(p!!)
        lineTo(left + r, top + h)
        arcTo(Rect(left, top + h - 2 * r, left + 2 * r, top + h), 90f, 90f, false)
        if (side == CaretSide.Left) caret(p!!)
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)
        close()
    }
}
