package com.metaself.app.domain.trainer

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import kotlin.math.roundToInt

/** D110: a kept version of a weekly plan (D94), with how its sessions are counted (D105). */
data class CountedPlan(val programme: Programme, val counting: PlanCounting) {
    init {
        require(programme.startEpochDay != null) { "only a kept plan is counted" }
    }

    val start: Long get() = programme.startEpochDay!!

    companion object {
        /**
         * The version counted during [monday]'s week: of those that started by its Sunday and were still
         * counted on or after its Monday, the latest-started; of two with the same start — an adjustment
         * keeps its plan's start (D97) — the one made later, which the week counts under. Null when none.
         */
        fun covering(plans: List<CountedPlan>, monday: Long): CountedPlan? = plans
            .filter { it.start <= monday + 6 && Programmes.countedUntil(it.programme, monday + 6) >= monday }
            .maxWithOrNull(compareBy<CountedPlan>({ it.start }, { it.programme.createdAtMillis }))
    }
}

/** D110: a week of a weekly plan, [week] counted from 1 of [ofWeeks], each planned session's outcome (D105). */
data class PracticeLedger(val week: Int, val ofWeeks: Int, val sessions: List<SessionOutcome>)

/**
 * D110: one kind's sessions beyond the plan. [distanceM] sums the sessions that recorded one, null when none
 * did. [briskMinutes] is the minutes in heart-rate zones 2 to 5 (D70) over the sessions that have zones, null
 * when none has — nothing measured is not zero. [attempts] are those of the sessions that attempted a planned
 * session (D105), each with the planned minutes it attempted, in start order.
 */
data class KindLedger(
    val kind: WorkoutKind,
    val sessions: Int,
    val minutes: Int,
    val distanceM: Int?,
    val briskMinutes: Int?,
    val attempts: List<AttemptFacts>,
)

/**
 * D110: every session that ticked no planned session, by kind; and steps and distance a day, each averaged
 * over the days that record it. Distance is never split into "in sessions" and "outside them".
 */
data class BeyondPlan(val kinds: List<KindLedger>, val stepsADay: Int?, val distanceMADay: Int?)

/** D110: the letter's food figures (D100) — averages over the [daysLogged] days — with today's calorie target. */
data class FoodContext(val daysLogged: Int, val kcalADay: Int?, val proteinGADay: Int?, val targetKcal: Int?)

/**
 * D110: sleep a night (minutes), resting heart rate and HRV (ms), each averaged over the days that record it;
 * each usual is the same average over the 28 days before the week's Monday, null under 7 recorded days.
 */
data class Recovery(
    val sleepMinutes: Int?,
    val usualSleepMinutes: Int?,
    val restingHeartRate: Int?,
    val usualRestingHeartRate: Int?,
    val hrvMs: Int?,
    val usualHrvMs: Int?,
)

/**
 * D110: one Monday-to-Sunday week as the trainer is told it. Every visible, counted session of the week is
 * in exactly one of [practice] (as the session that ticked a planned one) and [beyond]. [practice] is null
 * for a week no kept plan covers. [current] is the calendar's week, counted to today.
 */
data class WeekLedger(
    val monday: Long,
    val current: Boolean,
    val practice: PracticeLedger?,
    val beyond: BeyondPlan,
    val food: FoodContext,
    val recovery: Recovery,
)

/**
 * D110: a week's ledger, counted afresh from the record every time, like [PlanProgress] (D95): only stored
 * answers are kept, so a session deleted, split or combined later changes the ledger with no bookkeeping.
 * Pure.
 */
object WeekLedgers {

    /** The days before a week's Monday that its usuals are counted over. */
    const val USUAL_DAYS = 28

    /** Fewer recorded days than this in [USUAL_DAYS] and there is no usual. */
    const val USUAL_LEAST_DAYS = 7

    /** Fewest days a week must record a recovery figure for its average to go (D110: one night is never sent). */
    const val WEEK_LEAST_DAYS = 2

