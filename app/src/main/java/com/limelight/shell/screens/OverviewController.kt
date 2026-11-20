package com.limelight.shell.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import com.limelight.shell.data.App
import com.limelight.shell.ui.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

/** Where the dashboard's title was when the overview opened: its box on screen (root pixels) and, for text, its font size in stage pixels (null: a logo). */
internal data class TitleSource(val rect: Rect, val fontSize: Float?)

/**
 * A frame clock whose time advances by at most [maxStepNanos] from one frame to the next. The overview's first frames are its heaviest
 * (the whole page composes, its images and glass are set up): on the real frame clock the opening's animations, started with the first
 * of them, found most of their time gone by the next drawn frame (measured on a 1920 × 1080 emulator: the cover's spring was at 0.98
 * and the timeline at 367 ms in the first frame that moved). On this clock a long frame only holds the motion back; every step is drawn.
 */
internal class SteadyFrameClock(
    private val parent: MonotonicFrameClock,
    private val maxStepNanos: Long = MAX_STEP_NANOS,
) : MonotonicFrameClock {
    private var lastFrame = Long.MIN_VALUE
    private var time = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R = parent.withFrameNanos { frame ->
        // Every awaiter of one frame gets the same time: the animations of the timeline stay in step
        if (lastFrame == Long.MIN_VALUE) time = frame
        else if (frame != lastFrame) time += (frame - lastFrame).coerceIn(0L, maxStepNanos)
        lastFrame = frame
        onFrame(time)
    }

    companion object {
        /** Two frames at 60 Hz: real time at 30 fps and above. */
        const val MAX_STEP_NANOS = 33_333_333L
    }
}

/**
 * The timeline of the game overview (docs/gui/GAME_INFO.md, "Game overview"), shared by the dashboard, which lifts its covers away and
 * hides the card that flies, and the overview, which flies the cover and the title in and rises its page. Every animated value is a
 * pure function of [clock] or [home] ([OverviewMotion]), read while drawing, so the motion never recomposes anything.
 *
 * Nothing delays a control: the overview takes input from its first frame while it is still entering. Closing during the opening waits
 * for it to finish, then reverses ([close]). Both run on a [SteadyFrameClock], so a slow frame never skips a part of the motion.
 */
@Stable
internal class OverviewController(private val scope: CoroutineScope) {
    /** The game whose overview is shown (also while it closes); null on the dashboard. */
    var app by mutableStateOf<App?>(null)
        private set

    /** The opening is still running. */
    var opening by mutableStateOf(false)
        private set

    /** Back was pressed: the overview is on its way out (the ring hides at once, the motion reverses once the opening is over). */
    var leaving by mutableStateOf(false)
        private set

    /** The reversed motion runs ([clock] counts the closing). */
    var closing by mutableStateOf(false)
        private set

    /** Milliseconds since the opening or the closing began. */
    var clock = Animatable(0f)
        private set

    /**
     * How far the cover has flown (0: the card on the dashboard, 1: the header's slot), driven by a spring ([OverviewMotion.FlightOpen],
     * [OverviewMotion.FlightClose]) rather than [clock]: a close during the opening reverses it from its current position and velocity.
     */
    var flight = Animatable(0f)
        private set

    /** The dashboard's covers lifting away, in milliseconds of their own timeline; the closing brings them back from where it stopped ([homeAway]). */
    var home = Animatable(0f)
        private set

    /**
     * The overview's cover knows its slot and draws the flight: the dashboard's card hides from then on. Until then (the overview's
     * first frame lays its page out) the card stays, so the cover never shows anywhere but on its way from the card.
     */
    var coverPlaced by mutableStateOf(false)
        private set

    /** The card's rectangle on screen when the overview opened (root pixels, with its focus scale or where a finger dragged it). */
    var from: Rect? = null
        private set

    /** The dashboard's title (text or logo) when the overview opened; null when it was not laid out (the title then rises like the header). */
    var title: TitleSource? = null
        private set

    var reduced = false
        private set

    /** Milliseconds into the opening when a closing interrupted it ([Float.MAX_VALUE]: the opening had finished); read while drawing. */
    var interruptedAt = Float.MAX_VALUE
        private set

    private var job: Job? = null

    /** A motion of the overview, on its own [SteadyFrameClock] over the composition's frame clock. */
    private fun motion(block: suspend CoroutineScope.() -> Unit): Job =
        scope.launch(SteadyFrameClock(realClock()), block = block)

    private fun realClock(): MonotonicFrameClock = checkNotNull(scope.coroutineContext[MonotonicFrameClock])

