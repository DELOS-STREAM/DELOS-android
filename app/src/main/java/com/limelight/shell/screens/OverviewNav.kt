package com.limelight.shell.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.RowCandidates
import com.limelight.shell.ui.RowFirst
import com.limelight.shell.ui.Motion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** A direction of the D-pad, the arrow keys or the stick. */
internal enum class NavDirection {
    UP, DOWN, LEFT, RIGHT;

    val opposite: NavDirection get() = when (this) { UP -> DOWN; DOWN -> UP; LEFT -> RIGHT; RIGHT -> LEFT }
    val vertical: Boolean get() = this == UP || this == DOWN
}

/** A focusable element of the overview as navigation sees it: where it is on screen and the row it belongs to (null: none). */
internal data class NavTarget(val rect: Rect, val group: String? = null, val locked: Boolean = false)

/**
 * The overview's spatial focus (the prototype's `Focus.move`): the element in [direction] whose edge is nearest, sideways distance
 * weighing 2.2 times as much, with a slight pull towards the same column or line. Up and Down then follow the shell's row-first rule
 * ([RowFirst]): they land on the first element on screen of the row that nearest element is in. Left and right never leave a
 * [NavTarget.locked] row (the buttons, the carousel, the links, the similar games). Coming back the way one came returns to the element
 * one came from ([remember]), so Down then Up is always a round trip; Up or Down back into the row the last vertical move left lands
 * on the element it left.
 */
internal class OverviewNavigator<K : Any> {
    private val back = HashMap<K, Pair<NavDirection, K>>()

    /** The element the last vertical move left and the direction that leads back to it. */
    private var left: K? = null
    private var leftBack: NavDirection? = null
    private val rows = RowCandidates()
    private val keys = ArrayList<K>()

    fun forget() {
        back.clear()
        left = null
    }

    /** The element [direction] of [from] among [targets], or null at the edge. */
    fun move(from: K, direction: NavDirection, targets: Map<K, NavTarget>): K? {
        val a = targets[from] ?: return null
        val memo = back[from]
        val best = if (memo != null && memo.first == direction && memo.second in targets) memo.second
        else nearest(from, a, direction, targets)?.let { hit -> if (direction.vertical) rowFirst(from, a.rect, hit, direction, targets) else hit }
        if (best != null) {
            back[best] = direction.opposite to from
            if (direction.vertical) {
                left = from
                leftBack = direction.opposite
            }
        }
        return best
    }

    /** The row-first target of a vertical move from [from] whose nearest element is [hit]; on screen means a left edge at x >= 0. */
    private fun rowFirst(from: K, r: Rect, hit: K, direction: NavDirection, targets: Map<K, NavTarget>): K {
        rows.clear()
        keys.clear()
        var hitIndex = -1
        var remembered = -1
        for ((key, b) in targets) {
            if (key == from) continue
            if (key == hit) hitIndex = rows.size
            if (key == left && leftBack == direction) remembered = rows.size
            rows.add(b.rect.left, b.rect.top, b.rect.right, b.rect.bottom, onScreen = b.rect.left >= 0f)
            keys.add(key)
        }
        val i = RowFirst.resolve(rows, hitIndex, r.left, r.top, r.right, r.bottom, direction == NavDirection.DOWN, remembered)
        val target = if (i >= 0) keys[i] else hit
        keys.clear()
        return target
    }

    private fun nearest(from: K, a: NavTarget, direction: NavDirection, targets: Map<K, NavTarget>): K? {
        val r = a.rect
        var best: K? = null
        var score = Float.POSITIVE_INFINITY
        for ((key, b) in targets) {
            if (key == from) continue
            if (!direction.vertical && a.group != null && a.locked && b.group != a.group) continue
            val s = score(r, b.rect, direction) ?: continue
            if (s < score) {
                score = s
                best = key
            }
        }
        return best
    }

