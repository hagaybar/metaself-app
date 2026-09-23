package com.metaself.app.domain.day

/**
 * The days a pager can reach, and the mapping between a page number and a calendar date.
 *
 * A pager counts pages from zero; a calendar counts days from 1970. This is the one place that
 * conversion happens, because getting it wrong by one shows yesterday's food under today's heading —
 * invisible in a screenshot, and obvious a week later.
 *
 * The limits are the edges of a scrolling list, not a rule about anybody's life. Five years back is
 * longer than this app will have existed for most of its life; a year forward is more than anybody
 * plans meals for. If either is ever reached, the number changes.
 *
 * @param today the epoch day the range is anchored on, taken as a parameter so the arithmetic stays
 *   pure and a test does not have to pin a clock.
 */
data class DayRange(val today: Long) {

    val pageCount: Int = DAYS_BACK + DAYS_FORWARD + 1

    val todayPage: Int = DAYS_BACK

    /** The date shown on [page]. */
    fun dayAt(page: Int): Long = today - DAYS_BACK + page.coerceIn(0, pageCount - 1)

    /** The page showing [epochDay], clamped to the edges rather than running off them. */
    fun pageOf(epochDay: Long): Int =
        (epochDay - today + DAYS_BACK).coerceIn(0L, (pageCount - 1).toLong()).toInt()

    companion object {
        const val DAYS_BACK = 5 * 365
        const val DAYS_FORWARD = 365
    }
}
