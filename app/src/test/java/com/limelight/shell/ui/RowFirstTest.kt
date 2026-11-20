package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** The row-first rule of Up and Down (docs/GUI.md): the nearest row in the direction, then its first element on screen. */
class RowFirstTest {
    private class Page {
        val c = RowCandidates()
        val names = ArrayList<String>()

        fun add(name: String, x: Float, y: Float, w: Float, h: Float, onScreen: Boolean = true, grid: Boolean = false) {
            c.add(x, y, x + w, y + h, onScreen, grid)
            names.add(name)
        }

        fun index(name: String) = names.indexOf(name)

        /** The target's name of a move from x/y/w/h, or null when there is none. */
        fun move(x: Float, y: Float, w: Float, h: Float, down: Boolean, remembered: String? = null): String? {
            val i = RowFirst.target(c, x, y, x + w, y + h, down, remembered?.let(::index) ?: -1)
            return if (i < 0) null else names[i]
        }
    }

    /** Three rows of three 200 × 100 elements, 100 px apart. */
    private fun grid3() = Page().apply {
        for ((row, y) in listOf("a" to 0f, "b" to 200f, "c" to 400f)) {
            add("${row}0", 0f, y, 200f, 100f)
            add("${row}1", 300f, y, 200f, 100f)
            add("${row}2", 600f, y, 200f, 100f)
        }
    }

    /** [grid3] without the element [name] (the source is never its own candidate). */
    private fun grid3Without(name: String) = Page().apply {
        val all = grid3()
        for (i in 0 until all.c.size) {
            if (all.names[i] == name) continue
            add(all.names[i], all.c.left(i), all.c.top(i), all.c.right(i) - all.c.left(i), all.c.bottom(i) - all.c.top(i))
        }
    }

    @Test fun `down from the end of a row lands on the first element of the next row`() {
        assertEquals("b0", grid3Without("a2").move(600f, 0f, 200f, 100f, down = true))
        assertEquals("b0", grid3Without("a1").move(300f, 0f, 200f, 100f, down = true))
    }

    @Test fun `up lands on the first element of the row above`() {
        assertEquals("b0", grid3Without("c2").move(600f, 400f, 200f, 100f, down = false))
        assertEquals("a0", grid3Without("b1").move(300f, 200f, 200f, 100f, down = false))
    }

    @Test fun `a move reaches only the nearest row, never one further`() {
        val page = grid3Without("a2").apply { add("far", -100f, 400f, 50f, 100f) }
        assertEquals("b0", page.move(600f, 0f, 200f, 100f, down = true))
    }

    @Test fun `the row is every element whose centre lies within the hit's height`() {
        val page = Page().apply {
            add("hit", 600f, 200f, 200f, 100f)
            add("chip", 100f, 230f, 80f, 40f) // centre 250: in the row
            add("low", 0f, 310f, 80f, 80f) // centre 350: below the hit's bottom, another row
        }
        assertEquals("chip", page.move(600f, 0f, 200f, 100f, down = true))
    }

    @Test fun `equally far left the element nearer the hit's centre line wins`() {
        val page = Page().apply {
            add("hit", 600f, 200f, 200f, 100f)
            add("upper", 0f, 200f, 100f, 30f) // centre 215
            add("lower", 0f, 240f, 100f, 30f) // centre 255
        }
        assertEquals("lower", page.move(600f, 0f, 200f, 100f, down = true))
    }

    @Test fun `a row scrolled sideways starts at its leftmost element on screen`() {
        val page = Page().apply {
            add("b0", -150f, 200f, 200f, 100f, onScreen = false)
            add("b1", 100f, 200f, 200f, 100f)
            add("b2", 400f, 200f, 200f, 100f)
        }
        assertEquals("b1", page.move(400f, 0f, 200f, 100f, down = true))
    }

    @Test fun `a row with no element on screen keeps the hit`() {
        val page = Page().apply {
            add("b0", -450f, 200f, 200f, 100f, onScreen = false)
            add("b1", -150f, 200f, 200f, 100f, onScreen = false)
            add("b2", 400f, 200f, 200f, 100f, onScreen = false)
        }
        assertEquals("b2", page.move(400f, 0f, 200f, 100f, down = true))
    }

    @Test fun `nothing in the direction is no target`() {
        assertEquals(null, grid3Without("c2").move(600f, 400f, 200f, 100f, down = true))
        assertEquals(null, grid3Without("a0").move(0f, 0f, 200f, 100f, down = false))
        assertEquals(null, Page().move(0f, 0f, 100f, 100f, down = true))
    }

    @Test fun `elements of the source's own row never count, even beside a tall hit`() {
        val page = Page().apply {
            add("sibling", 0f, 100f, 200f, 100f) // the source's row: centre 150 lies within the card's height
            add("card", 900f, 150f, 100f, 450f)
        }
        assertEquals("card", page.move(600f, 100f, 200f, 100f, down = true))
    }

    @Test fun `going back the other way lands on the element the last move left`() {
        assertEquals("b2", grid3Without("a1").move(300f, 0f, 200f, 100f, down = true, remembered = "b2"))
        // Remembered from another row: the rule as usual
        assertEquals("b0", grid3Without("a1").move(300f, 0f, 200f, 100f, down = true, remembered = "c2"))
    }

    @Test fun `a grid keeps its column and never joins a row`() {
        val page = Page().apply {
            add("g0", 0f, 200f, 100f, 100f, grid = true)
            add("g1", 150f, 200f, 100f, 100f, grid = true)
            add("g2", 300f, 200f, 100f, 100f, grid = true)
        }
        assertEquals("g1", page.move(150f, 0f, 100f, 100f, down = true))
        val beside = Page().apply {
            add("g0", 0f, 200f, 100f, 100f, grid = true)
            add("button", 800f, 200f, 200f, 100f)
        }
        assertEquals("button", beside.move(900f, 0f, 100f, 100f, down = true))
    }

    @Test fun `leaving a grid lands on the first element of the row below it`() {
        val page = Page().apply {
            add("g0", 0f, 0f, 100f, 100f, grid = true)
            add("p0", 0f, 200f, 200f, 80f)
            add("p1", 300f, 200f, 200f, 80f)
        }
        assertEquals("p0", page.move(300f, 0f, 100f, 100f, down = true))
    }

    @Test fun `the hit is the element Compose's search reaches`() {
        // In the source's column beats one beside it that is not clearly nearer (Compose's beam rule)
        val page = Page().apply {
            add("beside", 0f, 250f, 100f, 100f)
            add("below", 600f, 300f, 200f, 100f)
        }
        assertEquals(page.index("below"), RowFirst.nearest(page.c, 600f, 0f, 800f, 100f, down = true))
    }
}
