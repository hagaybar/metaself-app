package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/** A session with its review, for "Earlier sessions" (D85). */
data class ReviewedSession(val workout: Workout, val review: TrainerReview)

/**
 * The Trainer screen, top to bottom (D85): the session waiting for words, the kept plan still offered,
 * the weekly plan's card (D95, D97), and earlier reviewed sessions, newest first. A review is shown only beside its own session, so one
 * whose session left the record (a negative workout id, D88) is shown nowhere.
 */
data class TrainerHome(
    val waiting: Workout?,
    val keptPlan: TrainerPlan?,
    val earlier: List<ReviewedSession>,
    /** D95, D97: the weekly plan's card. */
    val plan: PlanCard = PlanCard.None,
    /** D96: the running plan's next session this week, for Plan my next session. */
    val next: PlannedTick? = null,
) {
    companion object {
        /** Today, yesterday and the day before (design question 10). */
        const val WAITING_DAYS = 3

        fun of(
            today: Long,
            nowMillis: Long,
            recent: List<Workout>,
            reviewed: List<Workout>,
            reviews: List<TrainerReview>,
            kept: TrainerPlan?,
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
            return TrainerHome(waiting, PlanMatch.offered(kept, nowMillis), earlier, plan, (plan as? PlanCard.Running)?.next)
        }
    }
}

/** D95, D97: what the Trainer screen shows of the weekly plan. */
sealed interface PlanCard {
    /** No plan runs, or one ended more than fourteen days ago: the card offers an evaluation. */
    data object None : PlanCard

    /** [weekIndex] is 0 for week 1, and -1 before it starts (kept Friday to Sunday). */
    data class Running(val programme: Programme, val progress: PlanProgress, val weekIndex: Int) : PlanCard {
        /** This week's first unticked planned session, with its week counted from 1; none before week 1. */
        val next: PlannedTick?
            get() = if (weekIndex in progress.weeks.indices) {
                progress.weeks[weekIndex].next?.let { PlannedTick(weekIndex + 1, it) }
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
            if (running == null || start == null || running.status != ProgrammeStatus.RUNNING) return None
            val last = ProgrammeCalendar.lastDay(start, running.ask.weeks)
            val progress = PlanProgress.of(running.plan, start, workouts, minOf(today, last), counting)
            return when {
                today <= last -> Running(running, progress, ProgrammeCalendar.weekIndex(start, today).coerceAtLeast(-1))
                ProgrammeCalendar.endedShown(start, running.ask.weeks, today) -> Ended(running, progress)
                else -> None
            }
        }
    }
}
