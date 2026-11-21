package com.limelight.shell.screens

import androidx.compose.ui.geometry.Rect
import com.limelight.shell.ui.Motion
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
        val (rise, alpha) = OverviewMotion.page(0f, opening = true, reduced = false)
        assertEquals(OverviewMotion.PAGE_RISE, rise, 1e-4f)
        assertEquals(1f, alpha, 0f)
        val (gone, out) = OverviewMotion.page(OverviewMotion.PAGE_OUT.toFloat(), opening = false, reduced = false)
        assertEquals(OverviewMotion.PAGE_RISE, gone, 1e-4f)
        assertEquals(0f, out, 1e-4f)
    }

    @Test fun `the other covers lift away one after another and reduced motion only fades them`() {
        fun cover(millis: Float, distance: Int, reduced: Boolean) =
            OverviewMotion.homeCover(OverviewMotion.homeAway(millis, distance, reduced), distance, reduced)
        // 100 ms in, the neighbour has begun to lift; the cover four away waits for its 180 ms
        val near = cover(100f, 1, reduced = false)
        val far = cover(100f, 4, reduced = false)
        assertTrue(near.first < 0f)
        assertEquals(0f, far.first, 0f)
        assertEquals(1f, far.third, 0f)
        // Landed on the settle spring: within about 1 % of the way, invisible
        val done = cover(OverviewMotion.homeTotal(4, false).toFloat(), 4, reduced = false)
        assertEquals(-(160f + 4 * 30f), done.first, 3.5f)
        assertEquals(.92f, done.second, 1e-3f)
        assertEquals(0f, done.third, 0f)
        val reduced = cover(OverviewMotion.HOME_OUT.toFloat(), 4, reduced = true)
        assertEquals(0f, reduced.first, 0f)
        assertEquals(1f, reduced.second, 0f)
        assertEquals(0f, reduced.third, 1e-4f)
        // Opacity never overshoots, however far the spring swings
        for (t in 0..1200 step 8) {
            val a = cover(t.toFloat(), 2, reduced = false).third
            assertTrue(a in 0f..1f)
        }
    }

    @Test fun `closing brings the dashboard back with the cover, from where the opening left it, without a jump`() {
        // Fully away: every part comes back together on the closing flight's spring and has landed when the closing clock ends
        val end = OverviewController.closeTotal(reduced = false)
        assertEquals(OverviewMotion.CLOSE_SETTLE, end)
        assertEquals(1f, OverviewMotion.homeBack(0f, 1f, reduced = false), 0f)
        assertEquals(0f, OverviewMotion.homeBack(end.toFloat(), 1f, reduced = false), .002f)
        // It settles: a 2 % swing past home at most, and the dashboard's covers stay opaque
        val deepest = (0..end step 4).minOf { OverviewMotion.homeBack(it.toFloat(), 1f, reduced = false) }
        assertTrue(deepest < 0f && deepest > -.021f)
        assertEquals(1f, OverviewMotion.homeCover(deepest, 1, reduced = false).third, 0f)
        // A closing that interrupts the opening starts where the parts are, and never pushes them further away first
        assertEquals(.3f, OverviewMotion.homeBack(0f, .3f, reduced = false), 0f)
        assertTrue((0..end step 8).all { OverviewMotion.homeBack(it.toFloat(), .3f, reduced = false) <= .3f })
    }

    @Test fun `springs overshoot once by the agreed amounts`() {
        fun peak(response: Float, damping: Float) = (0..2000).maxOf { OverviewMotion.spring(it.toFloat(), response, damping) } - 1f
        assertEquals(0f, OverviewMotion.spring(0f, .62f, .74f), 0f)
        // Open flight about 3.2 %, close flight 2 %, the settle spring 1.5 %, a chip 4.6 %
        assertEquals(.032f, peak(OverviewMotion.FLIGHT_OPEN_RESPONSE, OverviewMotion.FLIGHT_OPEN_DAMPING), .002f)
        assertEquals(.020f, peak(OverviewMotion.FLIGHT_CLOSE_RESPONSE, OverviewMotion.FLIGHT_CLOSE_DAMPING), .002f)
        assertEquals(.015f, peak(OverviewMotion.SETTLE_RESPONSE, OverviewMotion.SETTLE_DAMPING), .002f)
        assertEquals(.046f, peak(OverviewMotion.CHIP_RESPONSE, OverviewMotion.CHIP_DAMPING), .002f)
        // The flight's spring matches the closed form: stiffness (2π / response)²
        assertEquals(102.7f, OverviewMotion.stiffness(OverviewMotion.FLIGHT_OPEN_RESPONSE), .1f)
        assertEquals(130.5f, OverviewMotion.stiffness(OverviewMotion.FLIGHT_CLOSE_RESPONSE), .1f)
        // Every settling part has landed (within 1 %) by SETTLE, and the closing flight (within 0.2 %) by CLOSE_SETTLE
        assertEquals(1f, OverviewMotion.settle(OverviewMotion.SETTLE.toFloat()), .01f)
        assertTrue((OverviewMotion.CLOSE_SETTLE..2000).all { abs(OverviewMotion.closeSpring(it.toFloat()) - 1f) < .002f })
    }

    @Test fun `the flying cover's place passes its end once by about 5 percent while its size never passes the end's`() {
        val card = r(150f, 520f, 316.5f, 474.75f)
        val slot = r(150f, 150f, 340f, 510f)
        val distance = 370f
        fun excursions(values: List<Float>) = values.zipWithNext().count { (a, b) -> a <= 0f && b > 0f }
        // Opening: past the slot the frame rises above it (its top above 0) and comes back, exactly once (a twentieth of a pixel counts)
        val opening = (0..2000).map { OverviewMotion.spring(it.toFloat(), OverviewMotion.FLIGHT_OPEN_RESPONSE, OverviewMotion.FLIGHT_OPEN_DAMPING) }
        val upFrames = opening.map { OverviewMotion.cover(it, card, slot, 30f) }
        val above = upFrames.map { -it.rect.top - .05f }
        assertEquals(1, excursions(above))
        assertTrue(-upFrames.minOf { it.rect.top } / distance in 0.04f..0.06f)
        upFrames.forEachIndexed { i, f ->
            if (opening[i] >= 1f) {
                assertEquals(340f, f.rect.width, 1e-3f)
                assertEquals(510f, f.rect.height, 1e-3f)
                assertEquals(OverviewMotion.TO_CORNER, f.corner, 0f)
            }
        }
        assertEquals(0f, upFrames.last().rect.top, .05f)
        // Closing: past the card the frame sinks below it and comes back, exactly once, at the card's size
        val closing = (0..2000).map { 1f - OverviewMotion.closeSpring(it.toFloat()) }
        val downFrames = closing.map { OverviewMotion.cover(it, card, slot, 30f) }
        val below = downFrames.map { it.rect.top - (card.top - slot.top) - .05f }
        assertEquals(1, excursions(below))
        assertTrue((downFrames.maxOf { it.rect.top } - (card.top - slot.top)) / distance in 0.04f..0.06f)
        downFrames.forEachIndexed { i, f ->
            if (closing[i] <= 0f) {
                assertEquals(card.width, f.rect.width, 1e-3f)
                assertEquals(card.height, f.rect.height, 1e-3f)
                assertEquals(OverviewMotion.FROM_CORNER, f.corner, 0f)
            }
        }
        // A long flight never swings more than the cap
        assertTrue(OverviewMotion.overshootPx(.05f, 5000f, 1f) <= OverviewMotion.OVERSHOOT_CAP)
        assertEquals(0f, OverviewMotion.overshootPx(0f, 400f, 1f), 0f)
    }

    @Test fun `a closing that interrupts the opening leaves from where the parts are`() {
        // The page 50 px below its place sinks from there
        assertEquals(50f, OverviewMotion.page(0f, opening = false, reduced = false, from = 50f).first, 0f)
        assertEquals(OverviewMotion.PAGE_RISE, OverviewMotion.page(OverviewMotion.PAGE_OUT.toFloat(), opening = false, reduced = false, from = 50f).first, 1e-4f)
        // The flying name turns back from where it was and lands on the dashboard's
        assertEquals(.4f, OverviewMotion.title(0f, opening = false, from = .4f), 0f)
        assertEquals(0f, OverviewMotion.title(OverviewMotion.FLIGHT_OUT.toFloat(), opening = false, from = .4f), .002f)
        // A part only half faded in fades out from there
        assertEquals(.5f, OverviewMotion.leave(0f, reduced = false, from = .5f).second, 0f)
        assertEquals(0f, OverviewMotion.leave(OverviewMotion.LEAVE.toFloat(), reduced = false, from = .5f).second, 0f)
    }

    @Test fun `each ring's arc overshoots about 8 percent once, 90 ms apart, and settles without a second bounce`() {
        val fills = (0..OverviewMotion.RATINGS_TOTAL).map { OverviewMotion.ring(it.toFloat(), 0, reduced = false) }
        val top = fills.max()
        assertTrue(top > 1.06f && top <= 1f + OverviewMotion.RING_OVERSHOOT)
        // Sampled every millisecond the curve has exactly one local maximum: it rises, peaks once and comes back without passing
        // the target again
        val peaks = fills.zipWithNext { a, b -> b - a }.zipWithNext().count { (rise, fall) -> rise > 0f && fall <= 0f }
        assertEquals(1, peaks)
        val peakAt = fills.indexOf(top)
        assertTrue(fills.drop(peakAt).all { it >= 1f })
        // A round, slow arc: the peak comes about 0.6 s in, and it rests exactly on its score once settled
        assertTrue(peakAt in 550..650)
        assertEquals(1f, fills.last(), 0f)
        assertEquals(1f, OverviewMotion.ring(OverviewMotion.RING_SETTLE.toFloat(), 0, reduced = false), 0f)
        assertEquals(1f, OverviewMotion.ring(OverviewMotion.RING_SETTLE - 1f, 0, reduced = false), .002f)
        // The second ring starts 90 ms later
        assertEquals(0f, OverviewMotion.ring(OverviewMotion.RING_STAGGER.toFloat(), 1, reduced = false), 0f)
        assertTrue(OverviewMotion.ring(OverviewMotion.RING_STAGGER + 50f, 1, reduced = false) > 0f)
        // The ring grows into place with its arc, from 0.92
        assertEquals(OverviewMotion.RING_FROM_SCALE, OverviewMotion.ringScale(0f, reduced = false), 0f)
        // Reduced motion: full at once, a short fade, no growth
        assertEquals(1f, OverviewMotion.ring(0f, 0, reduced = true), 0f)
        assertEquals(1f, OverviewMotion.ringScale(1f, reduced = true), 0f)
        assertEquals(0f, OverviewMotion.ringAlpha(0f, reduced = true), 0f)
        assertEquals(1f, OverviewMotion.ringAlpha(Motion.FADE.toFloat(), reduced = true), 1e-4f)
    }

    @Test fun `a ring's number counts up on a plain ease-out to its score, never past it`() {
        val counts = (0..OverviewMotion.RATINGS_TOTAL).map { OverviewMotion.ringCount(it.toFloat(), 1, reduced = false) }
        assertTrue(counts.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(counts.all { it in 0f..1f })
        assertEquals(0f, OverviewMotion.ringCount(OverviewMotion.RING_STAGGER.toFloat(), 1, reduced = false), 0f)
        assertEquals(1f, OverviewMotion.ringCount((OverviewMotion.RING_STAGGER + OverviewMotion.RING_COUNT).toFloat(), 1, reduced = false), 0f)
        // Ease-out: past the halfway mark at half the time
        assertTrue(OverviewMotion.ringCount(OverviewMotion.RING_COUNT / 2f, 0, reduced = false) > .6f)
        // A 98 never shows more than 98, and lands on it
        assertTrue(counts.all { OverviewMotion.ringNumber(98.0, it) <= 98L })
        assertEquals(98L, OverviewMotion.ringNumber(98.0, counts.last()))
        assertEquals(1f, OverviewMotion.ringCount(0f, 0, reduced = true), 0f)
    }

    @Test fun `chips pop in from 0·9 with a gentle overshoot`() {
        assertEquals(OverviewMotion.CHIP_FROM_SCALE, OverviewMotion.chip(0f, reduced = false), 0f)
        val top = (0..1000).maxOf { OverviewMotion.chip(it.toFloat(), reduced = false) }
        assertTrue(top > 1f && top < 1.006f)
        assertEquals(1f, OverviewMotion.chip(1000f, reduced = false), 1e-3f)
        assertEquals(1f, OverviewMotion.chip(0f, reduced = true), 0f)
        assertEquals(1f, OverviewMotion.chip(Float.MAX_VALUE, reduced = false), 0f)
    }

    @Test fun `a logo crossfades in place, out over the first 40 percent of the flight and in over the last half`() {
        val f = OverviewMotion.FLIGHT_IN.toFloat()
        assertEquals(1f, OverviewMotion.logoLeaving(0f, opening = true), 0f)
        assertEquals(0f, OverviewMotion.logoLeaving(f * .4f, opening = true), 1e-4f)
        // The fade in starts at the whole millisecond
        assertEquals(0f, OverviewMotion.logoArriving((f * .5f).toInt().toFloat(), opening = true), 1e-4f)
        assertEquals(1f, OverviewMotion.logoArriving(f, opening = true), 1e-4f)
        // Closing: the header's logo leaves over the first 40 % of the flight back, the dashboard's arrives over its last half
        val b = OverviewMotion.FLIGHT_OUT.toFloat()
        assertEquals(0f, OverviewMotion.logoLeaving(b * .4f, opening = false), 1e-4f)
        assertEquals(0f, OverviewMotion.logoArriving(b * .5f, opening = false), 1e-4f)
        assertEquals(1f, OverviewMotion.logoArriving(b, opening = false), 1e-4f)
    }

    @Test fun `the overview's clock never skips more than a step, however long a frame took`() {
        val frames = ArrayDeque(listOf(1_000_000_000L, 1_000_000_000L, 1_600_000_000L, 1_616_666_666L, 1_616_666_666L))
        val real = object : androidx.compose.runtime.MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = onFrame(frames.removeFirst())
        }
        val clock = SteadyFrameClock(real)
        val seen = kotlinx.coroutines.runBlocking { List(5) { clock.withFrameNanos { it } } }
        // The first frame is the start; two awaiters of one frame see one time; a 600 ms frame counts as one step
        assertEquals(1_000_000_000L, seen[0])
        assertEquals(seen[0], seen[1])
        assertEquals(seen[1] + SteadyFrameClock.MAX_STEP_NANOS, seen[2])
        assertEquals(seen[2] + 16_666_666L, seen[3])
        assertEquals(seen[3], seen[4])
    }

    @Test fun `the header's parts rise one after another after their delay and land with a settle`() {
        val start = OverviewMotion.HEADER_DELAY + OverviewMotion.HEADER_STAGGER
        val (before, hidden) = OverviewMotion.rise(start.toFloat(), OverviewMotion.HEADER_DELAY, 1, reduced = false)
        assertEquals(18f, before, 1e-4f)
        assertEquals(0f, hidden, 1e-4f)
        val (after, shown) = OverviewMotion.rise(start + OverviewMotion.SETTLE.toFloat(), OverviewMotion.HEADER_DELAY, 1, reduced = false)
        assertEquals(0f, after, .25f)
        assertEquals(1f, shown, 0f)
        // It passes its place by a fraction of a pixel and comes back; its opacity never passes 1
        val swing = (0..600).minOf { OverviewMotion.rise(start + it.toFloat(), OverviewMotion.HEADER_DELAY, 1, reduced = false).first }
        assertTrue(swing < 0f && swing > -.4f)
        // Reduced motion: no rise, a short fade
        val (still, faded) = OverviewMotion.rise(start + Motion.FADE.toFloat(), OverviewMotion.HEADER_DELAY, 1, reduced = true)
        assertEquals(0f, still, 0f)
        assertEquals(1f, faded, 1e-4f)
    }

    @Test fun `a cover dragged up follows on a rubber band, at most 60 percent of the finger and never past its cap`() {
        val cap = OverviewMotion.dragCap(90f, 450f)
        assertEquals(90f - 450f * (OverviewMotion.DRAG_GROW + HomeCompact.FOCUS_SCALE - 1f) / 2f - OverviewMotion.DRAG_MARGIN, cap, 1e-3f)
        assertEquals(OverviewMotion.CoverDrag(0f, HomeCompact.FOCUS_SCALE, 1f, 0f), OverviewMotion.drag(40f, cap))
        assertEquals(0f, OverviewMotion.drag(-100f, 0f).lift, 0f)
        val drags = (0..2000).map { OverviewMotion.drag(-it.toFloat(), cap) }
        drags.forEachIndexed { finger, d ->
            assertTrue(-d.lift <= OverviewMotion.DRAG_RATIO * finger + 1e-3f)
            assertTrue(-d.lift <= cap)
            // Grown to its scale about its middle, the cover's top stays the margin under the row's edge (90 px above its place)
            assertTrue(90f + d.lift - 450f * (d.scale - 1f) / 2f >= OverviewMotion.DRAG_MARGIN - 1e-3f)
            assertTrue(d.alpha in (1f - OverviewMotion.DRAG_FADE - 1e-4f)..1f)
        }
        assertTrue(drags.zipWithNext().all { (a, b) -> b.lift <= a.lift && b.progress >= a.progress && b.alpha <= a.alpha + 1e-6f })
        // Nearly the whole finger at first; near the cap the cover hardly moves, and it fades only in the second half
        assertEquals(-OverviewMotion.DRAG_RATIO * 10f, OverviewMotion.drag(-10f, cap).lift, .6f)
        assertEquals(cap, -drags.last().lift, .01f * cap)
        assertEquals(1f, OverviewMotion.drag(-cap, cap).alpha, 0f)
        assertEquals(1f - OverviewMotion.DRAG_FADE, drags.last().alpha, .01f)
        // The slope is the band's derivative: a finger's speed times it is the cover's
        for (finger in listOf(0f, 40f, 120f)) {
            val numeric = (OverviewMotion.drag(-(finger + .5f), cap).lift - OverviewMotion.drag(-(finger - .5f).coerceAtLeast(0f), cap).lift) /
                -(finger + .5f - (finger - .5f).coerceAtLeast(0f))
            assertEquals(numeric, OverviewMotion.dragSlope(-finger, cap), .01f)
        }
    }

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 1e-3f)
        assertEquals(expected.top, actual.top, 1e-3f)
        assertEquals(expected.right, actual.right, 1e-3f)
        assertEquals(expected.bottom, actual.bottom, 1e-3f)
    }

    @Test fun `a finger's release hands the cover's place and speed to the flight's spring`() {
        val card = r(150f, 560f, 316.5f, 474.75f)
        val slot = r(150f, 150f, 340f, 510f)
        val arc = 30f
        fun top(p: Float, to: Rect = slot) = OverviewMotion.cover(p, card, to, arc).rect.top
        // Closing: the frame's top moves down as the progress falls; 600 px/s down is that many px/s of the frame's top
        val down = OverviewMotion.flightVelocity(600f, card, slot, arc, opening = false)
        assertTrue(down < 0f)
        assertEquals(600f, (top(.999f) - top(1f)) / -.001f * down, 1f)
        // Opening: a finger moving up hands the frame's speed at the card's end
        val up = OverviewMotion.flightVelocity(-500f, card, slot, arc, opening = true)
        assertTrue(up > 0f)
        assertEquals(-500f, (top(.001f) - top(0f)) / .001f * up, 1f)
        // Only motion along the way carries over, and never faster than the cap
        assertEquals(0f, OverviewMotion.flightVelocity(-600f, card, slot, arc, opening = false), 0f)
        assertEquals(0f, OverviewMotion.flightVelocity(500f, card, slot, arc, opening = true), 0f)
        assertEquals(-OverviewMotion.MAX_HANDOFF, OverviewMotion.flightVelocity(1e6f, card, slot, arc, opening = false), 0f)
        assertEquals(0f, OverviewMotion.flightVelocity(600f, slot, slot, 0f, opening = false), 0f)
        // The pulled cover (shrunk about its slot's top-left corner) is exactly where the closing flight starts, and it still lands on the card
        val s = OverviewMotion.pulledScale(200f)
        assertEquals(1f - 200f / 2400f, s, 1e-6f)
        assertEquals(1f, OverviewMotion.pulledScale(0f), 0f)
        val start = OverviewMotion.cover(1f, card, OverviewMotion.releaseSlot(slot, s), arc).rect
        assertRect(Rect(0f, 0f, 340f * s, 510f * s), start)
        val landed = OverviewMotion.cover(0f, card, OverviewMotion.releaseSlot(slot, s), arc).rect
        assertEquals(card.left - slot.left, landed.left, 1e-3f)
        assertEquals(card.top - slot.top, landed.top, 1e-3f)
        assertEquals(card.width, landed.width, 1e-3f)
        // A card a finger had lifted: the closing leaves from it and lands on the card at rest
        val lifted = card.translate(0f, -40f)
        assertRect(lifted, OverviewMotion.closingFrom(.6f, .6f, lifted, card))
        assertRect(card, OverviewMotion.closingFrom(0f, .6f, lifted, card))
        assertEquals(card.top - 20f, OverviewMotion.closingFrom(.3f, .6f, lifted, card).top, 1e-3f)
        assertRect(card, OverviewMotion.closingFrom(1f, 1f, card, card))
        // A card that faded under the finger flies on from its opacity and is whole by 30 % of the way
        assertEquals(.8f, OverviewMotion.flightAlpha(0f, .8f), 0f)
        assertEquals(1f, OverviewMotion.flightAlpha(.3f, .8f), 0f)
    }

    @Test fun `count-ups ease out over 1300 ms after their delay`() {
        assertEquals(0f, OverviewMotion.countUp(380f, 380, reduced = false), 0f)
        assertEquals(1f, OverviewMotion.countUp(380f + 1300f, 380, reduced = false), 1e-4f)
        assertEquals(1f - .5f * .5f * .5f * .5f, OverviewMotion.countUp(380f + 650f, 380, reduced = false), 1e-4f)
        assertEquals(1f, OverviewMotion.countUp(380f, 380, reduced = true), 0f)
    }

    @Test fun `the viewer's picture owns the whole stage, edge to edge`() {
        val fit = viewerFit(1920f, 1080f)
        assertEquals(0f, fit.left, 0f)
        assertEquals(0f, fit.top, 0f)
        assertEquals(1920f, fit.width, 0f)
        assertEquals(1080f, fit.height, 0f)
    }
}
