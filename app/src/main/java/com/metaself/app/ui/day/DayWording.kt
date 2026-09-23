package com.metaself.app.ui.day

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a date is called on screen.
 *
 * Today, yesterday and tomorrow get names because they are the three days that get used. Everything
 * else is a weekday and a date: "4 days ago" reads as though it were friendlier and makes the reader
 * do arithmetic to find out which day it means.
 *
 * The year appears only when it is not the current one. On the 364 days it is redundant it is noise.
 *
 * [Locale.UK] rather than the default, deliberately: the format has to be stable, or a test passing
 * on this machine fails on a differently configured one. The app is single-user and English.
 */
object DayWording {

    private val SAME_YEAR = DateTimeFormatter.ofPattern("EEE d MMMM", Locale.UK)
    private val OTHER_YEAR = DateTimeFormatter.ofPattern("EEE d MMMM yyyy", Locale.UK)

    fun label(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(if (date.year == today.year) SAME_YEAR else OTHER_YEAR)
    }
}
