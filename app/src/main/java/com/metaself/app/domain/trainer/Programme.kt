package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.WorkoutKind

/**
 * D93's form, as stored: how many weeks, how many sessions a week the owner can manage, optional words, and — from
 * D119 — the goal. A plan made before goals has none and is 2, 4 or 6 weeks; one with a goal is as long as the goal
 * makes it ([PlanAheadAsk.toAsk]), and a steady one grows a block at a time.
 */
data class ProgrammeAsk(val weeks: Int, val perWeek: Int, val words: String = "", val goal: PlanGoal? = null) {
    init {
        if (goal == null) require(weeks in WEEKS) { "a plan is 2, 4 or 6 weeks, not $weeks" }
        require(weeks >= 1) { "a plan has at least one week, not $weeks" }
        require(perWeek in PER_WEEK) { "2 to 5 sessions a week, not $perWeek" }
    }

    companion object {
        val WEEKS = listOf(2, 4, 6)
        val PER_WEEK = 2..5
    }
}

/** D119: what a distance goal is done as. Sent and stored by name; a ride is a cycle. */
enum class GoalSport(val kind: WorkoutKind) { RUN(WorkoutKind.RUN), WALK(WorkoutKind.WALK), SWIM(WorkoutKind.SWIM), RIDE(WorkoutKind.CYCLE) }

/** D119: a plan's one goal, chosen when it is made. */
sealed interface PlanGoal {
    /** No end date: the plan runs block after block. */
    data object Steady : PlanGoal

    /** D119, D127: a goal with a date — the plan ends on [byEpochDay]'s week (a Sunday, as the form gives it). */
    sealed interface Dated : PlanGoal {
        val byEpochDay: Long
    }

    /** [km] by [byEpochDay]. */
    data class Distance(val sport: GoalSport, val km: Double, override val byEpochDay: Long) : Dated {
        init {
            require(km > 0.0 && km <= MAX_KM) { "a distance goal is more than 0 and at most $MAX_KM km, not $km" }
        }

        companion object {
            /** The form's ceiling — a bound on typing, not a measurement. */
            const val MAX_KM = 1000.0
        }
    }

    /** D127: more time each week — [minutes] a week, reached by [byEpochDay]'s week and held there. */
    data class WeeklyMinutes(val minutes: Int, override val byEpochDay: Long) : Dated {
        init {
            require(minutes in MINUTES) { "a time goal is 30 to 900 minutes a week, not $minutes" }
        }

        companion object {
            /** The form's bounds — a bound on choosing, not a measurement. */
            val MINUTES = 30..900
        }
    }
}

/** D124: Plan ahead's form — the goal, sessions a week, words; the weeks follow from the goal. */
data class PlanAheadAsk(val goal: PlanGoal, val perWeek: Int, val words: String = "") {
    /** The stored ask for a plan starting on [start] (a Monday): steady, one block; a dated goal, to the goal's week. */
    fun toAsk(start: Long): ProgrammeAsk = ProgrammeAsk(
        weeks = when (goal) {
            PlanGoal.Steady -> BLOCK_WEEKS
            is PlanGoal.Dated -> ProgrammeCalendar.weekIndex(start, goal.byEpochDay) + 1
        },
        perWeek = perWeek,
        words = words,
        goal = goal,
    )

    companion object {
        /** D120: a steady plan's block, and the weeks Plan the next block adds. */
        const val BLOCK_WEEKS = 4
    }
}

/** D120: a block's one focus. Sent and stored by name. */
enum class Phase { BASE, BUILD, PEAK, TAPER, STEADY }

/** D120: one week's target — its total minutes, the longest session's minutes, and whether it is an easier week. */
data class WeekTarget(val minutes: Int, val longest: Int, val easier: Boolean) {
    /** D94's session bounds: the longest is a session's 5 to 180 minutes; the week holds at least it and at most [perWeek] of the longest kind. */
    fun fits(perWeek: Int): Boolean = longest in PlannedSession.MINUTES && minutes >= longest && minutes <= perWeek * PlannedSession.MINUTES.last
}

