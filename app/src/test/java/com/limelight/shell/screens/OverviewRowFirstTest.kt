package com.limelight.shell.screens

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

/** The overview's Up and Down by the row-first rule (docs/GUI.md): the first element on screen of the row the move reaches. */
class OverviewRowFirstTest {
    private fun r(x: Float, y: Float, w: Float, h: Float) = Rect(x, y, x + w, y + h)

    /** OverviewNavTest's page with the screenshots scrolled: shot0 starts left of the screen. */
    private val page = mapOf(
        "play" to NavTarget(r(562f, 484f, 185f, 76f), "buttons", locked = true),
        "settings" to NavTarget(r(767f, 484f, 277f, 76f), "buttons", locked = true),
        "summary" to NavTarget(r(150f, 990f, 1004f, 129f)),
        "story" to NavTarget(r(150f, 1145f, 215f, 52f)),
        "facts" to NavTarget(r(1210f, 1000f, 560f, 312f)),
        "shot0" to NavTarget(r(-400f, 1450f, 520f, 292f), "track", locked = true),
        "shot1" to NavTarget(r(146f, 1450f, 520f, 292f), "track", locked = true),
        "shot2" to NavTarget(r(692f, 1450f, 520f, 292f), "track", locked = true),
        "shot3" to NavTarget(r(1238f, 1450f, 520f, 292f), "track", locked = true),
        "details" to NavTarget(r(150f, 1880f, 1620f, 480f)),
    )

    @Test fun `down from the facts card lands on the first screenshot on screen, not the one below it`() {
        assertEquals("shot1", OverviewNavigator<String>().move("facts", NavDirection.DOWN, page))
    }

    @Test fun `up from the last screenshot lands on the first element of the row above`() {
        // The row of the facts card: the summary and the story button both start at 150; the story lies nearer the card's middle
        assertEquals("story", OverviewNavigator<String>().move("shot3", NavDirection.UP, page))
    }

    @Test fun `up from the facts card lands on Play, the first of the buttons`() {
        assertEquals("play", OverviewNavigator<String>().move("facts", NavDirection.UP, page))
    }

    @Test fun `back into the row one left lands on the element one left`() {
        val nav = OverviewNavigator<String>()
        assertEquals("story", nav.move("shot3", NavDirection.UP, page))
        assertEquals("facts", nav.move("story", NavDirection.RIGHT, page))
        assertEquals("shot3", nav.move("facts", NavDirection.DOWN, page))
    }
}
