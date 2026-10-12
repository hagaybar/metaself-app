package com.metaself.app.domain.letter

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.WeekSessions
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.CountedPlan
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.SessionReviews
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekLedgers

/** One counted session on a letter's day row (D141): its kind, minutes and how it felt, where reviewed. */
data class DaySession(val kind: WorkoutKind, val minutes: Int, val felt: Felt?)

/**
 * One day of the letter's 35 (D141). [plannedMinutes] are the minutes of the day's sessions that ticked a
 * planned session, [ownMinutes] the rest — the split the week's ledger makes (D110). [sleepMinutes] is the
 * night ending that morning (D68). [kcal] and [proteinG] are null when nothing was logged that day. A day
 * before the movement record begins (D136) has null minutes, never zero (D141, amended). A null figure is
 * one not recorded, never zero.
 */
data class LetterDay(
    val epochDay: Long,
    val plannedMinutes: Int?,
    val ownMinutes: Int?,
    val sessions: List<DaySession>,
    val steps: Int?,
    val sleepMinutes: Int?,
    val kcal: Int?,
    val proteinG: Int?,
) {
    /** Every counted minute of the day; null before the record begins. */
    val minutes: Int? get() = if (plannedMinutes == null || ownMinutes == null) null else plannedMinutes + ownMinutes
}

/** D141: the letter's day rows, counted afresh from the record. Pure. */
object LetterDays {

    /** Five weeks: the letter's week and the four before it. */
    const val DAYS = 35

    /**
     * @param sunday the letter's Sunday; the rows run from the Monday four weeks before its week to it.
     * @param workouts the record's sessions as read (combined, D92); only the visible, counted ones count (D131).
     * @param plans every kept version of a weekly plan; each week's covering one decides what was planned (D110).
     * @param reviews any; a session's felt is its review's (D85), never its words.
     * @param days the health record's days.
     * @param food each day's totals, for days holding at least one item.
     * @param recordStart the first day the record holds any movement (D136), null when none; days before it have no minutes.
     */
    fun of(
        sunday: Long,
        workouts: List<Workout>,
        plans: List<CountedPlan>,
        reviews: List<TrainerReview>,
        days: List<HealthDay>,
        food: Map<Long, DayTotals>,
        recordStart: Long?,
    ): List<LetterDay> {
        require(MovementWeek.mondayOf(sunday) == sunday - 6) { "not a Sunday: $sunday" }
        val first = sunday - DAYS + 1
        val sessions = WeekSessions.between(workouts, first, sunday)
        val felt = SessionReviews.bySession(sessions, reviews)
        // The ledger's own count of each week's ticks (D110), each week counted to its Sunday.
        val planned: Set<Long> = (first..sunday step 7).flatMap { monday ->
            WeekLedgers.practiceOf(monday, sunday, workouts, plans)?.ticks.orEmpty().mapNotNull { it.by?.id }
        }.toSet()
        val byDay = sessions.groupBy { it.epochDay }
        val health = days.associateBy { it.epochDay }
        return (first..sunday).map { day ->
            val today = byDay[day].orEmpty()
            val eaten = food[day]
            val recorded = recordStart != null && day >= recordStart
            LetterDay(
                epochDay = day,
                plannedMinutes = if (recorded) today.filter { it.id in planned }.sumOf { it.durationMinutes } else null,
                ownMinutes = if (recorded) today.filterNot { it.id in planned }.sumOf { it.durationMinutes } else null,
                sessions = today.map { DaySession(it.kind, it.durationMinutes, felt[it.id]?.felt) },
                steps = health[day]?.steps,
                sleepMinutes = health[day]?.sleepMinutes,
                kcal = eaten?.kcal,
                proteinG = eaten?.proteinG,
            )
        }
    }
}