    companion object {
        /** The prototype's score of [b] seen from [a] in [direction]; null when [b] does not lie that way. */
        fun score(a: Rect, b: Rect, direction: NavDirection): Float? {
            val p: Float
            val q: Float
            when (direction) {
                NavDirection.DOWN -> {
                    if (b.top + b.height / 2 <= a.bottom - 2 || b.top < a.top + a.height * .5f) return null
                    p = b.top - a.bottom
                    q = max(0f, max(b.left - a.right, a.left - b.right))
                }
                NavDirection.UP -> {
                    if (b.top + b.height / 2 >= a.top + 2 || b.bottom > a.top + a.height * .5f) return null
                    p = a.top - b.bottom
                    q = max(0f, max(b.left - a.right, a.left - b.right))
                }
                NavDirection.RIGHT -> {
                    if (b.left + b.width / 2 <= a.right - 2 || b.left < a.left + a.width * .5f) return null
                    p = b.left - a.right
                    q = max(0f, max(b.top - a.bottom, a.top - b.bottom))
                }
                NavDirection.LEFT -> {
                    if (b.left + b.width / 2 >= a.left + 2 || b.right > a.left + a.width * .5f) return null
                    p = a.left - b.right
                    q = max(0f, max(b.top - a.bottom, a.top - b.bottom))
                }
            }
            val column = if (direction.vertical) abs(b.center.x - a.center.x) * .08f else 0f
            val line = if (!direction.vertical) abs(b.center.y - a.center.y) * .08f else 0f
            return max(0f, p) + q * 2.2f + column + line
        }
    }
}

/**
 * Where the page scrolls when an element takes the focus (the prototype's `onFocus` and `Scroller.reveal`), in content pixels: the
 * header shows the top; a section that fits the view comes to rest [SECTION_ANCHOR] under the view's top; in a taller one the element
 * is brought into view with [ELEMENT_MARGIN] around it. Clamped to `0..max`.
 */
internal object OverviewScroll {
    const val SECTION_ANCHOR = 132f
    const val ELEMENT_MARGIN = 160f

    /** A section this much shorter than the view counts as fitting it. */
    const val TALL_SLACK = 260f

    /** Room left after the last section when the page is scrolled to its end. */
    const val END_PAD = 40f

    /** [current]'s target for an element at [elementTop]..[elementBottom] in a section at [sectionTop]..[sectionBottom] (content pixels). */
    fun page(
        current: Float, header: Boolean, elementTop: Float, elementBottom: Float, sectionTop: Float, sectionBottom: Float,
        view: Float, max: Float,
    ): Float {
        val target = when {
            header -> 0f
            sectionBottom - sectionTop > view - TALL_SLACK -> reveal(current, elementTop, elementBottom, view, ELEMENT_MARGIN)
            else -> sectionTop - SECTION_ANCHOR
        }
        return target.coerceIn(0f, max(0f, max))
    }

    /** Just enough scroll to show [start]..[end] with [margin] around it in a view of [view] scrolled to [current]. */
    fun reveal(current: Float, start: Float, end: Float, view: Float, margin: Float): Float = when {
        start - margin < current -> start - margin
        end + margin > current + view -> end + margin - view
        else -> current
    }
}

/** The blur depth, parallax and zoom of the hero behind the page at a scroll of [scroll] content pixels (the prototype's `linkBackdrop`). */
internal data class BackdropDepth(val sharp: Float, val deep: Float, val shade: Float, val offsetY: Float, val zoom: Float) {
    companion object {
        const val SHARP = .62f
        const val SHADE = .62f

        fun at(scroll: Float, reduced: Boolean): BackdropDepth {
            val v = max(0f, scroll)
            val deep = min(1f, v / 520f)
            return BackdropDepth(
                sharp = SHARP * (1f - deep),
                deep = deep,
                shade = SHADE + deep * .25f,
                offsetY = if (reduced) 0f else -min(v, 700f) * .1f,
                zoom = if (reduced) 1f else 1f + min(v, 900f) / 9000f,
            )
        }
    }
}

