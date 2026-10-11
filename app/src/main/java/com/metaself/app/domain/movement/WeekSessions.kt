package com.metaself.app.domain.movement

/**
 * D115, D131: the one rule for a week's sessions — every visible (not hidden), counted (D81) session from
 * [monday] to the week's last day shown, in start order — and their minutes. The Trainer's verdict, its week
 * strip and ledger (D110, D114) and Movement's headline all count by it, so the two pages never disagree.
 * Pure.
 */
object WeekSessions {

    /** [monday]'s sessions, to [today] on this week and to Sunday on an earlier one. */
    fun of(workouts: List<Workout>, monday: Long, today: Long): List<Workout> {
        val week = monday..minOf(today, monday + 6)
        return workouts
            .filter { !it.hidden && it.counted && it.epochDay in week }
            .sortedBy { it.startedAtMillis }
    }

    /** "156 minutes moving this week" (D115), "156 min" (D131): every one of [sessions]' minutes. */
    fun minutes(sessions: List<Workout>): Int = sessions.sumOf { it.durationMinutes }
}
