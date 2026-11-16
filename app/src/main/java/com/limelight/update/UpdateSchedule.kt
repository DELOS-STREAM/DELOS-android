package com.limelight.update

/** When the updater asks and when it speaks (docs/UPDATES.md "Schedule"); pure, the clock is passed in. */
object UpdateSchedule {
    /** The background check runs at most once in this time. */
    const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

    /** "Later" hides the offer of one version for this time. */
    const val LATER_MS = 3L * 24 * 60 * 60 * 1000

    /** Whether GitHub is asked now: never asked, [CHECK_INTERVAL_MS] has passed, or the clock was set back before the last check. */
    fun checkDue(now: Long, lastCheck: Long): Boolean = lastCheck <= 0 || now < lastCheck || now - lastCheck >= CHECK_INTERVAL_MS

    /** The time until which "Later" hides the offer pressed at [now]. */
    fun laterUntil(now: Long): Long = now + LATER_MS

    /**
     * Whether the offer of [tag] stays hidden: "Later" was pressed for this very tag ([laterTag]) and its time has not passed. A newer
     * tag is another version and shows at once. A [laterUntil] farther away than [LATER_MS] (the clock was set back) does not hide.
     */
    fun hidden(now: Long, tag: String, laterTag: String?, laterUntil: Long): Boolean =
        tag == laterTag && now < laterUntil && laterUntil - now <= LATER_MS
}