/**
 * The opening and closing motion of the overview (the prototype's `A.open`, `A.close` and `homeOut`), as pure functions of the time
 * since the motion began, so every frame is reproducible and tested (`OverviewNavTest`).
 *
 * Springs (owner, 2026-11-20, retuned 2026-11-21) are given as a response (the period of the undamped oscillation, seconds) and a
 * damping ratio, the same numbers on every platform; a spring's stiffness is (2π / response)². The cover's flight runs on a real
 * spring ([FlightOpen], [FlightClose]) so a close reverses it from its position and velocity, and a finger hands its release over to it
 * ([flightVelocity], [releaseSlot]); everything else that lands uses the step response of the settle spring ([settle]), so it lands
 * with the same gentle settle. Opacity never overshoots: it reads a spring clamped to 0…1. The timeline is the prototype's (a.html).
 */
internal object OverviewMotion {
    /** The screenshot viewer's zoom (`--ease-flight`) and the closing `ease-in-out`. */
    val Flight = CubicBezierEasing(.2f, .9f, .25f, 1f)
    val InOut = CubicBezierEasing(.65f, 0f, .35f, 1f)

    /** The cover's flight into the header: one gentle overshoot (3.2 %), its peak 461 ms in. */
    const val FLIGHT_OPEN_RESPONSE = .62f
    const val FLIGHT_OPEN_DAMPING = .74f

    /** The cover's flight back into its card: 2 % overshoot. */
    const val FLIGHT_CLOSE_RESPONSE = .55f
    const val FLIGHT_CLOSE_DAMPING = .78f

    /** The closing flight's spring is within 0.2 % of the card by then: the closing clock runs at least this long. */
    const val CLOSE_SETTLE = 700

    /** The title, the page, the header's parts and the dashboard's covers and info row: 1.5 % overshoot, within about 1 % by [SETTLE]. */
    const val SETTLE_RESPONSE = .6f
    const val SETTLE_DAMPING = .8f
    const val SETTLE = 610

    /** The stiffness of a spring of [response] seconds (unit mass). */
    fun stiffness(response: Float): Float = (2f * PI.toFloat() / response).let { it * it }

    val FlightOpen = androidx.compose.animation.core.spring<Float>(FLIGHT_OPEN_DAMPING, stiffness(FLIGHT_OPEN_RESPONSE), visibilityThreshold = .0005f)
    val FlightClose = androidx.compose.animation.core.spring<Float>(FLIGHT_CLOSE_DAMPING, stiffness(FLIGHT_CLOSE_RESPONSE), visibilityThreshold = .0005f)

    /** How long the flight counts for the logos' crossfade ([logoLeaving], [logoArriving]) and the closing clock (a.html: 780 and 640 ms). */
    const val FLIGHT_IN = 780
    const val FLIGHT_OUT = 640
    const val PAGE_RISE = 120f
    const val PAGE_OUT = 560
    const val BACKDROP_IN = 900
    const val BACKDROP_OUT = 640

    /** A part of the header leaving while the overview closes ([leave]). */
    const val LEAVE = 240

    /** The rings start [RATINGS_DELAY] after the opening began ([ring]); the streaming stats count up over [COUNT_UP] ([countUp]). */
    const val RATINGS_DELAY = 380
    const val COUNT_UP = 1300

    /** The other covers lift away one after another, [HOME_STAGGER] apart by distance from the focused one; [HOME_OUT] each. */
    const val HOME_OUT = 620
    const val HOME_STAGGER = 45

    /** The header's parts rise after [HEADER_DELAY], the cue under it (the sixth part, so at 730 ms) after [CUE_DELAY], [HEADER_STAGGER] apart. */
    const val HEADER_DELAY = 260
    const val CUE_DELAY = 500
    const val HEADER_STAGGER = 46

