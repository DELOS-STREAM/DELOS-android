package com.limelight.shell.data

import android.content.res.Resources
import com.limelight.R
import java.text.NumberFormat
import java.util.Locale

/** The "Streaming time" amount: minutes under one hour, hours with one decimal from one hour up; empty (hidden) for zero. */
object PlaytimeFormat {
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE

    /** How long, as the unit and amount to show. [tenths] is hours times ten for [Unit.HOURS] and whole minutes for [Unit.MINUTES]. */
    data class Amount(val unit: Unit, val tenths: Long) { enum class Unit { MINUTES, HOURS } }

    /** Null for no time at all; under one minute counts as one minute so that any played app shows something. */
    fun amount(millis: Long): Amount? = when {
        millis <= 0 -> null
        millis < HOUR -> Amount(Amount.Unit.MINUTES, (millis / MINUTE).coerceAtLeast(1))
        else -> Amount(Amount.Unit.HOURS, (millis * 10 + HOUR / 2) / HOUR)
    }

    /** The amount split as the stat block shows it: [number] big ("20.4", "45") and [unit] small ("hours", "minutes", or "h", "min" when [short]). */
    data class Parts(val number: String, val unit: String)

    /** The amount of [millis] in the language of [res] and the decimal separator of [locale]; null when there is nothing to show. */
    fun parts(res: Resources, millis: Long, locale: Locale = Locale.getDefault(), short: Boolean = false): Parts? {
        val amount = amount(millis) ?: return null
        val count = amount.tenths.toInt()
        return when (amount.unit) {
            Amount.Unit.MINUTES -> Parts(
                NumberFormat.getIntegerInstance(locale).format(count),
                if (short) res.getString(R.string.playtime_unit_minutes_short) else res.getQuantityString(R.plurals.playtime_unit_minutes, count),
            )
            Amount.Unit.HOURS -> {
                val whole = amount.tenths % 10 == 0L
                val hours = (amount.tenths / 10).toInt()
                Parts(
                    if (whole) NumberFormat.getIntegerInstance(locale).format(hours) else decimal(amount.tenths, locale),
                    when {
                        short -> res.getString(R.string.playtime_unit_hours_short)
                        whole -> res.getQuantityString(R.plurals.playtime_unit_hours, hours)
                        else -> res.getString(R.string.playtime_unit_hours_fraction)
                    },
                )
            }
        }
    }

    /** [tenths] as a number with one decimal in [locale] ("204" gives "20.4" or "20,4"). */
    fun decimal(tenths: Long, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(tenths / 10.0)
}
