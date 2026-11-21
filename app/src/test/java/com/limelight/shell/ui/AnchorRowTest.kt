package com.limelight.shell.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AnchorRowTest {
    @Test fun `every step past the start glides the focused item to the anchor`() {
        // Items 546 px apart, the row scrolled so item 0 rests at the anchor: each step scrolls by one item, from the very first
        val offsets = mapOf(0 to 0, 1 to 546, 2 to 1092, 3 to 1638, 4 to 2184)
        assertEquals(AnchorRow.Step.Glide(546f), AnchorRow.step(1, offsets::get))
        assertEquals(AnchorRow.Step.Glide(2184f), AnchorRow.step(4, offsets::get))
    }

    @Test fun `stepping back glides the row back`() {
        val offsets = mapOf(2 to -546, 3 to 0, 4 to 546)
        assertEquals(AnchorRow.Step.Glide(-546f), AnchorRow.step(2, offsets::get))
    }

    @Test fun `an item at the anchor stays`() {
        assertEquals(AnchorRow.Step.Stay, AnchorRow.step(3, mapOf(3 to 0)::get))
    }

    @Test fun `an item not laid out is jumped to`() {
        assertEquals(AnchorRow.Step.Jump, AnchorRow.step(9, mapOf(0 to 0, 1 to 546)::get))
    }
}