    /** A cover flies [ARC] stage pixels above the straight line at its midpoint, [MID_SCALE] times as big. */
    const val ARC = 30f
    const val MID_SCALE = 1.04f

    /** The cover's corner on the dashboard (26 grown by the focus scale) and in the header. */
    const val FROM_CORNER = 26f * 1.055f
    const val TO_CORNER = 30f

    /** How long the whole opening runs, so the last staggered part has landed. */
    val OPEN_TOTAL: Int get() = max(FLIGHT_IN, CUE_DELAY + 5 * HEADER_STAGGER + SETTLE)

    /**
     * The step response of a spring of [response] seconds and [damping] (below 1) [millis] after it began: 0 before, then towards 1,
     * passing it once by e^(−πζ/√(1−ζ²)).
     */
    fun spring(millis: Float, response: Float, damping: Float): Float {
        if (millis <= 0f) return 0f
        val t = millis / 1000f
        val w = 2f * PI.toFloat() / response
        val decay = damping * w
        if (decay * t > 24f) return 1f
        val wd = w * sqrt(1f - damping * damping)
        return 1f - exp(-decay * t) * (cos(wd * t) + decay / wd * sin(wd * t))
    }

    /** The settle spring [millis] after it began. */
    fun settle(millis: Float): Float = spring(millis, SETTLE_RESPONSE, SETTLE_DAMPING)

    /** The closing flight's spring from rest [millis] after it began: what comes back with the cover lands with it. */
    fun closeSpring(millis: Float): Float = spring(millis, FLIGHT_CLOSE_RESPONSE, FLIGHT_CLOSE_DAMPING)

    /** One frame of the cover in flight: the frame it shows, relative to the slot's top-left (screen pixels), and its corner (stage pixels). */
    data class Frame(val rect: Rect, val corner: Float)

    /**
     * The cover at [progress] of its flight from [from] to [to] (both on screen): the keyframes of the prototype's `Flight.cover`,
     * start, a lifted and slightly grown midpoint, end, with the corner going from [FROM_CORNER] to [TO_CORNER]. Only the frame
     * moves and changes its shape (a wide card's into the portrait slot): the picture in it keeps its proportions ([fillScale]).
     * (0, 0) and the slot's size is the cover at rest in its slot.
     *
     * A spring passes its end a little: past either end the frame stays where it lands and grows about its centre by the overshoot
     * (never shrinks, so neither the slot nor the card's place is ever uncovered), and its corner stays the end's.
     */
    fun cover(progress: Float, from: Rect, to: Rect, arcPx: Float): Frame {
        val p = progress.coerceIn(0f, 1f)
        val ax = from.left - to.left
        val ay = from.top - to.top
        val mx = ax * .5f
        val my = ay * .5f - arcPx
        val mw = (from.width + to.width) / 2f * MID_SCALE
        val mh = (from.height + to.height) / 2f * MID_SCALE
        // Two linear segments between the keyframes, like WAAPI with the easing over the whole animation
        var x: Float
        var y: Float
        var w: Float
        var h: Float
        if (p < .5f) {
            val f = p / .5f
            x = Motion.lerp(ax, mx, f)
            y = Motion.lerp(ay, my, f)
            w = Motion.lerp(from.width, mw, f)
            h = Motion.lerp(from.height, mh, f)
        } else {
            val f = (p - .5f) / .5f
            x = Motion.lerp(mx, 0f, f)
            y = Motion.lerp(my, 0f, f)
            w = Motion.lerp(mw, to.width, f)
            h = Motion.lerp(mh, to.height, f)
        }
        val grow = abs(progress - p)
        if (grow > 0f) {
            x -= w * grow / 2f
            y -= h * grow / 2f
            w *= 1f + grow
            h *= 1f + grow
        }
        return Frame(Rect(x, y, x + w, y + h), Motion.lerp(FROM_CORNER, TO_CORNER, p))
    }

