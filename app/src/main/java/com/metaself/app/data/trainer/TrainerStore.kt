package com.metaself.app.data.trainer

import com.metaself.app.data.ai.TrainerResponse
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.health.WorkoutSessions
import com.metaself.app.data.health.toWorkout
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** What the Movement screen reads of the trainer: which sessions have a review (D85, design question 7). */
interface TrainerReviews {
    fun observeReviews(): Flow<List<TrainerReview>>

    companion object {
        val NONE = object : TrainerReviews {
            override fun observeReviews(): Flow<List<TrainerReview>> = flowOf(emptyList())
        }
    }
}

/** The trainer's plans and the owner's reviews (D88). */
interface TrainerStore : TrainerReviews {
    fun observeReviewedWorkouts(): Flow<List<Workout>>
    /** The newest kept suggestion, whatever it is for: what a review is matched against (D87). */
    suspend fun keptPlan(): TrainerPlan?

    /** D122: every kept suggestion, newest first — one per planned session, and the unlinked one. */
    suspend fun keptPlans(): List<TrainerPlan>
    suspend fun plans(ids: Collection<Long>): Map<Long, TrainerPlan>
    suspend fun workout(id: Long): Workout?

    /** Stores a new suggestion, not kept; its id. */
    suspend fun addPlan(plan: TrainerPlan): Long

    /**
     * Keeps [planId], in one transaction (D86): the one kept before for the same planned session — or, with
     * none named, the kept unlinked one — is no longer kept (D122). Other places' are left.
     */
    suspend fun keep(planId: Long)

    suspend fun unkeep(planId: Long)
    suspend fun reviewOf(workoutId: Long): TrainerReview?

    /** Inserts or replaces the one review of its workout; its id. */
    suspend fun putReview(review: TrainerReview): Long

    /** Newest first, at most [count], leaving out [exceptWorkoutId]'s. */
    suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<Feedback>
}

class RoomTrainerStore @Inject constructor(
    private val dao: TrainerDao,
    private val transaction: DatabaseTransaction,
    private val sessions: WorkoutSessions,
) : TrainerStore {

    override fun observeReviews(): Flow<List<TrainerReview>> = dao.observeReviews().map { rows -> rows.map { it.toReview() } }
    override fun observeReviewedWorkouts(): Flow<List<Workout>> = dao.observeReviewedWorkouts().map { rows -> rows.map { it.toWorkout() } }
    override suspend fun keptPlan(): TrainerPlan? = dao.kept()?.toPlan()
    override suspend fun keptPlans(): List<TrainerPlan> = dao.keptPlans().mapNotNull { it.toPlan() }
    /** A few hundred ids per query: an older SQLite allows 999 parameters in one statement. */
    override suspend fun plans(ids: Collection<Long>): Map<Long, TrainerPlan> =
        ids.distinct().chunked(IDS_PER_QUERY).flatMap { dao.plans(it) }.mapNotNull { it.toPlan() }.associateBy { it.id }
    /** D92: the session [id] is a witness of, so a review shows and sends the combined figures. */
    override suspend fun workout(id: Long): Workout? = sessions.sessionOf(id)
    override suspend fun addPlan(plan: TrainerPlan): Long = dao.insertPlan(plan.copy(id = 0, kept = false).toEntity())

    override suspend fun keep(planId: Long) = transaction.run {
        val slot = dao.plan(planId)?.let { TrainerResponse.readSlot(it.suggestion) }
        dao.keptPlans()
            .filter { it.id != planId && TrainerResponse.readSlot(it.suggestion) == slot }
            .forEach { dao.setKept(it.id, false) }
        dao.setKept(planId, true)
    }

    override suspend fun unkeep(planId: Long) = dao.setKept(planId, false)
    override suspend fun reviewOf(workoutId: Long): TrainerReview? = dao.reviewOf(workoutId)?.toReview()

    /** Read and written in one transaction, so no other write of the same workout's review falls between. */
    override suspend fun putReview(review: TrainerReview): Long {
        var id = 0L
        transaction.run {
            val existing = dao.reviewOf(review.workoutId)
            id = if (existing == null) {
                dao.insertReview(review.copy(id = 0).toEntity())
            } else {
                dao.updateReview(review.copy(id = existing.id).toEntity())
                existing.id
            }
        }
        return id
    }

    override suspend fun latestFeedback(count: Int, exceptWorkoutId: Long): List<Feedback> =
        dao.latestFeedback(count, exceptWorkoutId).mapNotNull(TrainerResponse::readFeedback)

    private companion object {
        const val IDS_PER_QUERY = 500
    }
}

/** Null when this version cannot read the answers or the suggestion: such a plan is offered nowhere. */
fun TrainerPlanEntity.toPlan(): TrainerPlan? {
    val answers = PlanAnswers(
        activity = PlanActivity.entries.firstOrNull { it.name == activity } ?: return null,
        time = TimeAvailable.ofMinutes(minutes) ?: return null,
        feeling = Feeling.entries.firstOrNull { it.name == feeling } ?: return null,
        wish = Wish.entries.firstOrNull { it.name == wish } ?: return null,
        words = words.orEmpty(),
    )
    val plan = TrainerResponse.readPlan(suggestion) ?: return null
    return TrainerPlan(id, createdAtMillis, answers, plan, model, kept, TrainerResponse.readSlot(suggestion))
}

fun TrainerPlan.toEntity(): TrainerPlanEntity = TrainerPlanEntity(
    id = id, createdAtMillis = createdAtMillis, activity = answers.activity.name, minutes = answers.time.minutes,
    feeling = answers.feeling.name, wish = answers.wish.name, words = answers.words.trim().ifEmpty { null },
    suggestion = TrainerResponse.encodeStoredPlan(plan, slot), model = model, kept = kept,
)

fun TrainerReviewEntity.toReview(): TrainerReview = TrainerReview(
    id = id, workoutId = workoutId, planId = planId,
    felt = Felt.entries.firstOrNull { it.name == felt },
    words = words, feedback = TrainerResponse.readFeedback(feedback),
    feedbackAtMillis = feedbackAtMillis, model = model,
)

fun TrainerReview.toEntity(): TrainerReviewEntity = TrainerReviewEntity(
    id = id, workoutId = workoutId, planId = planId, felt = felt?.name, words = words,
    feedback = feedback?.let(TrainerResponse::encodeFeedback), feedbackAtMillis = feedbackAtMillis, model = model,
)
