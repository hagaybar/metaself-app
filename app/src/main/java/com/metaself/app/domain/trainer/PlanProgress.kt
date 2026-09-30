package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind

/**
 * One planned session (D95, D105): [by] is the session that ticked it — as long as planned, or shorter and
 * confirmed ([short]); [candidate] is a shorter session waiting for the owner's answer, while unticked.
 */
data class Tick(
    val planned: PlannedSession,
    val by: Workout?,
    val short: Boolean = false,
    val candidate: Workout? = null,
)

/** D105: a session of a planned kind that ticked nothing, with the planned session it came nearest to. */
data class Attempt(val workout: Workout, val against: PlannedSession)

/**
 * [attempts]: under half as long as planned, a candidate answered No, or one a full session displaced
 * (D105), in start order. An open candidate is on its [Tick], not here.
 */
data class WeekProgress(
    val index: Int,
    val monday: Long,
    val focus: String,
    val ticks: List<Tick>,
    val attempts: List<Attempt> = emptyList(),
) {
    val done: Int get() = ticks.count { it.by != null }
    val planned: Int get() = ticks.size

    /** The first unticked planned session, in the plan's order (D96's "next in your plan"). */
    val next: PlannedSession? get() = ticks.firstOrNull { it.by == null }?.planned

    /** D105, what the trainer is told of this week: each planned session's outcome, and every attempt — an open candidate among them. */
    fun outcome(): WeekOutcome = WeekOutcome(
        week = index + 1,
        sessions = ticks.map { tick ->
            val outcome = when {
                tick.by == null -> PlannedOutcome.NOT_DONE
                tick.short -> PlannedOutcome.DONE_SHORT
                else -> PlannedOutcome.DONE
            }
            SessionOutcome(tick.planned, outcome, tick.by?.durationMinutes)
        },
        attempts = (attempts + ticks.mapNotNull { tick -> tick.candidate?.let { Attempt(it, tick.planned) } })
            .sortedBy { it.workout.startedAtMillis }
            .map { AttemptFacts(it.workout.kind, it.workout.durationMinutes, it.against.minutes) },
    )
}

/**
 * D105: a plan's sessions count from [fromMillis], the moment its chain's first version was kept (the
 * answer's `createdAtMillis`; Keep follows by seconds). [answers] are the owner's, by workout id — true is
 * Yes — stored under [chainId], the first version's id.
 */
data class PlanCounting(val chainId: Long, val fromMillis: Long, val answers: Map<Long, Boolean>)

/**
 * D95, D105: which planned sessions are done, counted afresh from the record every time — only the owner's
 * answers are stored, so a session deleted, split or combined later changes the ticks with no bookkeeping.
 * Pure.
 */
data class PlanProgress(val weeks: List<WeekProgress>) {
    val done: Int get() = weeks.sumOf { it.done }
    val planned: Int get() = weeks.sumOf { it.planned }

    /** D96: the planned session [workoutId] ticked, with its week counted from 1; null when it ticked none. */
    fun tickOf(workoutId: Long): PlannedTick? = weeks.firstNotNullOfOrNull { week ->
        week.ticks.firstOrNull { it.by?.id == workoutId }?.let { PlannedTick(week.index + 1, weeks.size, it.planned) }
    }