    /** The one scale at which a picture of [w] × [h] fills a frame of [frameW] × [frameH], cropped (`object-fit: cover`). */
    fun fillScale(frameW: Float, frameH: Float, w: Float, h: Float): Float = max(frameW / w, frameH / h)

    /** The retained dashboard art crossfades into portrait art around the midpoint of the same aspect-filled frame. */
    fun cardShown(progress: Float): Float = 1f - ((progress - .3f) / .4f).coerceIn(0f, 1f)

    /**
     * How far the title has flown (1: at its place in the header), [millis] into the opening or the closing. A closing starts from
     * [from], where the opening left it.
     */
    fun title(millis: Float, opening: Boolean, from: Float = 1f): Float =
        if (opening) settle(millis) else from * (1f - closeSpring(millis))

    /**
     * A logo does not fly (owner, 2026-11-20): the one the motion leaves (the dashboard's on opening, the header's on closing) fades out
     * where it is over the first [LOGO_OUT] of the flight, the one it goes to fades in at its own place over the last [LOGO_IN].
     */
    const val LOGO_OUT = .4f
    const val LOGO_IN = .5f

    /** The opacity of the logo the motion leaves, [millis] into it. */
    fun logoLeaving(millis: Float, opening: Boolean): Float =
        1f - Motion.progress(millis, 0, (flightMillis(opening) * LOGO_OUT).toInt(), Motion.Ease)

    /** The opacity of the logo the motion goes to, [millis] into it. */
    fun logoArriving(millis: Float, opening: Boolean): Float {
        val d = flightMillis(opening)
        return Motion.progress(millis, (d * (1f - LOGO_IN)).toInt(), (d * LOGO_IN).toInt(), Motion.Ease)
    }

    private fun flightMillis(opening: Boolean) = if (opening) FLIGHT_IN else FLIGHT_OUT

    /**
     * The page's rise: how far below its place it is, in stage pixels, and its opacity. A closing starts from [from], where the
     * opening left the page (stage pixels below its place).
     */
    fun page(millis: Float, opening: Boolean, reduced: Boolean, from: Float = 0f): Pair<Float, Float> = when {
        reduced -> 0f to if (opening) 1f else 1f - Motion.progress(millis, 0, Motion.EXIT, InOut)
        opening -> PAGE_RISE * (1f - settle(millis)) to 1f
        else -> {
            val p = Motion.progress(millis, 0, PAGE_OUT, InOut)
            Motion.lerp(from, PAGE_RISE, p) to 1f - p
        }
    }

    /** How far a part of the dashboard has gone away at [millis] of the home clock, [distance] covers from the focused one (the info row: 0). */
    fun homeAway(millis: Float, distance: Int, reduced: Boolean): Float =
        if (reduced) Motion.progress(millis, 0, HOME_OUT, EaseOut) else settle(millis - distance * HOME_STAGGER)

    /**
     * How far a part of the dashboard is still away [millis] into the closing, from [away] (where the opening left it): every part comes
     * back together, on the closing flight's spring (the iPad's choreography), so a closing that interrupts the opening turns the parts
     * back from where they are, and they land with the cover, settling as it does.
     */
    fun homeBack(millis: Float, away: Float, reduced: Boolean): Float =
        if (reduced) min(away, 1f - Motion.progress(millis, 0, Motion.FADE, InOut)) else away * (1f - closeSpring(millis))

    /** A cover [away] from the dashboard ([homeAway], [homeBack]), [distance] covers from the focused one: (rise, scale, opacity). */
    fun homeCover(away: Float, distance: Int, reduced: Boolean): Triple<Float, Float, Float> {
        val a = 1f - away.coerceIn(0f, 1f)
        if (reduced) return Triple(0f, 1f, a)
        return Triple(-(160f + distance * 30f) * away, Motion.lerp(1f, .92f, away), a)
    }

