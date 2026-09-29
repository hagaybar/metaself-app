package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.MovementWeek

/** D94, D97: a plan's weeks are Monday to Sunday, as everywhere else (D74). Pure. */
object ProgrammeCalendar {

    /** How long an ended plan's card stays (D97). */
    const val ENDED_SHOWN_DAYS = 14

    /** Kept Monday to Thursday: this week's Monday; Friday to Sunday: the next Monday. */
    fun startFor(keptOn: Long): Long {
        val monday = MovementWeek.mondayOf(keptOn)
        return if (keptOn - monday <= 3) monday else monday + 7
    }

    fun lastDay(start: Long, weeks: Int): Long = start + 7L * weeks - 1

    /** 0 for week 1; negative before the start; [weeks] or more after the end. */
    fun weekIndex(start: Long, day: Long): Int = Math.floorDiv(day - start, 7L).toInt()

    fun monday(start: Long, index: Int): Long = start + 7L * index

    fun ended(start: Long, weeks: Int, today: Long): Boolean = today > lastDay(start, weeks)

    fun endedShown(start: Long, weeks: Int, today: Long): Boolean =
        today - lastDay(start, weeks) in 1..ENDED_SHOWN_DAYS.toLong()
}
