package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.WorkoutKind

/** D93's form: how many weeks, how many sessions a week the owner can manage, and optional words. */
data class ProgrammeAsk(val weeks: Int, val perWeek: Int, val words: String = "") {
    init {
        require(weeks in WEEKS) { "a plan is 2, 4 or 6 weeks, not $weeks" }
        require(perWeek in PER_WEEK) { "2 to 5 sessions a week, not $perWeek" }
    }

    companion object {
        val WEEKS = listOf(2, 4, 6)
        val PER_WEEK = 2..5
    }
}

/** D94: how hard a planned session is meant to be. Stored and sent by name. */
enum class PlannedEffort { EASY, STEADY, PUSH }

/** One planned session (D94): no day — it may be done on any day of its week. */
data class PlannedSession(val kind: WorkoutKind, val minutes: Int, val effort: PlannedEffort, val what: String) {
    init {
        require(kind != WorkoutKind.UNRECOGNISED) { "a planned session is of a known kind" }
        require(minutes in MINUTES) { "a planned session is 5 to 180 minutes, not $minutes" }
    }

    companion object {
        val MINUTES = 5..180
    }
}

data class PlanWeek(val focus: String, val sessions: List<PlannedSession>)

/** A plan of weeks as the model gave it, or as an adjustment composed it (D94, D97). Advice (D4). */
data class WeeksPlan(val title: String, val weeks: List<PlanWeek>, val why: String) {

    /** D94: as many weeks as asked, each with one to [ProgrammeAsk.perWeek] sessions. */
    fun fits(ask: ProgrammeAsk): Boolean = weeks.size == ask.weeks && weeks.all { it.sessions.size in 1..ask.perWeek }

    /** D97: this week and the weeks after — this week up to [thisWeekMax] sessions, none allowed; later weeks one to [perWeek]. */
    fun fitsRest(weeksLeft: Int, perWeek: Int, thisWeekMax: Int): Boolean =
        weeksLeft >= 1 && weeks.size == weeksLeft &&
            weeks.first().sessions.size in 0..thisWeekMax &&
            weeks.drop(1).all { it.sessions.size in 1..perWeek }
}

/** D94: where the owner stands. [sinceLast] is "" when no earlier evaluation was sent. Advice (D4). */
data class Evaluation(val headline: String, val goingWell: String, val toWorkOn: String, val sinceLast: String)

/** One evaluate reply (D94). */
data class EvaluationAndPlan(val evaluation: Evaluation, val plan: WeeksPlan)

/** D98. There is no ENDED: a RUNNING plan past its last Sunday is ended by date. */
enum class ProgrammeStatus { OFFERED, RUNNING, REPLACED, ADJUSTED, STOPPED }

/**
 * One stored answer (D98): an evaluation with its plan, or an adjusted version of a plan ([evaluation]
 * null, [replacesId] the version it was made from). [startEpochDay] is null until kept.
 * [stoppedEpochDay] is the day it stopped running, whatever stopped it (design question 1).
 */
data class Programme(
    val id: Long,
    val createdAtMillis: Long,
    val ask: ProgrammeAsk,
    val evaluation: Evaluation?,
    val plan: WeeksPlan,
    val model: String,
    val startEpochDay: Long? = null,
    val status: ProgrammeStatus = ProgrammeStatus.OFFERED,
    val stoppedEpochDay: Long? = null,
    val replacesId: Long? = null,
)

/** A planned session with its week, counted from 1 (D96), and how many weeks its plan has (D107). */
data class PlannedTick(val week: Int, val ofWeeks: Int, val session: PlannedSession)

/**
 * D93, D105: the last kept evaluation, the plan that ran with it, how many of its sessions were done each
 * week, and how each week went ([weeks], one per week that had begun, as [doneByWeek]).
 */
data class LastEvaluation(
    val epochDay: Long,
    val evaluation: Evaluation,
    val plan: WeeksPlan,
    val doneByWeek: List<Int>,
    val weeks: List<WeekOutcome> = emptyList(),
)

/** D105: the owner's answer to "count it for this?", stored under the chain's first version. */
data class PlanConfirmation(val programmeId: Long, val workoutId: Long, val confirmed: Boolean, val answeredAtMillis: Long)

/** D105: a planned session done as long as planned, done shorter and confirmed, or not done. Sent by name. */
enum class PlannedOutcome { DONE, DONE_SHORT, NOT_DONE }

/** [minutesDone] is the ticking session's, null when not done. */
data class SessionOutcome(val planned: PlannedSession, val outcome: PlannedOutcome, val minutesDone: Int?)

/** D105: a session that ticked nothing, its minutes against the planned minutes of the session it came nearest to. */
data class AttemptFacts(val kind: WorkoutKind, val minutes: Int, val plannedMinutes: Int)

/** D105: one week of a plan as the trainer is told it; [week] is counted from 1. */
data class WeekOutcome(val week: Int, val sessions: List<SessionOutcome>, val attempts: List<AttemptFacts>)