    /** The info row [away] from the dashboard: its rise and its opacity. */
    fun homeInfo(away: Float, reduced: Boolean): Pair<Float, Float> = (if (reduced) 0f else -90f * away) to 1f - away.coerceIn(0f, 1f)

    /** The home clock's length: the farthest cover has lifted away. */
    fun homeTotal(maxDistance: Int, reduced: Boolean): Int = HOME_OUT + if (reduced) 0 else max(0, maxDistance) * HOME_STAGGER

    /** Staggered part [index] of the header rising in ([delay]: [HEADER_DELAY] or [CUE_DELAY]): its offset below its place and its opacity. */
    fun rise(millis: Float, delay: Int, index: Int, reduced: Boolean): Pair<Float, Float> {
        val start = delay + min(index, Motion.STAGGER_CAP) * HEADER_STAGGER
        if (reduced) return 0f to Motion.progress(millis, start, Motion.FADE, Motion.Ease)
        val s = settle(millis - start)
        return Motion.RISE * (1f - s) to s.coerceIn(0f, 1f)
    }

    /** A staggered part leaving while the overview closes, from opacity [from] (where the opening left it): it fades and sinks a little. */
    fun leave(millis: Float, reduced: Boolean, from: Float = 1f): Pair<Float, Float> {
        val p = Motion.progress(millis, 0, LEAVE, Motion.In)
        return (if (reduced) 0f else Motion.RISE * .6f * p) to from * (1f - p)
    }

    /** The blurred hero's values while opening (from the dashboard's look to [BackdropDepth] at the top) or closing. */
    fun backdrop(millis: Float, opening: Boolean): Float =
        if (opening) Motion.progress(millis, 0, BACKDROP_IN, EaseOut) else 1f - Motion.progress(millis, 0, BACKDROP_OUT, EaseOut)

    /** A count-up [delay] ms after [millis] began: 0 to 1 with ease-out-quart. */
    fun countUp(millis: Float, delay: Int, reduced: Boolean): Float {
        if (reduced) return if (millis >= delay) 1f else 0f
        val k = ((millis - delay) / COUNT_UP).coerceIn(0f, 1f)
        return 1f - (1f - k).let { it * it * it * it }
    }

    /**
     * The score rings (owner, 2026-11-21): each arc fills on a slow, round spring of [RING_RESPONSE] s and [RING_DAMPING],
     * [RING_STAGGER] ms after the one before. Its one overshoot is softly capped at [RING_OVERSHOOT] of the target, and from its peak it
     * comes back without passing the target again (a critically damped return at the spring's own rate), so it never bounces twice.
     * The number does not overshoot: it counts up on a plain ease-out over [RING_COUNT] ([ringCount]).
     */
    const val RING_RESPONSE = 1f
    const val RING_DAMPING = .55f
    const val RING_OVERSHOOT = .08f
    const val RING_STAGGER = 90
    const val RING_FROM_SCALE = .92f
    const val RING_COUNT = 1100

    /** The arc is within 0.2 % of its score this long after it began: it rests there exactly from then on. */
    const val RING_SETTLE = 1600

    /** The age rating and the tags pop in on a gentle spring, from [CHIP_FROM_SCALE]. */
    const val CHIP_RESPONSE = .5f
    const val CHIP_DAMPING = .7f
    const val CHIP_FROM_SCALE = .9f

    /** The ratings' clock: both rings and the age rating ([RING_STAGGER] × 2 after the first) have landed. */
    const val RATINGS_TOTAL = 2 * RING_STAGGER + RING_SETTLE

    private val ringW = 2f * PI.toFloat() / RING_RESPONSE
    private val ringPeakMillis = PI.toFloat() / (ringW * sqrt(1f - RING_DAMPING * RING_DAMPING)) * 1000f
    private val ringPeak = spring(ringPeakMillis, RING_RESPONSE, RING_DAMPING) - 1f