    /**
     * Debug builds: the pacing of the real frames while [label] runs, logged under `OverviewMotion` when it is cancelled (frames,
     * median, 90th percentile and longest interval, frames that missed a 60 Hz vsync). Null in a release build.
     */
    private fun CoroutineScope.logFrames(label: String): Job? {
        if (!com.limelight.BuildConfig.DEBUG) return null
        val clock = realClock()
        return launch(clock) {
            val deltas = FloatArray(512)
            var n = 0
            var last = 0L
            try {
                while (true) clock.withFrameNanos { t ->
                    if (last != 0L && n < deltas.size) deltas[n++] = (t - last) / 1e6f
                    last = t
                }
            } finally {
                if (n > 0) {
                    val d = deltas.copyOf(n).apply { sort() }
                    android.util.Log.d(
                        "OverviewMotion",
                        "$label frames=${n + 1} ms=%.0f p50=%.1f p90=%.1f max=%.1f missed=%d".format(
                            d.sum(), d[n / 2], d[(n * 9) / 10], d[n - 1], d.count { it > 25f },
                        ),
                    )
                }
            }
        }
    }

    /** The overview is up and nothing moves: the focus ring shows. */
    val settled: Boolean get() = app != null && !opening && !leaving

    /** The header's cover has its slot (see [coverPlaced]). */
    fun placeCover() {
        coverPlaced = true
    }

    /** How far the dashboard's part [distance] covers from the focused one (the info row: 0) has gone (0: in place, 1: gone). */
    fun homeAway(distance: Int): Float {
        val away = OverviewMotion.homeAway(home.value, distance, reduced)
        return if (closing) OverviewMotion.homeBack(clock.value, away, reduced) else away
    }

    /**
     * Opens [app]'s overview: the card at [from] flies into the header. [homeTotal] is how long the dashboard's covers take to lift
     * away ([OverviewMotion.homeTotal]). [instant]: everything in place at once (the overview is shown again after the app settings).
     */
    fun open(app: App, from: Rect?, title: TitleSource?, homeTotal: Int, reduced: Boolean, instant: Boolean = false) {
        if (this.app != null) return
        // Reset before publishing the session: a reopened page must never draw the previous closing clock.
        clock = Animatable(if (instant) openTotal(reduced).toFloat() else 0f)
        home = Animatable(if (instant) homeTotal.toFloat() else 0f)
        flight = Animatable(if (instant) 1f else 0f)
        this.app = app
        this.from = from
        this.title = title
        this.reduced = reduced
        leaving = false
        closing = false
        opening = !instant
        coverPlaced = instant
        interruptedAt = Float.MAX_VALUE
        job = motion {
            if (instant) {
                clock.snapTo(openTotal(reduced).toFloat())
                home.snapTo(homeTotal.toFloat())
                return@motion
            }
            clock.snapTo(0f)
            val log = logFrames("open")
            launch { home.animateTo(homeTotal.toFloat(), tween(max(1, homeTotal - home.value.toInt()), easing = LinearEasing)) }
            val cover = launch { flight.animateTo(1f, if (reduced) tween(Motion.FADE) else OverviewMotion.FlightOpen) }
            val total = openTotal(reduced)
            clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
            cover.join()
            log?.cancel()
            opening = false
        }
    }

    /**
     * Closes the overview: waits for the opening, runs [prepare] (the dashboard takes back the focus and the hints, the page scrolls
     * back to its top), then reverses every motion; the overview leaves composition once the card is back in its row.
     */
    fun close(prepare: suspend () -> Unit) {
        if (app == null || leaving) return
        leaving = true
        val opened = job
        job = motion {
            // Interruptible: the opening stops where it is; the cover's spring turns back from its current position and velocity, the
            // other parts leave from where the opening left them ([interruptedAt])
            val velocity = flight.velocity
            if (opening) interruptedAt = clock.value
            opened?.cancel()
            opening = false
            val before = System.nanoTime()
            prepare()
            // The page had to scroll back to its top first: the cover stood still meanwhile
            val turn = if (System.nanoTime() - before < SteadyFrameClock.MAX_STEP_NANOS) velocity else 0f
            clock.snapTo(0f)
            closing = true
            coroutineScope {
                val log = logFrames("close")
                launch { flight.animateTo(0f, if (reduced) tween(Motion.FADE) else OverviewMotion.FlightClose, initialVelocity = turn) }
                val total = closeTotal(reduced)
                clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
                log?.cancel()
            }
            // The last frame of the motion is drawn first; taking the whole page out of composition (the heaviest frame of the
            // closing) happens in the next one, when nothing moves any more
            withFrameNanos { }
            app = null
            closing = false
            leaving = false
        }
    }

    /** Milliseconds since the opening began, or [Float.MAX_VALUE] once it is over (for the parts that only ever enter). */
    fun openMillis(): Float = if (closing) Float.MAX_VALUE else clock.value

    companion object {
        /** Reduced motion: the cue's fade (the last part to enter) has ended. */
        fun openTotal(reduced: Boolean): Int =
            if (reduced) OverviewMotion.CUE_DELAY + 5 * OverviewMotion.HEADER_STAGGER + Motion.FADE else OverviewMotion.OPEN_TOTAL

        fun closeTotal(reduced: Boolean): Int = if (reduced) Motion.FADE else maxOf(OverviewMotion.FLIGHT_OUT, OverviewMotion.PAGE_OUT, OverviewMotion.BACKDROP_OUT)
    }
}
