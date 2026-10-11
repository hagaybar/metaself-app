package com.metaself.app.domain.trainer

/** D117: where the owner stands in a plan — the last part of the plan screen's subtitle. */
sealed interface PlanStanding {
    /** The plan starts on [start], after today. */
    data class Before(val start: Long) : PlanStanding

    /** Today is in week [number], counted from 1. */
    data class InWeek(val number: Int) : PlanStanding

    /** Its last day, [lastDay], is past. */
    data class Ended(val lastDay: Long) : PlanStanding

    /** Stopped on [day] (D97). */
    data class Stopped(val day: Long) : PlanStanding
}

/** D117: how one planned session of this week stands, as its tile shows it. */
enum class TileState { DONE, DONE_SHORT, OPEN }

/**
 * D117: a planned session of this week; [day] and [minutes] are the session that ticked it, null while open.
 * [stepsReady]: kept steps belong to it (D122).
 */
data class PlanTile(
    val planned: PlannedSession,
    val state: TileState,
    val day: Long? = null,
    val minutes: Int? = null,
    val stepsReady: Boolean = false,
)

/** D117: a week's place on the timeline. */
sealed interface TimelinePlace {
    /** [done] of [planned] planned sessions; [own], the week's sessions beyond the plan, from its ledger (D110). */
    data class Past(val done: Int, val planned: Int, val own: Int) : TimelinePlace

    /** One tile per planned session. */
    data class ThisWeek(val tiles: List<PlanTile>) : TimelinePlace

    /** A week ahead, or one a stopped plan never reached. */
    data class Ahead(val sessions: List<PlannedSession>) : TimelinePlace
}

/** D117: one row of the timeline, [number] counted from 1; [target], its outline's target (D123), null without one. */
data class TimelineWeek(val number: Int, val focus: String, val place: TimelinePlace, val target: WeekTarget? = null)

/**
 * D117, D118: a weekly plan drawn as its weeks — kept, or offered and not yet kept. Every count is the phone's
 * (D95): the plan's progress, and each past week's ledger. Pure.
 */
data class PlanTimeline(
    val title: String,
    val why: String,
    val start: Long,
    val standing: PlanStanding,
    val weeks: List<TimelineWeek>,
) {
    val lastDay: Long get() = ProgrammeCalendar.lastDay(start, weeks.size)

    /** Ended or stopped: the screen offers one thing, Plan ahead (D117, D124). */
    val over: Boolean get() = standing is PlanStanding.Ended || standing is PlanStanding.Stopped

    /** D122: this week's tiles at [places] have steps. Other weeks are drawn without tiles, so are left as they are. */
    fun withSteps(places: Set<PlanPlace>): PlanTimeline = copy(
        weeks = weeks.map { week ->
            val place = week.place as? TimelinePlace.ThisWeek ?: return@map week
            val tiles = place.tiles.mapIndexed { at, tile -> tile.copy(stepsReady = PlanPlace(week.number - 1, at) in places) }
            week.copy(place = TimelinePlace.ThisWeek(tiles))
        },
    )

    companion object {
        /**
         * @param start the plan's first Monday: its own once kept, else the one keeping it today would give (D94).
         * @param progress the plan's ticks, counted to today or to its stop day; null for an answer nothing is
         *   counted for — every session of this week open.
         * @param ledgers the ledger (D110) of each week begun, by its Monday; a past week with none has no
         *   sessions of its own.
         *
         * A stopped plan's weeks begun by its stop day are past and the rest are drawn ahead; an ended plan's
         * are all past. Only a STOPPED version is stopped: a replaced or adjusted one has a stop day too, but a
         * later version runs.
         */
        fun of(programme: Programme, start: Long, today: Long, progress: PlanProgress?, ledgers: Map<Long, WeekLedger>): PlanTimeline {
            val plan = programme.plan
            val last = ProgrammeCalendar.lastDay(start, plan.weeks.size)
            val stopped = programme.stoppedEpochDay.takeIf { programme.status == ProgrammeStatus.STOPPED }
            val current = ProgrammeCalendar.weekIndex(start, today)
            val standing = when {
                stopped != null -> PlanStanding.Stopped(stopped)
                today > last -> PlanStanding.Ended(last)
                today < start -> PlanStanding.Before(start)
                else -> PlanStanding.InWeek(current + 1)
            }
            // Release 1's limit 3: a version replaced from next Monday is drawn to its stop day; the next plan has the rest.
            val replaced = programme.stoppedEpochDay.takeIf { programme.status == ProgrammeStatus.REPLACED }
            val drawn = if (replaced == null) plan.weeks else plan.weeks.filterIndexed { index, _ -> ProgrammeCalendar.monday(start, index) <= replaced }
            val weeks = drawn.mapIndexed { index, week ->
                val monday = ProgrammeCalendar.monday(start, index)
                val ticks = progress?.weeks?.getOrNull(index)?.ticks
                val past = TimelinePlace.Past(
                    done = ticks.orEmpty().count { it.by != null },
                    planned = week.sessions.size,
                    own = ledgers[monday]?.beyond?.kinds.orEmpty().sumOf { it.sessions },
                )
                val place = when {
                    stopped != null -> if (monday <= stopped) past else TimelinePlace.Ahead(week.sessions)
                    index < current -> past
                    index == current -> TimelinePlace.ThisWeek(week.sessions.mapIndexed { at, planned -> tile(planned, ticks?.getOrNull(at)) })
                    else -> TimelinePlace.Ahead(week.sessions)
                }
                TimelineWeek(index + 1, week.focus, place, plan.outline?.target(index))
            }
            return PlanTimeline(plan.title, plan.why, start, standing, weeks)
        }

        private fun tile(planned: PlannedSession, tick: Tick?): PlanTile {
            val by = tick?.by ?: return PlanTile(planned, TileState.OPEN)
            return PlanTile(planned, if (tick.short) TileState.DONE_SHORT else TileState.DONE, by.epochDay, by.durationMinutes)
        }
    }
}

/** D93, D117: an evaluation with the day it was made, for "WHERE YOU STOOD". */
data class DatedEvaluation(val evaluation: Evaluation, val epochDay: Long, val goalView: GoalView? = null)

/**
 * D117: what the plan screen draws — the plan as its weeks, the running plan's card behind its buttons (null
 * when the plan has ended or stopped, or is an answer not yet kept), the evaluation it carries, and the steps
 * kept for its planned sessions, by place (D122).
 */
data class PlanShown(
    val timeline: PlanTimeline,
    val running: PlanCard.Running?,
    val evaluation: DatedEvaluation?,
    val steps: Map<PlanPlace, TrainerPlan> = emptyMap(),
    /** D119: the plan's goal; null for a plan made before goals. */
    val goal: PlanGoal? = null,
    /** D123: the Month view; null for a plan without an outline. */
    val month: PlanMonth? = null,
)
