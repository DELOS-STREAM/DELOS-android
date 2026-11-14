package com.limelight.shell.data

import android.content.res.Resources
import com.limelight.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The "Last streamed" text of the stat block: Never, Today, Yesterday, N days ago (2 to 6), N weeks ago (1 to 4), then the date. Days are calendar
 * days of the device's time zone, so a stream at 23:50 is "Yesterday" at 00:10; a clock set back (a time in the future) counts as today.
 */
object LastStreamed {
    sealed interface Relative {
        /** Nothing was recorded. */
        data object Never : Relative
        data object Today : Relative
        data object Yesterday : Relative
        data class Days(val count: Int) : Relative
        data class Weeks(val count: Int) : Relative
        data class Date(val date: LocalDate) : Relative
    }

    private const val DAYS_IN_WEEK = 7
    private const val LAST_WEEKS = 4

    /** [Relative.Never] when nothing was recorded ([lastMillis] zero or less). */
    fun relative(lastMillis: Long, nowMillis: Long, zone: ZoneId): Relative {
        if (lastMillis <= 0) return Relative.Never
        val last = Instant.ofEpochMilli(lastMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(last, today)
        return when {
            days <= 0 -> Relative.Today
            days == 1L -> Relative.Yesterday
            days < DAYS_IN_WEEK -> Relative.Days(days.toInt())
            days / DAYS_IN_WEEK <= LAST_WEEKS -> Relative.Weeks((days / DAYS_IN_WEEK).toInt())
            else -> Relative.Date(last)
        }
    }

    /** [date] as the locale writes a medium date (month, day and year). */
    fun date(date: LocalDate, locale: Locale): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)

    /** The text in the language of [res]. */
    fun format(
        res: Resources,
        lastMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String = when (val relative = relative(lastMillis, nowMillis, zone)) {
        Relative.Never -> res.getString(R.string.playtime_never)
        Relative.Today -> res.getString(R.string.playtime_last_today)
        Relative.Yesterday -> res.getString(R.string.playtime_last_yesterday)
        is Relative.Days -> res.getQuantityString(R.plurals.playtime_last_days, relative.count, relative.count)
        is Relative.Weeks -> res.getQuantityString(R.plurals.playtime_last_weeks, relative.count, relative.count)
        is Relative.Date -> date(relative.date, locale)
    }
}
