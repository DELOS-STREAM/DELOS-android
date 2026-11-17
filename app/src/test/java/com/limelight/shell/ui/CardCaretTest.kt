package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one caret of every glass card (dashboard context menu, settings popover, stream popover): its numbers and its geometry on every side. */
class CardCaretTest {
    // A card body at (100, 200), 400 x 300, the caret centred 150 along the edge, 1 px per stage px
    private val left = 100f
    private val top = 200f
    private val w = 400f
    private val h = 300f
    private val at = 150f

    private fun pts(side: CaretSide, k: Float = 1f) = CardCaret.points(side, left, top, w, h, at, k)

    @Test fun theSpecIsWholePixelsBetweenTheTwoOldCarets() {
        assertEquals(60f, CardCaret.WIDTH, 0f)
        assertEquals(24f, CardCaret.DEPTH, 0f)
        assertEquals(30f, CardCaret.BASE_U, 0f)
        assertEquals(8f, CardCaret.TIP_JOIN_U, 0f)
        assertEquals(16f, CardCaret.TIP_JOIN_V, 0f)
        // The tip's curve (a quadratic from the join through its control) peaks at the depth
        assertEquals(CardCaret.DEPTH, (CardCaret.TIP_JOIN_V + CardCaret.TIP_CONTROL_V) / 2f, 0f)
        listOf(CardCaret.BASE_U, CardCaret.FLANK_C1_U, CardCaret.FLANK_C1_V, CardCaret.FLANK_C2_U, CardCaret.FLANK_C2_V, CardCaret.TIP_CONTROL_V).forEach {
            assertEquals(it, Math.round(it).toFloat(), 0f)
        }
    }

    @Test fun theBottomCaretHangsUnderTheCardRunningRightToLeft() {
        val p = pts(CaretSide.Bottom)
        // Start and end on the bottom edge (y = 500), 30 either side of x = 250; clockwise: the right one first
        assertEquals(280f, p[0], 0f); assertEquals(500f, p[1], 0f)
        assertEquals(220f, p[16], 0f); assertEquals(500f, p[17], 0f)
        // The tip's control is 32 below the edge, its joins 16
        assertEquals(250f, p[8], 0f); assertEquals(532f, p[9], 0f)
        assertEquals(258f, p[6], 0f); assertEquals(516f, p[7], 0f)
        assertEquals(242f, p[10], 0f); assertEquals(516f, p[11], 0f)
    }

    @Test fun theTopCaretStandsOnTheCardRunningLeftToRight() {
        val p = pts(CaretSide.Top)
        assertEquals(220f, p[0], 0f); assertEquals(200f, p[1], 0f)
        assertEquals(280f, p[16], 0f); assertEquals(200f, p[17], 0f)
        assertEquals(250f, p[8], 0f); assertEquals(168f, p[9], 0f)
    }

    @Test fun theLeftCaretPointsLeftRunningBottomToTop() {
        val p = pts(CaretSide.Left)
        // Along the left edge (x = 100) at y = 350 +- 30; clockwise: the lower one first
        assertEquals(100f, p[0], 0f); assertEquals(380f, p[1], 0f)
        assertEquals(100f, p[16], 0f); assertEquals(320f, p[17], 0f)
        assertEquals(68f, p[8], 0f); assertEquals(350f, p[9], 0f)
    }

    @Test fun theRightCaretPointsRightRunningTopToBottom() {
        val p = pts(CaretSide.Right)
        assertEquals(500f, p[0], 0f); assertEquals(320f, p[1], 0f)
        assertEquals(500f, p[16], 0f); assertEquals(380f, p[17], 0f)
        assertEquals(532f, p[8], 0f); assertEquals(350f, p[9], 0f)
    }

    @Test fun everySideIsSymmetricAboutTheCaretsCentreAndScalesWithThePixels() {
        for (side in listOf(CaretSide.Top, CaretSide.Bottom, CaretSide.Left, CaretSide.Right)) {
            val p = pts(side)
            val horizontal = side == CaretSide.Top || side == CaretSide.Bottom
            val along = if (horizontal) 0 else 1
            val out = 1 - along
            val centre = if (horizontal) left + at else top + at
            // Point i and point 8 - i are mirrored about the centre and equally far out of the edge
            for (i in 0..3) {
                assertEquals(side.toString(), 2 * centre, p[2 * i + along] + p[2 * (8 - i) + along], .001f)
                assertEquals(side.toString(), p[2 * i + out], p[2 * (8 - i) + out], .001f)
            }
        }
        // At 2 px per stage px the tip's control stands 64 out of the bottom edge
        assertEquals(top + h + 64f, CardCaret.points(CaretSide.Bottom, left, top, w, h, at, 2f)[9], 0f)
    }
}
