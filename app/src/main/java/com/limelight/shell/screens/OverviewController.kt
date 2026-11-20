package com.limelight.shell.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
 * The timeline of the game overview (docs/gui/GAME_INFO.md, "Game overview"), shared by the dashboard, which lifts its covers away and
 * hides the card that flies, and the overview, which flies the cover and the title in and rises its page. Every animated value is a
 * pure function of [clock] or [home] ([OverviewMotion]), read while drawing, so the motion never recomposes anything.
 *
 * Nothing delays a control: the overview takes input from its first frame while it is still entering. Closing during the opening waits
 * for it to finish, then reverses ([close]).
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

    /** The dashboard's covers lifting away, in milliseconds of their own timeline; it runs back to 0 while the overview closes. */
    var home = Animatable(0f)
        private set

    /** The card's rectangle on screen when the overview opened (root pixels, with its focus scale or where a finger dragged it). */
    var from: Rect? = null
        private set

    /** The dashboard's title (text or logo) when the overview opened; null when it was not laid out (the title then rises like the header). */
    var title: TitleSource? = null
        private set

    var reduced = false
        private set

    private var job: Job? = null

    /** The overview is up and nothing moves: the focus ring shows. */
    val settled: Boolean get() = app != null && !opening && !leaving

    /**
     * Opens [app]'s overview: the card at [from] flies into the header. [homeTotal] is how long the dashboard's covers take to lift
     * away ([OverviewMotion.homeTotal]). [instant]: everything in place at once (the overview is shown again after the app settings).
     */
    fun open(app: App, from: Rect?, title: TitleSource?, homeTotal: Int, reduced: Boolean, instant: Boolean = false) {
        if (this.app != null) return
        // Reset before publishing the session: a reopened page must never draw the previous closing clock.
        clock = Animatable(if (instant) openTotal(reduced).toFloat() else 0f)
        home = Animatable(if (instant) homeTotal.toFloat() else 0f)
        this.app = app
        this.from = from
        this.title = title
        this.reduced = reduced
        leaving = false
        closing = false
        opening = !instant
        job = scope.launch {
            if (instant) {
                clock.snapTo(openTotal(reduced).toFloat())
                home.snapTo(homeTotal.toFloat())
                return@launch
            }
            clock.snapTo(0f)
            launch { home.animateTo(homeTotal.toFloat(), tween(max(1, homeTotal - home.value.toInt()), easing = LinearEasing)) }
            val total = openTotal(reduced)
            clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
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
        job = scope.launch {
            opened?.join()
            opening = false
            prepare()
            clock.snapTo(0f)
            closing = true
            coroutineScope {
                launch { home.animateTo(0f, tween(if (reduced) Motion.FADE else minOf(OverviewMotion.HOME_OUT, home.value.toInt()).coerceAtLeast(1), easing = LinearEasing)) }
                val total = closeTotal(reduced)
                clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
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
        fun openTotal(reduced: Boolean): Int = if (reduced) max(Motion.FADE, OverviewMotion.CUE_DELAY + Motion.FADE) else OverviewMotion.OPEN_TOTAL

        fun closeTotal(reduced: Boolean): Int = if (reduced) Motion.FADE else maxOf(OverviewMotion.FLIGHT_OUT, OverviewMotion.PAGE_OUT, OverviewMotion.BACKDROP_OUT)
    }
}
