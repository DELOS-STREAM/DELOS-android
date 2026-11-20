package com.limelight.shell.screens

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The overview's spatial focus, its scroll targets and its motion (docs/gui/GAME_INFO.md, "Game overview"). */
class OverviewNavTest {
    private fun r(x: Float, y: Float, w: Float, h: Float) = Rect(x, y, x + w, y + h)

    /** Variant A at 1920 × 1080, scrolled to the About section: the prototype's measured rectangles. */
    private val page = mapOf(
        "play" to NavTarget(r(562f, 484f, 185f, 76f), "buttons", locked = true),
        "settings" to NavTarget(r(767f, 484f, 277f, 76f), "buttons", locked = true),
        "summary" to NavTarget(r(150f, 990f, 1004f, 129f)),
        "story" to NavTarget(r(150f, 1145f, 215f, 52f)),
        "facts" to NavTarget(r(1210f, 1000f, 560f, 312f)),
        "shot0" to NavTarget(r(150f, 1450f, 520f, 292f), "track", locked = true),
        "shot1" to NavTarget(r(696f, 1450f, 520f, 292f), "track", locked = true),
        "shot2" to NavTarget(r(1242f, 1450f, 520f, 292f), "track", locked = true),
        "details" to NavTarget(r(150f, 1880f, 1620f, 480f)),
    )

    @Test fun `down from Play reaches the summary, down again the story button, then the first screenshot`() {
        val nav = OverviewNavigator<String>()
        assertEquals("summary", nav.move("play", NavDirection.DOWN, page))
        assertEquals("story", nav.move("summary", NavDirection.DOWN, page))
        assertEquals("shot0", nav.move("story", NavDirection.DOWN, page))
        assertEquals("details", nav.move("shot0", NavDirection.DOWN, page))
    }

    @Test fun `up retraces the way down`() {
        val nav = OverviewNavigator<String>()
        nav.move("play", NavDirection.DOWN, page)
        assertEquals("settings", nav.move("play", NavDirection.RIGHT, page))
        // From App settings Down finds the summary; Up from it returns to App settings, not to Play
        assertEquals("summary", nav.move("settings", NavDirection.DOWN, page))
        assertEquals("settings", nav.move("summary", NavDirection.UP, page))
    }

    @Test fun `up from the header has nothing above it`() {
        assertNull(OverviewNavigator<String>().move("play", NavDirection.UP, page))
        assertNull(OverviewNavigator<String>().move("settings", NavDirection.UP, page))
    }

    @Test fun `left and right walk a row and never leave a locked one`() {
        val nav = OverviewNavigator<String>()
        assertEquals("shot1", nav.move("shot0", NavDirection.RIGHT, page))
        assertEquals("shot2", nav.move("shot1", NavDirection.RIGHT, page))
        assertNull(nav.move("shot2", NavDirection.RIGHT, page))
        assertNull(nav.move("settings", NavDirection.RIGHT, page))
        assertNull(nav.move("play", NavDirection.LEFT, page))
    }

    @Test fun `right from the summary reaches the facts card, left goes back`() {
        val nav = OverviewNavigator<String>()
        assertEquals("facts", nav.move("summary", NavDirection.RIGHT, page))
        assertEquals("summary", nav.move("facts", NavDirection.LEFT, page))
        assertEquals("facts", OverviewNavigator<String>().move("story", NavDirection.RIGHT, page))
    }

    @Test fun `the score weighs the sideways distance 2·2 times`() {
        val a = r(0f, 0f, 100f, 100f)
        val below = r(0f, 200f, 100f, 50f)
        val belowAside = r(300f, 150f, 100f, 50f)
        assertEquals(100f, OverviewNavigator.score(a, below, NavDirection.DOWN)!!, .01f)
        // 50 down, 200 aside × 2.2, 300 × .08 between the centres
        assertEquals(50f + 440f + 24f, OverviewNavigator.score(a, belowAside, NavDirection.DOWN)!!, .01f)
        assertNull(OverviewNavigator.score(a, r(0f, -100f, 100f, 50f), NavDirection.DOWN))
    }

    @Test fun `the header scrolls to the top, a short section to its anchor, a tall one just enough`() {
        assertEquals(0f, OverviewScroll.page(800f, header = true, 484f, 560f, 0f, 920f, 1080f, 3000f))
        assertEquals(990f - 132f, OverviewScroll.page(0f, header = false, 1040f, 1170f, 990f, 1300f, 1080f, 3000f))
        // A section taller than the view less 260: the element comes into view with 160 around it
        assertEquals(2200f + 160f - 1080f, OverviewScroll.page(0f, header = false, 2000f, 2200f, 1000f, 2400f, 1080f, 3000f))
        // Clamped at the end of the page
        assertEquals(500f, OverviewScroll.page(0f, header = false, 2000f, 2100f, 1990f, 2300f, 1080f, 500f))
    }

