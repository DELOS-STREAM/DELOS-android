package com.limelight.shell.data

import android.content.res.Resources
import com.limelight.R
import java.text.NumberFormat
import java.util.Locale

/** The "Streaming time" text: minutes under one hour, hours with one decimal from one hour up; empty (hidden) for zero. */
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

    /** "Streaming time: 20.4 hours" in the language of [res] and the decimal separator of [locale]; null when there is nothing to show. */
    fun format(res: Resources, millis: Long, locale: Locale = Locale.getDefault()): String? {
        val amount = amount(millis) ?: return null
        val text = when (amount.unit) {
            Amount.Unit.MINUTES -> res.getQuantityString(R.plurals.playtime_minutes, amount.tenths.toInt(), amount.tenths.toInt())
            Amount.Unit.HOURS ->
                if (amount.tenths % 10 == 0L) (amount.tenths / 10).toInt().let { res.getQuantityString(R.plurals.playtime_hours, it, it) }
                else res.getString(R.string.playtime_hours_fraction, decimal(amount.tenths, locale))
        }
        return res.getString(R.string.playtime_label, text)
    }

    /** [tenths] as a number with one decimal in [locale] ("204" gives "20.4" or "20,4"). */
    fun decimal(tenths: Long, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(tenths / 10.0)
}
