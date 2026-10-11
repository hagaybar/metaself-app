package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.TrainerResponse
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.GOAL_150_MIN
import com.metaself.app.domain.trainer.GoalVerdict
import com.metaself.app.domain.trainer.GoalView
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.Wish
import org.junit.jupiter.api.Test

/** The trainer's rows and the domain, both ways (D88). Every title, sentence and figure is invented. */
class TrainerMappingTest {

    /** D129: the trainer's stand on the goal is stored with the evaluation and read back; an older row has none. */
    @Test
    fun `a plan's goal_view is stored with its evaluation and reads back`() {
        val programme = Programme(
            4, 1_000, ProgrammeAsk(15, 3, goal = GOAL_15K), Evaluation("Invented headline", "Invented.", "Invented.", ""),
            WeeksPlan("Invented plan", List(15) { PlanWeek("", emptyList()) }, "Invented reason."), "a-model",
            goalView = GoalView(GoalVerdict.TOO_BIG, "Invented stand.", GOAL_150_MIN),
        )

        assertThat(programme.toEntity().toProgramme()).isEqualTo(programme)
        val older = programme.toEntity().copy(evaluation = TrainerResponse.encodeEvaluation(programme.evaluation!!))
        assertThat(older.toProgramme()).isEqualTo(programme.copy(goalView = null))
    }

    @Test
    fun `a stored plan reads back with its answers and its suggestion`() {
        val entity = TrainerPlanEntity(
            id = 3, createdAtMillis = 1_000, activity = "OUTDOOR_WALK", minutes = 60, feeling = "TIRED", wish = "EASY",
            words = null, suggestion = TrainerResponse.encodePlan(PLAN), model = "a-model", kept = true,
        )

        val plan = entity.toPlan()!!

        assertThat(plan.answers).isEqualTo(PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_60_OR_MORE, Feeling.TIRED, Wish.EASY, ""))
        assertThat(plan.plan).isEqualTo(PLAN)
        assertThat(plan.toEntity()).isEqualTo(entity)
    }

    /** D122: a suggestion kept for a planned session carries its place in the stored JSON; an older row carries none. */
    @Test
    fun `a plan kept for a planned session reads back with its place, and an older row with none`() {
        val linked = ENTITY.copy(suggestion = TrainerResponse.encodeStoredPlan(PLAN, SessionSlot(7, 1, 0)))

        assertThat(linked.toPlan()!!.slot).isEqualTo(SessionSlot(7, 1, 0))
        assertThat(linked.toPlan()!!.plan).isEqualTo(PLAN)
        assertThat(linked.toPlan()!!.toEntity()).isEqualTo(linked)
        assertThat(ENTITY.toPlan()!!.slot).isNull()
        assertThat(TrainerResponse.readPlan(linked.suggestion)).isEqualTo(PLAN)
        assertThat(TrainerResponse.readSlot("garbled")).isNull()
    }

    @Test
    fun `a plan whose answers or suggestion this version cannot read is none`() {
        assertThat(ENTITY.toPlan()).isNotNull()
        assertThat(ENTITY.copy(activity = "SWIM").toPlan()).isNull()
        assertThat(ENTITY.copy(minutes = 25).toPlan()).isNull()
        assertThat(ENTITY.copy(suggestion = "garbled").toPlan()).isNull()
    }

    @Test
    fun `a review reads back, and feedback that cannot be read is none`() {
        val review = TrainerReview(1, workoutId = 7, planId = 3, felt = Felt.HARD, words = "Invented.", feedback = FEEDBACK, feedbackAtMillis = 2_000, model = "m")

        assertThat(review.toEntity().toReview()).isEqualTo(review)
        assertThat(review.toEntity().copy(feedback = "garbled").toReview().feedback).isNull()
        assertThat(review.toEntity().copy(felt = "MEH").toReview().felt).isNull()
    }

    private companion object {
        val PLAN = SessionPlan(
            title = "Steady walk",
            steps = listOf(PlanStep(0, 10, "Warm up", "easy pace"), PlanStep(10, 40, "Walk", "zone 2"), PlanStep(40, 50, "Cool down", "")),
            why = "Invented reason.",
        )
        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.PARTLY)
        val ENTITY = TrainerPlanEntity(
            id = 1, createdAtMillis = 1_000, activity = "RUN", minutes = 30, feeling = "FRESH", wish = "PUSH",
            words = "Invented words.", suggestion = TrainerResponse.encodePlan(PLAN), model = "a-model", kept = false,
        )
    }
}