    /** How full ring [index]'s arc is (of its score; up to 1 + [RING_OVERSHOOT]) [millis] after the ratings began. Reduced motion: full. */
    fun ring(millis: Float, index: Int, reduced: Boolean): Float {
        if (reduced) return 1f
        val t = millis - index * RING_STAGGER
        if (t >= RING_SETTLE) return 1f
        val over = if (t <= ringPeakMillis) spring(t, RING_RESPONSE, RING_DAMPING) - 1f
        else ((t - ringPeakMillis) / 1000f * ringW).let { ringPeak * (1f + it) * exp(-it) }
        return if (over <= 0f) 1f + over else 1f + RING_OVERSHOOT * tanh(over / RING_OVERSHOOT)
    }

    /** How far ring [index]'s number has counted (0…1, never past it) [millis] after the ratings began: a plain ease-out. */
    fun ringCount(millis: Float, index: Int, reduced: Boolean): Float =
        if (reduced) 1f else Motion.progress(millis - index * RING_STAGGER, 0, RING_COUNT, Motion.EaseOutCss)

    /** A ring's scale at fill [fill] ([ring]): it grows into place with its arc. */
    fun ringScale(fill: Float, reduced: Boolean): Float = if (reduced) 1f else Motion.lerp(RING_FROM_SCALE, 1f, fill)

    /** A ring's opacity [millis] after the ratings began: reduced motion fades it in, otherwise it is there from the start. */
    fun ringAlpha(millis: Float, reduced: Boolean): Float = if (reduced) Motion.progress(millis, 0, Motion.FADE, Motion.Ease) else 1f

    /** The number a ring of [score] shows at count [count] ([ringCount]). */
    fun ringNumber(score: Double, count: Float): Long = Math.round(score * count.coerceIn(0f, 1f))

    /** A chip's scale [millis] after its pop began. */
    fun chip(millis: Float, reduced: Boolean): Float =
        if (reduced) 1f else Motion.lerp(CHIP_FROM_SCALE, 1f, spring(millis, CHIP_RESPONSE, CHIP_DAMPING))

    /**
     * The focused cover lifted by a finger ([drag]): [lift] stage pixels (negative: up), its [scale] (the focus scale, grown a little),
     * its [alpha] and how far it is towards its cap ([progress], 0…1: the art's zoom and the cue's chevrons follow it).
     */
    data class CoverDrag(val lift: Float, val scale: Float, val alpha: Float, val progress: Float)

    /** A finger lifts the cover at most [DRAG_RATIO] of its travel (the rubber band's slope at the start), less and less towards the cap. */
    const val DRAG_RATIO = .6f

    /** The cover grows by [DRAG_GROW] and fades by [DRAG_FADE] at its cap (the fade begins halfway there). */
    const val DRAG_GROW = .03f
    const val DRAG_FADE = .3f

    /** The lifted cover stays this far under the row's top edge (its focus ring and glow), at most [DRAG_MAX] above its place. */
    const val DRAG_MARGIN = 14f
    const val DRAG_MAX = 120f

    /**
     * How far the cover may lift (stage pixels) when the row has [rowPad] above a cover [coverHeight] high: everything above the row's
     * edge would be cut (the row draws in an offscreen layer for its edge fades), so the cover, grown to its largest, stays under it.
     */
    fun dragCap(rowPad: Float, coverHeight: Float): Float =
        (rowPad - coverHeight * (HomeCompact.FOCUS_SCALE + DRAG_GROW - 1f) / 2f - DRAG_MARGIN).coerceIn(0f, DRAG_MAX)