/** D120: a block of weeks with one phase and a one-line focus. */
data class Block(val phase: Phase, val focus: String, val weeks: List<WeekTarget>)

/** D120: the road to the goal — its blocks, in order, every week's target among them. */
data class Outline(val blocks: List<Block>) {
    val targets: List<WeekTarget> get() = blocks.flatMap { it.weeks }
    val weeks: Int get() = targets.size

    /** Every week of a plan of [weeks] has a target, and no block is empty. */
    fun covers(weeks: Int): Boolean = weeks >= 1 && this.weeks == weeks && blocks.all { it.weeks.isNotEmpty() }

    fun fits(perWeek: Int): Boolean = targets.all { it.fits(perWeek) }

    /** Week [index]'s target, from 0; null past the end. */
    fun target(index: Int): WeekTarget? = targets.getOrNull(index)

    fun withTarget(index: Int, target: WeekTarget): Outline = withTargetsFrom(index, listOf(target))

    /** The targets from week [from] on replaced by [targets], in order; the blocks, their phases and focus kept. */
    fun withTargetsFrom(from: Int, targets: List<WeekTarget>): Outline {
        var at = 0
        return Outline(
            blocks.map { block ->
                block.copy(
                    weeks = block.weeks.map { old ->
                        val index = at++
                        targets.getOrNull(index - from)?.takeIf { index >= from } ?: old
                    },
                )
            },
        )
    }

    operator fun plus(block: Block): Outline = Outline(blocks + block)
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

/**
 * A plan of weeks as the model gave it, or as an adjustment composed it (D94, D97). Advice (D4). [outline] is its
 * blocks and weekly targets (D120); null for a plan made before goals, which every week of carries sessions.
 */
data class WeeksPlan(val title: String, val weeks: List<PlanWeek>, val why: String, val outline: Outline? = null) {

    /** D94: as many weeks as asked, each with one to [ProgrammeAsk.perWeek] sessions. */
    fun fits(ask: ProgrammeAsk): Boolean = weeks.size == ask.weeks && weeks.all { it.sessions.size in 1..ask.perWeek }

    /** D125: the weeks that carry sessions, counted from 1 — every week of a plan made before outlines. */
    val filledWeeks: List<Int> get() = weeks.withIndex().filter { it.value.sessions.isNotEmpty() }.map { it.index + 1 }

    /** D97: this week and the weeks after — this week up to [thisWeekMax] sessions, none allowed; later weeks one to [perWeek]. */
    fun fitsRest(weeksLeft: Int, perWeek: Int, thisWeekMax: Int): Boolean =
        weeksLeft >= 1 && weeks.size == weeksLeft &&
            weeks.first().sessions.size in 0..thisWeekMax &&
            weeks.drop(1).all { it.sessions.size in 1..perWeek }
}

/** D121: how many of a new plan's weeks carry sessions — the first two; the rest carry their target only. */
const val FILLED_AT_FIRST = 2

/**
 * D120, D121: a new plan's reply — its first weeks with sessions — as the whole plan: the remaining weeks of [outline]
 * added empty, with no focus (their block gives it), and the outline kept.
 */
fun WeeksPlan.withOutline(outline: Outline): WeeksPlan =
    copy(weeks = weeks + List(outline.weeks - weeks.size) { PlanWeek("", emptyList()) }, outline = outline)

/** D97, D121: Adjust's reply — this week's rest and the weeks after it that it rewrites — and, for a plan with an outline, a target for every week from this one. */
data class AdjustedRest(val plan: WeeksPlan, val targets: List<WeekTarget> = emptyList())

/** D121: one week filled in — its sessions, its target (as it was, or changed) and, when changed, one sentence of why ("" otherwise). */
data class FilledWeek(val week: PlanWeek, val target: WeekTarget, val why: String)

/** D121: a steady plan's next block, and why. */
data class BlockAnswer(val block: Block, val why: String)

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
