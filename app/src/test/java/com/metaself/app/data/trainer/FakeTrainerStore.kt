package com.metaself.app.data.trainer

import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerReview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * An in-memory [TrainerStore]. Ids are assigned from 1; [workouts] is set by the test. Any method
 * named in [failing] throws, as a full disk would.
 */
class FakeTrainerStore(var failing: Set<String> = emptySet()) : TrainerStore {

    val plans = MutableStateFlow<List<TrainerPlan>>(emptyList())
    val reviews = MutableStateFlow<List<TrainerReview>>(emptyList())
    val workouts = MutableStateFlow<List<Workout>>(emptyList())

    private var nextPlanId = 1L
    private var nextReviewId = 1L

    private fun check(method: String) {
        if (method in failing) throw IllegalStateException("disk full")
    }

    override fun observeReviews(): Flow<List<TrainerReview>> = reviews

    override fun observeReviewedWorkouts(): Flow<List<Workout>> =
        combine(workouts, reviews) { all, reviewed ->
            val ids = reviewed.map { it.workoutId }.toSet()
            all.filter { it.id in ids }.sortedByDescending { it.startedAtMillis }
        }

    override suspend fun keptPlan(): TrainerPlan? {
        check("keptPlan")
        return plans.value.filter { it.kept }.maxByOrNull { it.createdAtMillis }
    }

    override suspend fun keptPlans(): List<TrainerPlan> {
        check("keptPlans")
        return plans.value.filter { it.kept }.sortedByDescending { it.createdAtMillis }
    }

    override suspend fun plans(ids: Collection<Long>): Map<Long, TrainerPlan> {
        check("plans")
        return plans.value.filter { it.id in ids }.associateBy { it.id }
    }

    override suspend fun workout(id: Long): Workout? {
        check("workout")
        return workouts.value.firstOrNull { it.id == id }
    }

    override suspend fun addPlan(plan: TrainerPlan): Long {
        check("addPlan")
        val id = nextPlanId++
        plans.value = plans.value + plan.copy(id = id, kept = false)
        return id
    }

    override suspend fun keep(planId: Long) {
        check("keep")
        // D122: one kept per place — the same planned session's, or the unlinked one.
        val slot = plans.value.firstOrNull { it.id == planId }?.slot
        plans.value = plans.value.map {
            when {
                it.id == planId -> it.copy(kept = true)
                it.slot == slot -> it.copy(kept = false)
                else -> it
            }
        }
    }

    override suspend fun unkeep(planId: Long) {
        check("unkeep")
        plans.value = plans.value.map { if (it.id == planId) it.copy(kept = false) else it }
    }

    override suspend fun reviewOf(workoutId: Long): TrainerReview? {
        check("reviewOf")
        return reviews.value.firstOrNull { it.workoutId == workoutId }
    }

    override suspend fun putReview(review: TrainerReview): Long {
        check("putReview")
        val existing = reviews.value.firstOrNull { it.workoutId == review.workoutId }
        val id = existing?.id ?: nextReviewId++
        reviews.value = reviews.value.filter { it.workoutId != review.workoutId } + review.copy(id = id)
        return id
    }

    override suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<Feedback> {
        check("latestFeedback")
        return reviews.value
            .filter { it.feedback != null && it.workoutId != exceptWorkoutId }
            .sortedByDescending { it.feedbackAtMillis ?: 0 }
            .take(count)
            .mapNotNull { it.feedback }
    }
}
