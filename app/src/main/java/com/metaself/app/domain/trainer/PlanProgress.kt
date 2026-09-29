package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind

/** One planned session and the session that ticked it, if any (D95). */
data class Tick(val planned: PlannedSession, val by: Workout?)

data class WeekProgress(val index: Int, val monday: Long, val focus: String, val ticks: List<Tick>) {
    val done: Int get() = ticks.count { it.by != null }
    val planned: Int get() = ticks.size

    /** The first unticked planned session, in the plan's order (D96's "next in your plan"). */
    val next: PlannedSession? get() = ticks.firstOrNull { it.by == null }?.planned
}

/**
 * D95: which planned sessions are done, counted afresh from the record every time — nothing is stored,
 * so a session deleted, split or combined later changes the ticks with no bookkeeping. Pure.
 */
data class PlanProgress(val weeks: List<WeekProgress>) {
    val done: Int get() = weeks.sumOf { it.done }
    val planned: Int get() = weeks.sumOf { it.planned }

    /** D96: the planned session [workoutId] ticked, with its week counted from 1; null when it ticked none. */
    fun tickOf(workoutId: Long): PlannedTick? = weeks.firstNotNullOfOrNull { week ->
        week.ticks.firstOrNull { it.by?.id == workoutId }?.let { PlannedTick(week.index + 1, it.planned) }
    }

    companion object {
        /**
         * The sessions that count are the visible, counted ones (D74, D81), on or before [until].
         * [workouts] is expected already combined by [com.metaself.app.domain.movement.SessionWitnesses]
         * (D92) — a session witnessed by several sources arrives once. Within each week, in start order,
         * each ticks the first unticked planned session of the same kind; an unrecognised kind never
         * ticks (D95) — defensive, since no [PlannedSession] is ever of that kind either.
         */
        fun of(plan: WeeksPlan, start: Long, workouts: List<Workout>, until: Long): PlanProgress {
            val counted = workouts
                .filter { !it.hidden && it.counted && it.kind != WorkoutKind.UNRECOGNISED && it.epochDay <= until }
                .sortedBy { it.startedAtMillis }
            return PlanProgress(
                plan.weeks.mapIndexed { index, week ->
                    val monday = ProgrammeCalendar.monday(start, index)
                    val by = arrayOfNulls<Workout>(week.sessions.size)
                    counted.filter { it.epochDay in monday..monday + 6 }.forEach { workout ->
                        val slot = week.sessions.indices.firstOrNull { by[it] == null && week.sessions[it].kind == workout.kind }
                        if (slot != null) by[slot] = workout
                    }
                    WeekProgress(index, monday, week.focus, week.sessions.mapIndexed { at, planned -> Tick(planned, by[at]) })
                },
            )
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

    /** The last day a kept version's sessions count: the day it stopped, or [today], never past its last day. */
    fun countedUntil(programme: Programme, today: Long): Long {
        val start = requireNotNull(programme.startEpochDay) { "only a kept plan is counted" }
        return minOf(programme.stoppedEpochDay ?: today, ProgrammeCalendar.lastDay(start, programme.ask.weeks))
    }
}
