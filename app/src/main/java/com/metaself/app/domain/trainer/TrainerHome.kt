package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/** A session with its review, for "Earlier sessions" (D85). */
data class ReviewedSession(val workout: Workout, val review: TrainerReview)

/**
 * The Trainer screen, top to bottom (D85): the session waiting for words, the kept plan still offered,
 * and earlier reviewed sessions, newest first. A review is shown only beside its own session, so one
 * whose session left the record (a negative workout id, D88) is shown nowhere.
 */
data class TrainerHome(val waiting: Workout?, val keptPlan: TrainerPlan?, val earlier: List<ReviewedSession>) {
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
            return TrainerHome(waiting, PlanMatch.offered(kept, nowMillis), earlier)
        }
    }
}
