package com.metaself.app.domain.letter

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** When the weekly letter runs, and which week it writes (D99; design questions 4–6). Pure. */
object LetterSchedule {

    private const val DEADLINE_HOUR = 12

    /** The first Sunday at [hour] strictly after [now]. */
    fun nextRun(now: LocalDateTime, hour: Int): LocalDateTime {
        val thisSunday = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(hour, 0)
        return if (thisSunday.isAfter(now)) thisSunday else thisSunday.plusWeeks(1)
    }

    /** The Monday of the week to write at [now], or null outside Sunday [hour]..Monday noon. */
    fun weekToWrite(now: LocalDateTime, hour: Int): Long? {
        val date = now.toLocalDate()
        val sunday: LocalDate = when {
            date.dayOfWeek == DayOfWeek.SUNDAY && now.hour >= hour -> date
            date.dayOfWeek == DayOfWeek.MONDAY && now.hour < DEADLINE_HOUR -> date.minusDays(1)
            else -> return null
        }
        return sunday.minusDays(6).toEpochDay()
    }

    /** Whether a retryable failure for [weekMonday]'s letter may still be retried at [now]. */
    fun mayRetry(weekMonday: Long, now: LocalDateTime): Boolean =
        now.isBefore(LocalDate.ofEpochDay(weekMonday).plusDays(7).atTime(DEADLINE_HOUR, 0))
}
