package com.limelight.shell.data

import android.app.Application
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LastStreamedTest {
    private val prague = ZoneId.of("Europe/Prague")
    private val now = ZonedDateTime.of(2026, 11, 14, 12, 0, 0, 0, prague).toInstant().toEpochMilli()

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0, zone: ZoneId = prague) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun relative(last: Long, nowMillis: Long = now, zone: ZoneId = prague) = LastStreamed.relative(last, nowMillis, zone)

    @Test fun nothingRecordedIsNever() {
        assertEquals(LastStreamed.Relative.Never, relative(0))
        assertEquals(LastStreamed.Relative.Never, relative(-5))
    }

    @Test fun todayAndYesterdayAreCalendarDays() {
        assertEquals(LastStreamed.Relative.Today, relative(at(2026, 11, 14, 0, 0)))
        assertEquals(LastStreamed.Relative.Today, relative(now))
        assertEquals(LastStreamed.Relative.Yesterday, relative(at(2026, 11, 13, 23, 59)))
        assertEquals(LastStreamed.Relative.Yesterday, relative(at(2026, 11, 13, 0, 0)))
    }

    @Test fun twoToSixDaysAreDays() {
        assertEquals(LastStreamed.Relative.Days(2), relative(at(2026, 11, 12, 23, 59)))
        assertEquals(LastStreamed.Relative.Days(6), relative(at(2026, 11, 8, 0, 1)))
    }

    @Test fun sevenDaysUpToFourWeeksAreWeeks() {
        assertEquals(LastStreamed.Relative.Weeks(1), relative(at(2026, 11, 7)))
        assertEquals(LastStreamed.Relative.Weeks(1), relative(at(2026, 11, 1)))
        assertEquals(LastStreamed.Relative.Weeks(2), relative(at(2026, 10, 31)))
        assertEquals(LastStreamed.Relative.Weeks(4), relative(at(2026, 10, 11)))
    }

    @Test fun olderThanFourWeeksIsADate() {
        assertEquals(LastStreamed.Relative.Date(LocalDate.of(2026, 10, 10)), relative(at(2026, 10, 10)))
        assertEquals(LastStreamed.Relative.Date(LocalDate.of(2025, 3, 2)), relative(at(2025, 3, 2, 23, 30)))
    }

    @Test fun aClockSetBackCountsAsToday() {
        assertEquals(LastStreamed.Relative.Today, relative(at(2026, 11, 20)))
    }

    @Test fun midnightFollowsTheTimeZoneOfTheDevice() {
        // 23:30 UTC on the 14th and 00:30 UTC on the 15th: the day before in UTC, the same day in Tokyo (08:30 and 09:30 on the 15th)
        val last = at(2026, 11, 14, 23, 30, ZoneId.of("UTC"))
        val later = at(2026, 11, 15, 0, 30, ZoneId.of("UTC"))
        assertEquals(LastStreamed.Relative.Yesterday, relative(last, later, ZoneId.of("UTC")))
        assertEquals(LastStreamed.Relative.Today, relative(last, later, ZoneId.of("Asia/Tokyo")))
        // And across a clock change (Prague moved to summer time at 02:00 on 29 March 2026): one hour apart, two calendar days
        assertEquals(LastStreamed.Relative.Yesterday, relative(at(2026, 3, 28, 23, 30), at(2026, 3, 29, 3, 30)))
    }

    private fun resources(): Resources = ApplicationProvider.getApplicationContext<Application>().resources

    private fun text(last: Long, locale: Locale) = LastStreamed.format(resources(), last, now, prague, locale)

    @Test fun english() {
        assertEquals("Today", text(at(2026, 11, 14, 8), Locale.ENGLISH))
        assertEquals("Yesterday", text(at(2026, 11, 13), Locale.ENGLISH))
        assertEquals("2 days ago", text(at(2026, 11, 12), Locale.ENGLISH))
        assertEquals("6 days ago", text(at(2026, 11, 8), Locale.ENGLISH))
        assertEquals("1 week ago", text(at(2026, 11, 7), Locale.ENGLISH))
        assertEquals("2 weeks ago", text(at(2026, 10, 31), Locale.ENGLISH))
        assertEquals("Sep 3, 2026", text(at(2026, 9, 3), Locale.US))
        assertEquals("Never", text(0, Locale.ENGLISH))
    }

    @Test @Config(sdk = [33], qualifiers = "de")
    fun theDateFollowsTheResourcesLocaleNotTheJvmDefault() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("03.09.2026", LastStreamed.format(resources(), at(2026, 9, 3), now, prague))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test @Config(sdk = [33], qualifiers = "cs")
    fun czech() {
        val cs = Locale("cs")
        assertEquals("Nikdy", text(0, cs))
        assertEquals("Dnes", text(at(2026, 11, 14, 8), cs))
        assertEquals("Včera", text(at(2026, 11, 13), cs))
        assertEquals("před 2 dny", text(at(2026, 11, 12), cs))
        assertEquals("před 5 dny", text(at(2026, 11, 9), cs))
        assertEquals("před 2 týdny", text(at(2026, 10, 31), cs))
        val date = text(at(2026, 9, 3), cs)!!
        assertTrue(date, date.contains("2026") && date.contains("3") && date.contains("9"))
    }

    @Test @Config(sdk = [33], qualifiers = "de")
    fun german() {
        val de = Locale.GERMAN
        assertEquals("Nie", text(0, de))
        assertEquals("Heute", text(at(2026, 11, 14, 8), de))
        assertEquals("Gestern", text(at(2026, 11, 13), de))
        assertEquals("vor 3 Tagen", text(at(2026, 11, 11), de))
        assertEquals("vor 4 Wochen", text(at(2026, 10, 11), de))
        assertEquals("03.09.2026", text(at(2026, 9, 3), de))
    }
}