    /** The cover dragged by a finger [finger] stage pixels (negative: up) under a lift of at most [cap] ([dragCap]): a rubber band. */
    fun drag(finger: Float, cap: Float): CoverDrag {
        val d = max(-finger, 0f)
        if (cap <= 0f || d == 0f) return CoverDrag(0f, HomeCompact.FOCUS_SCALE, 1f, 0f)
        val progress = 1f - exp(-DRAG_RATIO * d / cap)
        val fade = ((progress - .5f) / .5f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        return CoverDrag(-cap * progress, HomeCompact.FOCUS_SCALE + DRAG_GROW * progress, 1f - DRAG_FADE * fade, progress)
    }

    /** How fast the dragged cover moves per finger speed at [finger] ([drag]'s slope): [DRAG_RATIO] at first, then less. */
    fun dragSlope(finger: Float, cap: Float): Float = if (cap <= 0f) 0f else DRAG_RATIO * exp(-DRAG_RATIO * max(-finger, 0f) / cap)

    /** Inside the lifted cover the art zooms by [ART_ZOOM] and drifts [ART_PARALLAX] stage pixels down, against the lift (at its cap). */
    const val ART_ZOOM = .07f
    const val ART_PARALLAX = 12f

    /** The flying cover's opacity at [progress] of the opening when the card left at [fromAlpha] ([CoverDrag.alpha]): full by 30 %. */
    fun flightAlpha(progress: Float, fromAlpha: Float): Float = Motion.lerp(fromAlpha, 1f, (progress / .3f).coerceIn(0f, 1f))

    /** A finger that has dragged the cover up this far (stage pixels) opens the overview when it lets go. */
    const val DRAG_OPENS = 72f

    /** Pulling the page down at its top this far closes the overview when released. */
    const val PULL_CLOSES = 150f

    /** The page pulled down [pull] stage pixels moves [PULL_FOLLOW] of it; the cover shrinks about its top-left corner ([pulledScale]). */
    const val PULL_FOLLOW = .45f

    fun pulledScale(pull: Float): Float = 1f - pull.coerceIn(0f, 300f) / 2400f

    /** A finger's hand-off moves the flight at most this fast (progress per second). */
    const val MAX_HANDOFF = 6f

    /**
     * The flight's velocity (progress per second) that carries on a cover a finger let go of while it moved [velocityY] screen pixels
     * per second (positive: down), at the flight's [opening] end (progress 0, the card [from]) or its closing one (1, the slot [to]):
     * the speed of the frame's top along the flight's first or last segment ([cover]). Only motion along the way carries over.
     */
    fun flightVelocity(velocityY: Float, from: Rect, to: Rect, arcPx: Float, opening: Boolean): Float {
        val ay = from.top - to.top
        // d(top) / d(progress): from the card to the lifted midpoint, or from the midpoint into the slot
        val slope = -(ay + if (opening) 2f * arcPx else -2f * arcPx)
        if (abs(slope) < 1f) return 0f
        val v = velocityY / slope
        return if (opening) v.coerceIn(0f, MAX_HANDOFF) else v.coerceIn(-MAX_HANDOFF, 0f)
    }

    /** Where the closing flight leaves from when a finger let go of the cover shrunk to [scale] about its [slot]'s top-left corner. */
    fun releaseSlot(slot: Rect, scale: Float): Rect =
        if (scale == 1f) slot else Rect(slot.left, slot.top, slot.left + slot.width * scale, slot.top + slot.height * scale)

    /**
     * The card the closing flight heads for at [progress]: where the opening left ([from], where a finger had lifted it) at the
     * progress the closing began at ([start]), turning into the card at rest ([back]) on the way, so it lands where the card is.
     */
    fun closingFrom(progress: Float, start: Float, from: Rect, back: Rect): Rect {
        if (start <= 0f || from == back) return back
        val t = (progress / start).coerceIn(0f, 1f)
        return Rect(
            Motion.lerp(back.left, from.left, t), Motion.lerp(back.top, from.top, t),
            Motion.lerp(back.right, from.right, t), Motion.lerp(back.bottom, from.bottom, t),
        )
    }
}
