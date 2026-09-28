package com.metaself.app.data.trainer

import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.TrainerReview
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The trainer's two uses (D86, D87): every request is built here, fresh, from the stored record, and
 * only when a screen asks — never in the background (D84). Writes throw; the screens catch them
 * (`guarded`) and say so.
 */
class AskTheTrainer @Inject constructor(
    private val record: MovementRecord,
    private val store: TrainerStore,
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val trainer: Trainer,
    private val today: Today,
    private val now: Now,
    private val year: CurrentYear,
) {

    sealed interface Suggested {
        data class Planned(val plan: TrainerPlan) : Suggested
        data class Failed(val failure: EstimateResult) : Suggested
    }

    sealed interface Reviewed {
        val review: TrainerReview

        data class Saved(override val review: TrainerReview) : Reviewed
        data class WithFeedback(override val review: TrainerReview) : Reviewed

        /** The words were saved; the call for feedback failed (D87). */
        data class NoFeedback(override val review: TrainerReview, val failure: EstimateResult) : Reviewed
    }

    suspend fun suggest(answers: PlanAnswers): Suggested =
        when (val reply = trainer.suggest(request(TrainerQuestion.Plan(answers), exceptWorkoutId = NO_WORKOUT))) {
            is TrainerReply.Failed -> Suggested.Failed(reply.failure)
            is TrainerReply.Answered -> {
                val plan = TrainerPlan(0, now(), answers, reply.value, reply.model, kept = false)
                Suggested.Planned(plan.copy(id = store.addPlan(plan)))
            }
        }

    suspend fun keep(planId: Long) = store.keep(planId)

    suspend fun save(workoutId: Long, felt: Felt?, words: String, planId: Long?, withFeedback: Boolean): Reviewed {
        val workout = store.workout(workoutId) ?: error("no session $workoutId")
        val before = store.reviewOf(workoutId)
        val review = (before ?: TrainerReview(workoutId = workoutId, planId = null, felt = null, words = null))
            .copy(planId = planId, felt = felt, words = words.trim().ifEmpty { null })
        val saved = review.copy(id = store.putReview(review))
        if (!withFeedback) return Reviewed.Saved(saved)

        val plan = planId?.let { store.plans(listOf(it))[it] }
        val question = TrainerRequest.reviewQuestion(workout, saved, plan)
        return when (val reply = trainer.feedback(request(question, exceptWorkoutId = workoutId))) {
            is TrainerReply.Failed -> Reviewed.NoFeedback(saved, reply.failure)
            is TrainerReply.Answered -> {
                // With no plan sent there is nothing to have followed, whatever the model judged
                // (design question 9); a planId whose plan is gone sends none either.
                val feedback = if (plan == null) reply.value.copy(followed = PlanFollowed.NO_PLAN) else reply.value
                val answered = saved.copy(feedback = feedback, feedbackAtMillis = now(), model = reply.model)
                store.putReview(answered)
                if (planId != null) store.unkeep(planId)
                Reviewed.WithFeedback(answered)
            }
        }
    }

    private suspend fun request(question: TrainerQuestion, exceptWorkoutId: Long): TrainerRequest {
        val day = today().toEpochDay()
        val reviews = store.observeReviews().first()
        return TrainerRequest.of(
            question = question,
            today = day,
            workouts = record.observeWorkouts(TrainerRequest.firstDay(day), day).first(),
            reviews = reviews,
            plans = store.plans(reviews.mapNotNull { it.planId }),
            days = record.observeDays(TrainerRequest.firstSummaryDay(day), day).first(),
            readings = weights.readings.first(),
            profile = profiles.profile.first(),
            currentYear = year(),
            earlierFeedback = store.latestFeedback(TrainerRequest.FEEDBACK_COUNT, exceptWorkoutId),
        )
    }

    private companion object {
        const val NO_WORKOUT = -1L
    }
}