    companion object {
        /**
         * The sessions that count are the visible, counted ones (D74, D81) that started at or after
         * [PlanCounting.fromMillis], on or before [until]. [workouts] is expected already combined by
         * [com.metaself.app.domain.movement.SessionWitnesses] (D92). Within each week, in start order, a
         * session goes among the unticked planned sessions of its kind (design question 1 of the D105 plan):
         * to the first it is as long as, which it ticks — an open candidate there becomes an attempt; else
         * to the first it is at least half of that holds no open candidate, where the owner's answer ticks
         * it (Yes), makes it an attempt (No), or leaves it a candidate; else it is an attempt against the
         * nearest. A session the owner said Yes to is placed first by the half rule, onto the place it was
         * confirmed for (pushing out an open candidate there), so an adjustment or a late sync cannot move it.
         * With nothing of its kind left unticked a session is neither. Only minutes decide.
         */
        fun of(plan: WeeksPlan, start: Long, workouts: List<Workout>, until: Long, counting: PlanCounting): PlanProgress {
            val counted = workouts
                .filter {
                    !it.hidden && it.counted && it.kind != WorkoutKind.UNRECOGNISED && it.epochDay <= until &&
                        it.startedAtMillis >= counting.fromMillis
                }
                .sortedBy { it.startedAtMillis }
            return PlanProgress(
                plan.weeks.mapIndexed { index, week ->
                    val monday = ProgrammeCalendar.monday(start, index)
                    val slots = week.sessions.map { Slot(it) }
                    val attempts = mutableListOf<Attempt>()
                    counted.filter { it.epochDay in monday..monday + 6 }.forEach { workout ->
                        place(workout, slots, counting.answers, attempts)
                    }
                    WeekProgress(
                        index, monday, week.focus,
                        slots.map { Tick(it.planned, it.by, it.short, it.candidate) },
                        attempts.sortedBy { it.workout.startedAtMillis },
                    )
                },
            )
        }

        private class Slot(val planned: PlannedSession) {
            var by: Workout? = null
            var short = false
            var candidate: Workout? = null
        }

        private fun place(workout: Workout, slots: List<Slot>, answers: Map<Long, Boolean>, attempts: MutableList<Attempt>) {
            val minutes = workout.durationMinutes
            val open = slots.filter { it.by == null && it.planned.kind == workout.kind }
            if (open.isEmpty()) return
            // A stored Yes is placed by the half rule first, onto the place it was confirmed for, pushing out
            // an open candidate there: an adjustment that adds a shorter place cannot move it, and an earlier
            // session synced late cannot take the place it holds.
            if (answers[workout.id] == true) {
                val confirmed = open.firstOrNull { 2 * minutes >= it.planned.minutes }
                if (confirmed != null) {
                    confirmed.candidate?.let { attempts += Attempt(it, confirmed.planned) }
                    confirmed.by = workout
                    confirmed.short = minutes < confirmed.planned.minutes
                    confirmed.candidate = null
                    return
                }
            }
            val full = open.firstOrNull { minutes >= it.planned.minutes }
            if (full != null) {
                full.candidate?.let { attempts += Attempt(it, full.planned) }
                full.by = workout
                full.candidate = null
                return
            }
            val half = open.firstOrNull { 2 * minutes >= it.planned.minutes && it.candidate == null }
            if (half == null) {
                attempts += Attempt(workout, open.minBy { kotlin.math.abs(it.planned.minutes - minutes) }.planned)
                return
            }
            // A Yes was placed above; here the answer is No or none.
            if (answers[workout.id] == false) attempts += Attempt(workout, half.planned) else half.candidate = workout
        }
    }
}

/** D98's reading of the stored rows (design questions 1–3). Pure. */
object Programmes {

    /**
     * The newest evaluation that was kept at some point, and the newest kept version of its plan
     * (followed forward through `replacesId`); null when none was ever kept.
     */
    fun lastEvaluated(all: List<Programme>): Pair<Programme, Programme>? {
        val kept = all.filter { it.status != ProgrammeStatus.OFFERED }
        val evaluated = kept.filter { it.evaluation != null }.maxByOrNull { it.createdAtMillis } ?: return null
        // Bounded the same way as evaluationOf: a self- or mutually-referencing replacesId (a
        // hand-edited or restored backup) must not loop forever.
        val latest = generateSequence(evaluated) { current -> kept.firstOrNull { it.replacesId == current.id } }
            .take(kept.size + 1)
            .last()
        return evaluated to latest
    }

    /** The evaluation a version carries, else the one of the version it replaces, and so on back. */
    fun evaluationOf(programme: Programme, all: List<Programme>): Evaluation? =
        generateSequence(programme) { current -> current.replacesId?.let { id -> all.firstOrNull { it.id == id } } }
            .take(all.size + 1)
            .firstNotNullOfOrNull { it.evaluation }

    /** D105: the chain's first version — followed back through `replacesId`, bounded as [evaluationOf] is. */
    fun rootOf(programme: Programme, all: List<Programme>): Programme =
        generateSequence(programme) { current -> current.replacesId?.let { id -> all.firstOrNull { it.id == id } } }
            .take(all.size + 1)
            .last()

    /** The last day a kept version's sessions count: the day it stopped, or [today], never past its last day. */
    fun countedUntil(programme: Programme, today: Long): Long {
        val start = requireNotNull(programme.startEpochDay) { "only a kept plan is counted" }
        return minOf(programme.stoppedEpochDay ?: today, ProgrammeCalendar.lastDay(start, programme.ask.weeks))
    }
}
