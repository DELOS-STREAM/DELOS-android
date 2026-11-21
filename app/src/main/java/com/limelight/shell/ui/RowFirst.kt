package com.limelight.shell.ui

import kotlin.math.abs
import kotlin.math.max

/**
 * The focusable elements one Up or Down move chooses from, read by index (reused between moves, no copies): each one's rectangle in
 * one coordinate space, whether its left edge is on screen (not scrolled out of a horizontal row) and whether it sits in a 2-D grid.
 */
class RowCandidates {
    var size = 0
        private set
    private var left = FloatArray(32)
    private var top = FloatArray(32)
    private var right = FloatArray(32)
    private var bottom = FloatArray(32)
    private var onScreen = BooleanArray(32)
    private var grid = BooleanArray(32)

    fun clear() {
        size = 0
    }

    /** Adds an element and returns its index. */
    fun add(left: Float, top: Float, right: Float, bottom: Float, onScreen: Boolean = true, grid: Boolean = false): Int {
        if (size == this.left.size) {
            val n = size * 2
            this.left = this.left.copyOf(n)
            this.top = this.top.copyOf(n)
            this.right = this.right.copyOf(n)
            this.bottom = this.bottom.copyOf(n)
            this.onScreen = this.onScreen.copyOf(n)
            this.grid = this.grid.copyOf(n)
        }
        this.left[size] = left
        this.top[size] = top
        this.right[size] = right
        this.bottom[size] = bottom
        this.onScreen[size] = onScreen
        this.grid[size] = grid
        return size++
    }

    fun left(i: Int) = left[i]
    fun top(i: Int) = top[i]
    fun right(i: Int) = right[i]
    fun bottom(i: Int) = bottom[i]
    fun onScreen(i: Int) = onScreen[i]
    fun grid(i: Int) = grid[i]
    internal fun centerX(i: Int) = (left[i] + right[i]) / 2f
    internal fun centerY(i: Int) = (top[i] + bottom[i]) / 2f
}

/**
 * The shell's row-first rule (docs/GUI.md): Up or Down, anywhere, lands on the FIRST element of the row it reaches, not on the
 * horizontally nearest one. The move first finds the element it would reach today, the [nearest] (the "hit"); the row is every
 * element in that direction whose vertical centre lies within the hit's top..bottom; the target is that row's leftmost element whose
 * left edge is on screen ([resolve]). Exceptions: a row scrolled sideways starts at its leftmost element ON SCREEN, not its item 0;
 * going back the way one came returns to the element one left (the remembered one, when it is in the reached row); a 2-D grid
 * (artwork picker, avatars, letter picker) keeps its column: a hit in a grid is the target, and grid elements never join a row.
 */
object RowFirst {
    /** The target of a move [down] or up from the rectangle l/t/r/b among [c]: an index into [c], or -1 when nothing lies that way. */
    fun target(c: RowCandidates, l: Float, t: Float, r: Float, b: Float, down: Boolean, remembered: Int = -1): Int =
        resolve(c, nearest(c, l, t, r, b, down), l, t, r, b, down, remembered)

    /**
     * The hit: what Compose's own 2-D focus search reaches from l/t/r/b (its candidate test, beam preference and the weighted distance
     * 13·major² + minor²), so the row is the one a move reaches today. -1 when nothing lies that way.
     */
    fun nearest(c: RowCandidates, l: Float, t: Float, r: Float, b: Float, down: Boolean): Int {
        var best = -1
        for (i in 0 until c.size) {
            if (!candidate(c, i, t, b, down)) continue
            if (best < 0 || better(c, i, best, l, t, r, b, down)) best = i
        }
        return best
    }

    /**
     * The row-first target for the [hit] of a move from l/t/r/b: the hit itself in a grid; else the [remembered] element (the one the
     * last vertical move left, coming back the other way) when it is in the hit's row; else the row's leftmost element on screen (ties:
     * the one nearest the hit's centre line, then the lower index); the hit when none of them is on screen.
     */
    fun resolve(c: RowCandidates, hit: Int, l: Float, t: Float, r: Float, b: Float, down: Boolean, remembered: Int = -1): Int {
        if (hit < 0 || c.grid(hit)) return hit
        val top = c.top(hit)
        val bottom = c.bottom(hit)
        fun inRow(i: Int) = !c.grid(i) && c.centerY(i) in top..bottom && candidate(c, i, t, b, down)
        if (remembered >= 0 && remembered < c.size && inRow(remembered)) return remembered
        val line = c.centerY(hit)
        var best = -1
        for (i in 0 until c.size) {
            if (!c.onScreen(i) || !inRow(i)) continue
            if (best < 0 || c.left(i) < c.left(best) || (c.left(i) == c.left(best) && abs(c.centerY(i) - line) < abs(c.centerY(best) - line))) best = i
        }
        return if (best >= 0) best else hit
    }

    /** Compose's candidate test: [i] lies below (or above) the source's top..bottom. */
    private fun candidate(c: RowCandidates, i: Int, t: Float, b: Float, down: Boolean): Boolean =
        if (down) (t < c.top(i) || b <= c.top(i)) && b < c.bottom(i)
        else (b > c.bottom(i) || t >= c.bottom(i)) && t > c.top(i)

    private fun better(c: RowCandidates, i: Int, j: Int, l: Float, t: Float, r: Float, b: Float, down: Boolean): Boolean = when {
        beamBeats(c, i, j, l, t, r, b, down) -> true
        beamBeats(c, j, i, l, t, r, b, down) -> false
        else -> weighted(c, i, l, t, r, b, down) < weighted(c, j, l, t, r, b, down)
    }

    /** Compose's beamBeats for a vertical move: [i] overlaps the source's column and [j] does not, and [j] is not clearly nearer. */
    private fun beamBeats(c: RowCandidates, i: Int, j: Int, l: Float, t: Float, r: Float, b: Float, down: Boolean): Boolean {
        fun inBeam(k: Int) = c.right(k) > l && c.left(k) < r
        if (inBeam(j) || !inBeam(i)) return false
        val jAhead = if (down) b <= c.top(j) else t >= c.bottom(j)
        if (!jAhead) return true
        val major = max(0f, if (down) c.top(i) - b else t - c.bottom(i))
        val jFar = max(1f, if (down) c.bottom(j) - b else t - c.top(j))
        return major < jFar
    }

    private fun weighted(c: RowCandidates, i: Int, l: Float, t: Float, r: Float, b: Float, down: Boolean): Long {
        val major = abs(max(0f, if (down) c.top(i) - b else t - c.bottom(i))).toLong()
        val minor = abs((l + r) / 2f - c.centerX(i)).toLong()
        return 13 * major * major + minor * minor
    }
}