    @Test fun `reveal moves only as far as needed`() {
        assertEquals(0f, OverviewScroll.reveal(0f, 200f, 720f, 1920f, 150f))
        assertEquals(1500f + 520f + 150f - 1920f, OverviewScroll.reveal(0f, 1500f, 2020f, 1920f, 150f))
        assertEquals(100f - 150f, OverviewScroll.reveal(300f, 100f, 620f, 1920f, 150f))
    }

    @Test fun `the backdrop deepens and drifts with the scroll and stops at its limits`() {
        val top = BackdropDepth.at(0f, reduced = false)
        assertEquals(.62f, top.sharp, 1e-4f)
        assertEquals(0f, top.deep, 1e-4f)
        assertEquals(1f, top.zoom, 1e-4f)
        val deep = BackdropDepth.at(2000f, reduced = false)
        assertEquals(0f, deep.sharp, 1e-4f)
        assertEquals(1f, deep.deep, 1e-4f)
        assertEquals(.87f, deep.shade, 1e-4f)
        assertEquals(-70f, deep.offsetY, 1e-4f)
        assertEquals(1.1f, deep.zoom, 1e-4f)
        val reduced = BackdropDepth.at(2000f, reduced = true)
        assertEquals(0f, reduced.offsetY, 0f)
        assertEquals(1f, reduced.zoom, 0f)
    }

    @Test fun `the cover flies from the card through a lifted midpoint into its slot`() {
        val card = r(150f, 520f, 316.5f, 474.75f)
        val slot = r(150f, 150f, 340f, 510f)
        val start = OverviewMotion.cover(0f, card, slot, 30f)
        assertEquals(0f, start.rect.left, 1e-3f)
        assertEquals(370f, start.rect.top, 1e-3f)
        assertEquals(316.5f, start.rect.width, 1e-3f)
        assertEquals(474.75f, start.rect.height, 1e-3f)
        val mid = OverviewMotion.cover(.5f, card, slot, 30f)
        assertEquals(370f / 2f - 30f, mid.rect.top, 1e-3f)
        assertEquals((316.5f + 340f) / 2f * 1.04f, mid.rect.width, 1e-3f)
        val end = OverviewMotion.cover(1f, card, slot, 30f)
        assertEquals(0f, end.rect.top, 1e-3f)
        assertEquals(340f, end.rect.width, 1e-3f)
        assertEquals(510f, end.rect.height, 1e-3f)
        assertEquals(30f, end.corner, 1e-4f)
    }

    @Test fun `a wide card's frame turns portrait while the picture in it keeps its proportions`() {
        val wide = r(150f, 520f, 980f * 1.055f, 450f * 1.055f)
        val slot = r(150f, 150f, 340f, 510f)
        for (p in listOf(0f, .25f, .5f, .75f, 1f)) {
            val frame = OverviewMotion.cover(p, wide, slot, 30f).rect
            val s = OverviewMotion.fillScale(frame.width, frame.height, 340f, 510f)
            // One scale for both axes that covers the frame: the portrait cover is cropped, never stretched
            assertTrue(340f * s >= frame.width - 1e-3f && 510f * s >= frame.height - 1e-3f)
            assertTrue(abs(340f * s - frame.width) < 1e-3f || abs(510f * s - frame.height) < 1e-3f)
        }
        // The frame starts as the wide card and ends as the slot
        assertEquals(980f * 1.055f / (450f * 1.055f), OverviewMotion.cover(0f, wide, slot, 30f).rect.let { it.width / it.height }, 1e-4f)
        assertEquals(340f / 510f, OverviewMotion.cover(1f, wide, slot, 30f).rect.let { it.width / it.height }, 1e-4f)
        assertEquals(1f, OverviewMotion.cardShown(0f), 0f)
        // Wide artwork remains intact until the middle of the flight, with a continuous mid-flight handover.
        assertEquals(1f, OverviewMotion.cardShown(.3f), 1e-4f)
        assertEquals(.5f, OverviewMotion.cardShown(.5f), 1e-4f)
        assertEquals(0f, OverviewMotion.cardShown(.7f), 1e-4f)
    }