    /**
     * @param monday the week; a Monday, never after this one.
     * @param today the calendar's today; this week is counted to it.
     * @param workouts the record's sessions as read (combined, D92); hidden and uncounted ones are left out here.
     * @param days the health record's days; the week's and the [USUAL_DAYS] before it are read.
     * @param food each day's food totals, for days holding at least one item; the week's are read.
     * @param targetKcal today's calorie target; null with no profile.
     * @param plans every kept version of a weekly plan; the one [CountedPlan.covering] the week gives practice.
     */
    fun of(
        monday: Long,
        today: Long,
        workouts: List<Workout>,
        days: List<HealthDay>,
        food: Map<Long, DayTotals>,
        targetKcal: Int?,
        plans: List<CountedPlan>,
    ): WeekLedger {
        require(MovementWeek.mondayOf(monday) == monday) { "not a Monday: $monday" }
        require(monday <= MovementWeek.mondayOf(today)) { "a week after this one: $monday" }
        val last = minOf(today, monday + 6)
        val week = monday..last
        val sessions = workouts
            .filter { !it.hidden && it.counted && it.epochDay in week }
            .sortedBy { it.startedAtMillis }
        val practice = practiceWeek(plans, monday, last, workouts)
        val progress = practice?.second
        val ticked = progress?.ticks.orEmpty().mapNotNull { it.by?.id }.toSet()
        // D105: attempts, and open candidates — what WeekProgress.outcome() sends as attempts.
        val attempted: Map<Long, PlannedSession> = progress?.let { p ->
            p.attempts.associate { it.workout.id to it.against } +
                p.ticks.mapNotNull { tick -> tick.candidate?.let { it.id to tick.planned } }
        }.orEmpty()
        val byKind = sessions
            .filter { it.id !in ticked }
            .groupBy { if (it.kind == WorkoutKind.UNRECOGNISED) WorkoutKind.OTHER else it.kind }
        val weekDays = days.filter { it.epochDay in week }
        val before = days.filter { it.epochDay in monday - USUAL_DAYS until monday }
        val eaten = food.filterKeys { it in week }.values
        return WeekLedger(
            monday = monday,
            current = MovementWeek.mondayOf(today) == monday,
            practice = practice?.let { (plan, weekProgress) ->
                PracticeLedger(weekProgress.index + 1, plan.programme.plan.weeks.size, weekProgress.outcome().sessions)
            },
            beyond = BeyondPlan(
                kinds = WorkoutKind.entries.mapNotNull { kind -> byKind[kind]?.let { kindLedger(kind, it, attempted) } },
                stepsADay = weekDays.mapNotNull { it.steps }.roundedMean(),
                distanceMADay = weekDays.mapNotNull { it.distanceM }.roundedMean(),
            ),
            food = FoodContext(
                daysLogged = eaten.size,
                kcalADay = eaten.map { it.kcal }.roundedMean(),
                proteinGADay = eaten.map { it.proteinG }.roundedMean(),
                targetKcal = targetKcal,
            ),
            recovery = Recovery(
                sleepMinutes = weekly(weekDays) { it.sleepMinutes },
                usualSleepMinutes = usual(before) { it.sleepMinutes },
                restingHeartRate = weekly(weekDays) { it.restingHeartRate },
                usualRestingHeartRate = usual(before) { it.restingHeartRate },
                hrvMs = weekly(weekDays) { it.hrvMs },
                usualHrvMs = usual(before) { it.hrvMs },
            ),
        )
    }

    /** The plan covering the week and the week's progress in it, counted to [last]; null when none covers it. */
    private fun practiceWeek(plans: List<CountedPlan>, monday: Long, last: Long, workouts: List<Workout>): Pair<CountedPlan, WeekProgress>? {
        val plan = CountedPlan.covering(plans, monday) ?: return null
        val index = ProgrammeCalendar.weekIndex(plan.start, monday)
        if (index !in plan.programme.plan.weeks.indices) return null
        val until = Programmes.countedUntil(plan.programme, last)
        return plan to PlanProgress.of(plan.programme.plan, plan.start, workouts, until, plan.counting).weeks[index]
    }

    private fun kindLedger(kind: WorkoutKind, sessions: List<Workout>, attempted: Map<Long, PlannedSession>): KindLedger {
        val zoned = sessions.mapNotNull { it.zoneSeconds }
        return KindLedger(
            kind = kind,
            sessions = sessions.size,
            minutes = sessions.sumOf { it.durationMinutes },
            distanceM = sessions.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum(),
            // Zones 2 to 5 (D70): the seconds summed, then rounded once to the nearest minute, as the request rounds.
            briskMinutes = if (zoned.isEmpty()) null else (zoned.sumOf { zones -> zones.drop(1).sum() } + 30) / 60,
            attempts = sessions.mapNotNull { session ->
                attempted[session.id]?.let { AttemptFacts(session.kind, session.durationMinutes, it.minutes) }
            },
        )
    }

    private fun usual(before: List<HealthDay>, pick: (HealthDay) -> Number?): Int? =
        before.mapNotNull(pick).takeIf { it.size >= USUAL_LEAST_DAYS }?.roundedMean()

    /** A week's recovery average needs two recorded days: an average of one night IS that night (D110). */
    private fun weekly(weekDays: List<HealthDay>, pick: (HealthDay) -> Number?): Int? =
        weekDays.mapNotNull(pick).takeIf { it.size >= WEEK_LEAST_DAYS }?.roundedMean()

    private fun List<Number>.roundedMean(): Int? = if (isEmpty()) null else map { it.toDouble() }.average().roundToInt()
}
