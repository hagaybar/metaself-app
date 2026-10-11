package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind

/** A session with its review, for "Earlier sessions" (D85). */
data class ReviewedSession(val workout: Workout, val review: TrainerReview)

/**
 * What the Trainer screen shows beside the week (D85, D114): the session waiting for words, the weekly plan (D95,
 * D97) behind the main button and the plan's row, and earlier reviewed sessions,
 * newest first, for their own page. A review is shown only beside its own session, so one whose session left the
 * record (a negative workout id, D88) is shown nowhere.
 */
data class TrainerHome(
    val waiting: Workout?,
    val earlier: List<ReviewedSession>,
    /** D95, D97: the weekly plan's card. */
    val plan: PlanCard = PlanCard.None,
    /** D96: the running plan's next session this week, for Plan my next session. */
    val next: PlannedTick? = null,
    /** The day the home was read for, which D121's offers depend on; null reads as no offer. */
    val today: Long? = null,
) {
    /** D114, D121, D124: the page's one main button — a week to fill in, the open planned session, the next session, or Plan ahead. */
    val main: MainAction get() = MainAction.of(plan, next, today)

    companion object {
        /** Today, yesterday and the day before (design question 10). */
        const val WAITING_DAYS = 3

        fun of(
            today: Long,
            recent: List<Workout>,
            reviewed: List<Workout>,
            reviews: List<TrainerReview>,
            running: Programme? = null,
            planWorkouts: List<Workout> = emptyList(),
            /** D105: what the running plan counts from, and the owner's answers; no card without it. */
            counting: PlanCounting? = null,
        ): TrainerHome {
            // D92: a combined session reviewed on another of its witnesses is not waiting.
            val byWorkout = SessionReviews.bySession(recent, reviews)
            val waiting = recent
                .filter { !it.hidden && it.counted && it.epochDay in (today - (WAITING_DAYS - 1))..today && it.id !in byWorkout }
                .maxByOrNull { it.startedAtMillis }
            val earlier = reviewed
                .filter { !it.hidden }
                .mapNotNull { workout -> byWorkout[workout.id]?.let { ReviewedSession(workout, it) } }
                .sortedByDescending { it.workout.startedAtMillis }
            val plan = if (counting == null) PlanCard.None else PlanCard.of(running, planWorkouts, today, counting)
            return TrainerHome(waiting, earlier, plan, (plan as? PlanCard.Running)?.next, today)
        }
    }
}

/** D114: the Trainer page's one main button, the next thing to do. */
sealed interface MainAction {
    /** A plan runs and this week's first open planned session is of [kind]: "Plan the open walk". */
    data class PlanOpen(val kind: WorkoutKind) : MainAction

    /** A plan runs with nothing open this week, or has not begun: "Plan my next session". */
    data object PlanNext : MainAction

    /** No plan runs, it has ended, or this week is its last: "Plan ahead" (D124). */
    data object PlanAhead : MainAction

    /** D121: this week has begun with no sessions: "Plan this week", on the plan page. */
    data object PlanThisWeek : MainAction

    /** D121: Sunday, and next week has no sessions: "Fill in next week", on the plan page. */
    data object FillNextWeek : MainAction

    /** D121: a steady plan's last week: "Plan the next block", on the plan page — a steady plan does not end. */
    data object NextBlock : MainAction

    companion object {
        /** D114, D121, D124: the next thing to do, for [plan], its running week's [next] open session, and [today]. */
        fun of(plan: PlanCard, next: PlannedTick?, today: Long? = null): MainAction {
            if (plan !is PlanCard.Running) return PlanAhead
            // The next plan is already kept; this one's open sessions are what is left to do.
            if (plan.replacedFrom != null) return session(next)
            when (val offer = today?.let { PlanFilling.offer(plan, it) }) {
                is PlanOffer.PlanThisWeek -> return PlanThisWeek
                is PlanOffer.FillNextWeek -> if (today != null && PlanFilling.sunday(today)) return FillNextWeek
                PlanOffer.NextBlock -> return NextBlock
                null -> Unit
            }
            return if (plan.weekIndex == plan.progress.weeks.lastIndex) PlanAhead else session(next)
        }

        /** D117: the session action — the open session's kind, or the next session — the plan screen's, whatever the week. */
        fun session(next: PlannedTick?): MainAction = next?.let { PlanOpen(it.session.kind) } ?: PlanNext
    }
}

/** D95, D97: what the Trainer screen shows of the weekly plan. */
sealed interface PlanCard {
    /** No plan runs, or one ended more than fourteen days ago: the card offers an evaluation. */
    data object None : PlanCard

    /**
     * [weekIndex] is 0 for week 1, and -1 before it starts (kept Friday to Sunday). [replacedFrom]: a plan replaced
     * from next Monday still runs this week (release 1's limit 3); the Monday the new one takes over, else null.
     */
    data class Running(
        val programme: Programme,
        val progress: PlanProgress,
        val weekIndex: Int,
        val replacedFrom: Long? = null,
    ) : PlanCard {
        /** This week's first unticked planned session, with its week counted from 1; none before week 1. */
        val next: PlannedTick?
            get() = if (weekIndex in progress.weeks.indices) {
                progress.weeks[weekIndex].next?.let { PlannedTick(weekIndex + 1, progress.weeks.size, it) }
            } else {
                null
            }
    }

    /** The day after its last Sunday, for fourteen days. */
    data class Ended(val programme: Programme, val progress: PlanProgress) : PlanCard

    companion object {
        /** [workouts]: the record over the plan's weeks; only its counted sessions after [counting]'s moment tick (D95, D105). */
        fun of(running: Programme?, workouts: List<Workout>, today: Long, counting: PlanCounting): PlanCard {
            val start = running?.startEpochDay
            if (running == null || start == null) return None
            // Release 1's limit 3: a version replaced from next Monday runs to its stop day, its Sunday.
            val finishing = Programmes.finishing(running, today)
            if (running.status != ProgrammeStatus.RUNNING && !finishing) return None
            val last = ProgrammeCalendar.lastDay(start, running.ask.weeks)
            val progress = PlanProgress.of(running.plan, start, workouts, minOf(today, last), counting)
            val replacedFrom = if (finishing) running.stoppedEpochDay!! + 1 else null
            return when {
                today <= last -> Running(running, progress, ProgrammeCalendar.weekIndex(start, today).coerceAtLeast(-1), replacedFrom)
                ProgrammeCalendar.endedShown(start, running.ask.weeks, today) -> Ended(running, progress)
                else -> None
            }
        }
    }
}
