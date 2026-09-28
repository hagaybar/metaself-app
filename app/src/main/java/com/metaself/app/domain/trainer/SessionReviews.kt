package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/**
 * D92: the owner's review of a session may sit on any of its witnesses — written before two copies
 * were read as one, or on the copy that led before another arrived. Reviews stay stored under their
 * own workout id; this is how they are read beside sessions. Pure.
 */
object SessionReviews {

    /**
     * Every review under its own workout id, and, for each of [sessions] whose lead has none, the first
     * of its other witnesses' in lead order under the session's id. The lead's own review always wins.
     */
    fun bySession(sessions: List<Workout>, reviews: List<TrainerReview>): Map<Long, TrainerReview> {
        val byWorkout = reviews.associateBy { it.workoutId }
        val keyed = byWorkout.toMutableMap()
        sessions.forEach { session ->
            if (session.id !in byWorkout) {
                session.witnesses.firstNotNullOfOrNull { byWorkout[it.id] }?.let { keyed[session.id] = it }
            }
        }
        return keyed
    }
}
