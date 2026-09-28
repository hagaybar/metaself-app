package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

/**
 * The trainer's two uses, from the stored record (D84, D86, D87). Today is [TEST_EPOCH_DAY], now is
 * 15:00 that day (UTC); every figure, answer and word is invented.
 */
class AskTheTrainerTest {

    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val weights = InMemoryWeightRepository()
    private val trainer = FakeTrainer()

    @BeforeEach
    fun setUp() {
        store.workouts.value = listOf(walk(id = 1, day = TEST_EPOCH_DAY))
        record.workouts.value = store.workouts.value
    }

    @Test
    fun `a suggestion is stored, not kept, and returned`() = runTest {
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        val outcome = ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned

        assertThat(outcome.plan.plan).isEqualTo(PLAN)
        assertThat(outcome.plan.kept).isFalse()
        assertThat(outcome.plan.model).isEqualTo("a-model")
        assertThat(store.plans(listOf(outcome.plan.id)).values.single().answers).isEqualTo(ANSWERS)
        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).answers).isEqualTo(ANSWERS)
    }

    @Test
    fun `a failed suggestion stores nothing and says why`() = runTest {
        trainer.plans += TrainerReply.Failed(EstimateResult.NoKey)

        assertThat(ask().suggest(ANSWERS)).isEqualTo(AskTheTrainer.Suggested.Failed(EstimateResult.NoKey))
        assertThat(store.plans(listOf(1L))).isEmpty()
    }

    @Test
    fun `just save stores the words and asks nothing`() = runTest {
        val outcome = ask().save(workoutId = 1, felt = Felt.RIGHT, words = " Invented words. ", planId = null, withFeedback = false)

        assertThat(outcome).isInstanceOf(AskTheTrainer.Reviewed.Saved::class.java)
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented words.")
        assertThat(trainer.asked).isEmpty()
    }

    /** D87: feedback is stored with the review, and the kept plan it used is cleared. */
    @Test
    fun `feedback is stored and clears the kept plan it used`() = runTest {
        val planId = store.addPlan(aStoredPlan()); store.keep(planId)
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")

        val outcome = ask().save(1, Felt.HARD, "Invented.", planId, withFeedback = true) as AskTheTrainer.Reviewed.WithFeedback

        assertThat(outcome.review.feedback).isEqualTo(FEEDBACK)
        assertThat(outcome.review.feedbackAtMillis).isEqualTo(NOW)
        assertThat(store.reviewOf(1)!!.feedback).isEqualTo(FEEDBACK)
        assertThat(store.keptPlan()).isNull()
    }

    /** Design question 9: with no plan matched, "followed its plan" is "no plan", whatever the model said. */
    @Test
    fun `with no plan the feedback is stored as no plan, whatever the model judged`() = runTest {
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")

        val outcome = ask().save(1, Felt.RIGHT, "Invented.", planId = null, withFeedback = true) as AskTheTrainer.Reviewed.WithFeedback

        assertThat(outcome.review.feedback!!.followed).isEqualTo(PlanFollowed.NO_PLAN)
        assertThat(store.reviewOf(1)!!.feedback!!.followed).isEqualTo(PlanFollowed.NO_PLAN)
        assertThat(outcome.review.feedback!!.copy(followed = PlanFollowed.YES)).isEqualTo(FEEDBACK)
    }

    /** A plan id that no longer finds a stored plan is no plan too: nothing was sent to judge against. */
    @Test
    fun `a plan that cannot be found counts as no plan`() = runTest {
        trainer.feedback += TrainerReply.Answered(FEEDBACK.copy(followed = PlanFollowed.PARTLY), "a-model")

        val outcome = ask().save(1, Felt.RIGHT, "Invented.", planId = 77, withFeedback = true) as AskTheTrainer.Reviewed.WithFeedback

        assertThat((trainer.asked.single().question as TrainerQuestion.Review).session.plan).isNull()
        assertThat(outcome.review.feedback!!.followed).isEqualTo(PlanFollowed.NO_PLAN)
    }

    /** With a plan matched, the model's judgement stands. */
    @Test
    fun `with a plan the model's judgement is kept`() = runTest {
        val planId = store.addPlan(aStoredPlan())
        trainer.feedback += TrainerReply.Answered(FEEDBACK.copy(followed = PlanFollowed.PARTLY), "a-model")

        val outcome = ask().save(1, Felt.RIGHT, "Invented.", planId, withFeedback = true) as AskTheTrainer.Reviewed.WithFeedback

        assertThat(outcome.review.feedback!!.followed).isEqualTo(PlanFollowed.PARTLY)
    }

    /** D87: if the call fails, the words are saved anyway. */
    @Test
    fun `a failed feedback call keeps the words`() = runTest {
        trainer.feedback += TrainerReply.Failed(EstimateResult.Unreachable())

        val outcome = ask().save(1, null, "Invented.", null, withFeedback = true) as AskTheTrainer.Reviewed.NoFeedback

        assertThat(outcome.failure).isEqualTo(EstimateResult.Unreachable())
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented.")
        assertThat(store.reviewOf(1)!!.feedback).isNull()
    }

    @Test
    fun `the feedback request is built from the record, with the session asked about`() = runTest {
        record.workouts.value = listOf(walk(id = 1, day = TEST_EPOCH_DAY))
        weights.log(WeightReading(TEST_EPOCH_DAY, 80.0))
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")

        ask().save(1, Felt.RIGHT, "Invented.", null, withFeedback = true)

        val request = trainer.asked.single()
        val question = request.question as TrainerQuestion.Review
        assertThat(question.session.felt).isEqualTo(Felt.RIGHT)
        assertThat(request.sessions.single().words).isEqualTo("Invented.")
        assertThat(request.weight!!.trendKg).isEqualTo(80.0)
        assertThat(request.body!!.ageYears).isEqualTo(46)
    }

    @Test
    fun `a session no longer in the record cannot be asked about`() = runTest {
        store.workouts.value = emptyList()

        assertThrows<IllegalStateException> { ask().save(9, Felt.EASY, "", null, withFeedback = true) }
    }

    private fun ask() = AskTheTrainer(
        record, store, weights, FakeProfileRepository(aProfile()), trainer,
        Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { NOW }, CurrentYear { TEST_YEAR },
    )

    /** A synced forty-minute walk starting at 08:00 on [day]. Invented. */
    private fun walk(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 8 * HOUR, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = 4_000, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private fun aStoredPlan() = TrainerPlan(0, NOW - HOUR, ANSWERS, PLAN, "a-model", kept = false)

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
        val ANSWERS = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE, "Invented words.")
        val PLAN = SessionPlan(
            "Steady walk",
            listOf(PlanStep(0, 10, "Warm up", "easy pace"), PlanStep(10, 35, "Walk", "zone 2"), PlanStep(35, 45, "Cool down", "")),
            "Invented reason.",
        )
        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
    }
}
