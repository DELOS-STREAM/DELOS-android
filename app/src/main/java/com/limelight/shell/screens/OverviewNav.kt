package com.limelight.shell.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect
import com.limelight.shell.ui.EaseOut
import com.limelight.shell.ui.Motion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
 * weighing 2.2 times as much, with a slight pull towards the same column or line. Left and right never leave a [NavTarget.locked]
 * row (the buttons, the carousel, the links, the similar games). Coming back the way one came returns to the element one came from
 * ([remember]), so Down then Up is always a round trip.
 */
internal class OverviewNavigator<K : Any> {
    private val back = HashMap<K, Pair<NavDirection, K>>()

    fun forget() = back.clear()

    /** The element [direction] of [from] among [targets], or null at the edge. */
    fun move(from: K, direction: NavDirection, targets: Map<K, NavTarget>): K? {
        val a = targets[from] ?: return null
        val memo = back[from]
        val best = if (memo != null && memo.first == direction && memo.second in targets) memo.second else nearest(from, a, direction, targets)
        if (best != null) back[best] = direction.opposite to from
        return best
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

    /** The carousel keeps this much room beside the focused item. */
    const val TRACK_MARGIN = 150f

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
 */
internal object OverviewMotion {
    /** `--ease-flight` and the closing `ease-in-out`. */
    val Flight = CubicBezierEasing(.2f, .9f, .25f, 1f)
    val InOut = CubicBezierEasing(.65f, 0f, .35f, 1f)

    /**
     * The cover's flight springs (owner, 2026-11-20): open response 0.55 s, damping 0.86; close response 0.45 s, damping 0.90.
     * Stiffness = (2π / response)²: 130.5 and 195.0. The frame rect, its corner and the art crossfade all read this one progress.
     */
    const val FLIGHT_OPEN_DAMPING = .86f
    const val FLIGHT_OPEN_STIFFNESS = 130.5f
    const val FLIGHT_CLOSE_DAMPING = .9f
    const val FLIGHT_CLOSE_STIFFNESS = 195f
    val FlightOpen = androidx.compose.animation.core.spring<Float>(FLIGHT_OPEN_DAMPING, FLIGHT_OPEN_STIFFNESS, visibilityThreshold = .0005f)
    val FlightClose = androidx.compose.animation.core.spring<Float>(FLIGHT_CLOSE_DAMPING, FLIGHT_CLOSE_STIFFNESS, visibilityThreshold = .0005f)

    const val FLIGHT_IN = 780
    const val FLIGHT_OUT = 640
    const val TITLE_IN = 720
    const val TITLE_OUT = 600
    const val PAGE_RISE = 120f
    const val PAGE_OUT = 560
    const val BACKDROP_IN = 900
    const val BACKDROP_OUT = 640
    const val RATINGS_DELAY = 380
    const val COUNT_UP = 1300

    /** The other covers lift away one after another: [HOME_OUT] each, [HOME_STAGGER] apart by distance from the focused one. */
    const val HOME_OUT = 620
    const val HOME_STAGGER = 45

    /** The header's parts rise after [HEADER_DELAY], the cue under it after [CUE_DELAY], [Motion.STAGGER_ROW] apart. */
    const val HEADER_DELAY = 260
    const val CUE_DELAY = 500

    /** A cover flies [ARC] stage pixels above the straight line at its midpoint, [MID_SCALE] times as big. */
    const val ARC = 30f
    const val MID_SCALE = 1.04f

    /** The cover's corner on the dashboard (26 grown by the focus scale) and in the header. */
    const val FROM_CORNER = 26f * 1.055f
    const val TO_CORNER = 30f

    /** How long the whole opening runs, so the last staggered part has risen. */
    val OPEN_TOTAL: Int get() = max(FLIGHT_IN, CUE_DELAY + 5 * Motion.STAGGER_ROW + Motion.ROW_IN)

    /** One frame of the cover in flight: the frame it shows, relative to the slot's top-left (screen pixels), and its corner (stage pixels). */
    data class Frame(val rect: Rect, val corner: Float)

    /**
     * The cover at [progress] (already eased) of its flight from [from] to [to] (both on screen): the keyframes of the prototype's
     * `Flight.cover`, start, a lifted and slightly grown midpoint, end, with the corner going from [FROM_CORNER] to [TO_CORNER].
     * Only the frame moves and changes its shape (a wide card's into the portrait slot): the picture in it keeps its proportions
     * ([fillScale]). (0, 0) and the slot's size is the cover at rest in its slot.
     */
    fun cover(progress: Float, from: Rect, to: Rect, arcPx: Float): Frame {
        val ax = from.left - to.left
        val ay = from.top - to.top
        val mx = ax * .5f
        val my = ay * .5f - arcPx
        val mw = (from.width + to.width) / 2f * MID_SCALE
        val mh = (from.height + to.height) / 2f * MID_SCALE
        // Two linear segments between the keyframes, like WAAPI with the easing over the whole animation
        val x: Float
        val y: Float
        val w: Float
        val h: Float
        if (progress < .5f) {
            val f = progress / .5f
            x = Motion.lerp(ax, mx, f)
            y = Motion.lerp(ay, my, f)
            w = Motion.lerp(from.width, mw, f)
            h = Motion.lerp(from.height, mh, f)
        } else {
            val f = (progress - .5f) / .5f
            x = Motion.lerp(mx, 0f, f)
            y = Motion.lerp(my, 0f, f)
            w = Motion.lerp(mw, to.width, f)
            h = Motion.lerp(mh, to.height, f)
        }
        // A spring may pass its target a little: the frame may, its corner never does
        return Frame(Rect(x, y, x + w, y + h), Motion.lerp(FROM_CORNER, TO_CORNER, progress.coerceIn(0f, 1f)))
    }

    /** The one scale at which a picture of [w] × [h] fills a frame of [frameW] × [frameH], cropped (`object-fit: cover`). */
    fun fillScale(frameW: Float, frameH: Float, w: Float, h: Float): Float = max(frameW / w, frameH / h)

    /** The retained dashboard art crossfades into portrait art around the midpoint of the same aspect-filled frame. */
    fun cardShown(progress: Float): Float = 1f - ((progress - .3f) / .4f).coerceIn(0f, 1f)

    /** How far the title has flown (1: at its place in the header). */
    fun title(millis: Float, opening: Boolean): Float =
        if (opening) Motion.progress(millis, 0, TITLE_IN, Flight) else 1f - Motion.progress(millis, 0, TITLE_OUT, InOut)

    /** The page's rise: how far below its place it is, in stage pixels, and its opacity. */
    fun page(millis: Float, opening: Boolean, reduced: Boolean): Pair<Float, Float> = when {
        opening -> (if (reduced) 0f else PAGE_RISE * (1f - Motion.progress(millis, 0, FLIGHT_IN, Flight))) to 1f
        else -> {
            val p = Motion.progress(millis, 0, if (reduced) Motion.EXIT else PAGE_OUT, InOut)
            (if (reduced) 0f else PAGE_RISE * p) to 1f - p
        }
    }

    /** A covers' lift away from the dashboard at [millis] of the home clock, [distance] covers from the focused one: (rise, scale, opacity). */
    fun homeCover(millis: Float, distance: Int, reduced: Boolean): Triple<Float, Float, Float> {
        val p = Motion.progress(millis, if (reduced) 0 else distance * HOME_STAGGER, HOME_OUT, EaseOut)
        if (reduced) return Triple(0f, 1f, 1f - p)
        return Triple(-(160f + distance * 30f) * p, Motion.lerp(1f, .92f, p), 1f - p)
    }

    /** The info row's rise out of the dashboard. */
    fun homeInfo(millis: Float, reduced: Boolean): Pair<Float, Float> {
        val p = Motion.progress(millis, 0, HOME_OUT, EaseOut)
        return (if (reduced) 0f else -90f * p) to 1f - p
    }

    /** The home clock's length: the farthest cover has lifted away. */
    fun homeTotal(maxDistance: Int, reduced: Boolean): Int = HOME_OUT + if (reduced) 0 else max(0, maxDistance) * HOME_STAGGER

    /** Staggered part [index] of the header rising in ([delay]: [HEADER_DELAY] or [CUE_DELAY]): its offset below its place and its opacity. */
    fun rise(millis: Float, delay: Int, index: Int, reduced: Boolean): Pair<Float, Float> {
        val start = delay + min(index, Motion.STAGGER_CAP) * Motion.STAGGER_ROW
        val p = Motion.progress(millis, start, Motion.ROW_IN, EaseOut)
        return Motion.rise(Motion.RISE, p, reduced) to p
    }

    /** A staggered part leaving while the overview closes: it fades and sinks a little. */
    fun leave(millis: Float, reduced: Boolean): Pair<Float, Float> {
        val p = Motion.progress(millis, 0, Motion.EXIT, Motion.In)
        return (if (reduced) 0f else Motion.RISE * .6f * p) to 1f - p
    }

    /** The blurred hero's values while opening (from the dashboard's look to [BackdropDepth] at the top) or closing. */
    fun backdrop(millis: Float, opening: Boolean): Float =
        if (opening) Motion.progress(millis, 0, BACKDROP_IN, EaseOut) else 1f - Motion.progress(millis, 0, BACKDROP_OUT, EaseOut)

    /** A count-up or a ring filling [delay] ms after [millis] began: 0 to 1 with ease-out-quart. */
    fun countUp(millis: Float, delay: Int, reduced: Boolean): Float {
        if (reduced) return if (millis >= delay) 1f else 0f
        val k = ((millis - delay) / COUNT_UP).coerceIn(0f, 1f)
        return 1f - (1f - k).let { it * it * it * it }
    }

    /** The cover dragged up by a finger [dy] stage pixels (negative: up): its lift with resistance and its grown scale. */
    fun drag(dy: Float): Pair<Float, Float> {
        val lift = max(min(dy, 0f), -260f) * .55f
        return lift to 1.055f + min(-lift, 140f) / 1400f
    }

    /** A drag that lifted the cover this far opens the overview when released. */
    const val DRAG_OPENS = -40f

    /** Pulling the page down at its top this far closes the overview when released. */
    const val PULL_CLOSES = 150f
}
