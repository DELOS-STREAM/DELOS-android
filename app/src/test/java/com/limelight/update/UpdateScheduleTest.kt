package com.limelight.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateScheduleTest {
    private val hour = 3_600_000L
    private val day = 24 * hour

    @Test fun asksAtMostOnceInTwentyFourHours() {
        val last = 1_000_000_000L
        assertTrue(UpdateSchedule.checkDue(last, 0))
        assertFalse(UpdateSchedule.checkDue(last + 23 * hour, last))
        assertTrue(UpdateSchedule.checkDue(last + day, last))
        assertTrue(UpdateSchedule.checkDue(last + 5 * day, last))
    }

    @Test fun aClockSetBackDoesNotBlockTheCheckForever() {
        assertTrue(UpdateSchedule.checkDue(1_000L, 9_000_000_000L))
    }

    @Test fun laterHidesTheSameVersionForThreeDays() {
        val now = 5_000_000_000L
        val until = UpdateSchedule.laterUntil(now)
        assertTrue(UpdateSchedule.hidden(now + 2 * day, "v1.1.0", "v1.1.0", until))
        assertFalse(UpdateSchedule.hidden(now + 3 * day, "v1.1.0", "v1.1.0", until))
    }

    @Test fun aNewerVersionShowsAtOnce() {
        val now = 5_000_000_000L
        assertFalse(UpdateSchedule.hidden(now + hour, "v1.2.0", "v1.1.0", UpdateSchedule.laterUntil(now)))
        assertFalse(UpdateSchedule.hidden(now, "v1.2.0", null, 0))
    }

    @Test fun aLaterFarInTheFutureBecauseOfAClockChangeDoesNotHide() {
        assertFalse(UpdateSchedule.hidden(1_000L, "v1.1.0", "v1.1.0", 1_000L + 400 * day))
    }
}