    @Test fun `closing reverses the flight and the page`() {
        assertEquals(1f, OverviewMotion.flight(0f, opening = false), 1e-4f)
        assertEquals(0f, OverviewMotion.flight(OverviewMotion.FLIGHT_OUT.toFloat(), opening = false), 1e-4f)
        assertEquals(0f, OverviewMotion.flight(0f, opening = true), 1e-4f)
        assertEquals(1f, OverviewMotion.flight(OverviewMotion.FLIGHT_IN.toFloat(), opening = true), 1e-4f)
        val (rise, alpha) = OverviewMotion.page(0f, opening = true, reduced = false)
        assertEquals(OverviewMotion.PAGE_RISE, rise, 1e-4f)
        assertEquals(1f, alpha, 0f)
        val (gone, out) = OverviewMotion.page(OverviewMotion.PAGE_OUT.toFloat(), opening = false, reduced = false)
        assertEquals(OverviewMotion.PAGE_RISE, gone, 1e-4f)
        assertEquals(0f, out, 1e-4f)
    }

    @Test fun `the other covers lift away one after another and reduced motion only fades them`() {
        // 100 ms in, the neighbour has begun to lift; the cover four away waits for its 180 ms
        val near = OverviewMotion.homeCover(100f, 1, reduced = false)
        val far = OverviewMotion.homeCover(100f, 4, reduced = false)
        assertTrue(near.first < 0f)
        assertEquals(0f, far.first, 0f)
        assertEquals(1f, far.third, 0f)
        val done = OverviewMotion.homeCover(OverviewMotion.homeTotal(4, false).toFloat(), 4, reduced = false)
        assertEquals(-(160f + 4 * 30f), done.first, 1e-3f)
        assertEquals(.92f, done.second, 1e-4f)
        assertEquals(0f, done.third, 1e-4f)
        val reduced = OverviewMotion.homeCover(OverviewMotion.HOME_OUT.toFloat(), 4, reduced = true)
        assertEquals(0f, reduced.first, 0f)
        assertEquals(1f, reduced.second, 0f)
        assertEquals(0f, reduced.third, 1e-4f)
    }

    @Test fun `the header's parts rise 46 ms apart after their delay`() {
        val (before, hidden) = OverviewMotion.rise(OverviewMotion.HEADER_DELAY.toFloat(), OverviewMotion.HEADER_DELAY, 1, reduced = false)
        assertEquals(18f, before, 1e-4f)
        assertEquals(0f, hidden, 1e-4f)
        val (after, shown) = OverviewMotion.rise(OverviewMotion.HEADER_DELAY + 46f + 420f, OverviewMotion.HEADER_DELAY, 1, reduced = false)
        assertEquals(0f, after, 1e-4f)
        assertEquals(1f, shown, 1e-4f)
    }

    @Test fun `a cover dragged up follows with resistance and opens past the threshold`() {
        assertEquals(0f, OverviewMotion.drag(40f).first, 0f)
        val (lift, scale) = OverviewMotion.drag(-100f)
        assertEquals(-55f, lift, 1e-4f)
        assertEquals(1.055f + 55f / 1400f, scale, 1e-4f)
        assertEquals(-143f, OverviewMotion.drag(-1000f).first, 1e-3f)
        assertTrue(OverviewMotion.drag(-80f).first < OverviewMotion.DRAG_OPENS)
        assertTrue(OverviewMotion.drag(-60f).first > OverviewMotion.DRAG_OPENS)
    }

    @Test fun `count-ups ease out over 1300 ms after their delay`() {
        assertEquals(0f, OverviewMotion.countUp(380f, 380, reduced = false), 0f)
        assertEquals(1f, OverviewMotion.countUp(380f + 1300f, 380, reduced = false), 1e-4f)
        assertEquals(1f - .5f * .5f * .5f * .5f, OverviewMotion.countUp(380f + 650f, 380, reduced = false), 1e-4f)
        assertEquals(1f, OverviewMotion.countUp(380f, 380, reduced = true), 0f)
    }

    @Test fun `the viewer's picture fits 16 by 9 a little above the middle`() {
        val fit = viewerFit(1920f, 1080f)
        assertEquals(780f, fit.height, 1e-3f)
        assertEquals(780f * 16f / 9f, fit.width, 1e-3f)
        assertEquals(140f, fit.top, 1e-3f)
        val tall = viewerFit(1920f, 1440f)
        assertEquals(1640f, tall.width, 1e-3f)
        assertEquals(1640f * 9f / 16f, tall.height, 1e-3f)
    }
}
