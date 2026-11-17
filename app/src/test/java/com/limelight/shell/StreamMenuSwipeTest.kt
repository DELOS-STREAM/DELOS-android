package com.limelight.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMenuSwipeTest {
    @Test fun zoneBoundsFor1080() {
        assertEquals(SwipeZone(885.6f, 1056f), StreamMenuSwipe.zone(1080, 0, 24))
        assertEquals(SwipeZone(885.6f, 1032f), StreamMenuSwipe.zone(1080, 48, 24))
        assertEquals(SwipeZone(885.6f, 930f), StreamMenuSwipe.zone(1080, 150, 24))
    }

    @Test fun minimumWinsOverSmallInset() {
        assertEquals(1056f, StreamMenuSwipe.zone(1080, 10, 24).bottom, 0f)
    }

    @Test fun tinyHeightsStayValid() {
        val z = StreamMenuSwipe.zone(20, 0, 24)
        assertEquals(0f, z.bottom, 0f)
        assertTrue(z.top <= z.bottom)
        assertFalse(StreamMenuSwipe.starts(z, 5f))
        assertFalse(StreamMenuSwipe.starts(StreamMenuSwipe.zone(0, 0, 24), 0f))
    }

    @Test fun startIsInclusiveAtTopExclusiveAtBottom() {
        val z = StreamMenuSwipe.zone(1080, 0, 24)
        assertTrue(StreamMenuSwipe.starts(z, z.top))
        assertFalse(StreamMenuSwipe.starts(z, z.top - 1f))
        assertTrue(StreamMenuSwipe.starts(z, z.bottom - 1f))
        assertFalse(StreamMenuSwipe.starts(z, z.bottom))
    }

    @Test fun theEdgeStripNoLongerStartsAndTheBandAboveItDoes() {
        val z = StreamMenuSwipe.zone(1080, 0, 24)
        // Where the old 6 % edge began (1015) a swipe still starts, but not in the last 24 px that Android's gesture bar takes
        assertTrue(StreamMenuSwipe.starts(z, 1080f * .94f))
        assertFalse(StreamMenuSwipe.starts(z, 1056f))
        assertFalse(StreamMenuSwipe.starts(z, 1079f))
    }

    @Test fun opensAtDistanceThreshold() {
        assertTrue(StreamMenuSwipe.opens(500f, 1000f, 500f, 1000f - 130f, 1080))
        assertFalse(StreamMenuSwipe.opens(500f, 1000f, 500f, 1000f - 129f, 1080))
        assertFalse(StreamMenuSwipe.opens(500f, 1000f, 500f, 1010f, 1080))
    }

    @Test fun mostlyHorizontalDragIsRejected() {
        assertFalse(StreamMenuSwipe.opens(100f, 1000f, 400f, 1000f - 200f, 1080))
        assertFalse(StreamMenuSwipe.opens(400f, 1000f, 100f, 1000f - 200f, 1080))
        assertFalse(StreamMenuSwipe.opens(100f, 1000f, 300f, 1000f - 200f, 1080))
        assertTrue(StreamMenuSwipe.opens(100f, 1000f, 150f, 1000f - 200f, 1080))
    }
}
