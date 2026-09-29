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
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.domain.weight.WeightReading
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
    private val aboutMe = InMemoryAboutMeStore()
    private val programmes = FakeProgrammeStore()

    /** Today, as the phone reads it; [TEST_EPOCH_DAY] unless a test moves it. */
    private var day = TEST_EPOCH_DAY

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

    /**
     * The session vanished while the form was open (the band's app deleted it): the words are kept,
     * as a review without a workout (the backup keeps those too, D88), and nothing is asked.
     */
    @Test
    fun `a session no longer in the record keeps the words and asks nothing`() = runTest {
        store.workouts.value = emptyList()

        val outcome = ask().save(9, Felt.EASY, " Invented words. ", null, withFeedback = true)

        assertThat(outcome).isInstanceOf(AskTheTrainer.Reviewed.SessionGone::class.java)
        assertThat(outcome.review.words).isEqualTo("Invented words.")
        assertThat(store.reviewOf(9)!!.words).isEqualTo("Invented words.")
        assertThat(store.reviewOf(9)!!.felt).isEqualTo(Felt.EASY)
        assertThat(trainer.asked).isEmpty()
    }

    /** "Just save" after feedback changes the words and keeps the feedback already given. */
    @Test
    fun `just save after feedback keeps the feedback`() = runTest {
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        ask().save(1, Felt.HARD, "First words.", null, withFeedback = true)

        val outcome = ask().save(1, Felt.RIGHT, "Second words.", null, withFeedback = false)

        assertThat(outcome).isInstanceOf(AskTheTrainer.Reviewed.Saved::class.java)
        val stored = store.reviewOf(1)!!
        assertThat(stored.words).isEqualTo("Second words.")
        assertThat(stored.felt).isEqualTo(Felt.RIGHT)
        assertThat(stored.feedback).isEqualTo(FEEDBACK.copy(followed = PlanFollowed.NO_PLAN))
        assertThat(stored.feedbackAtMillis).isEqualTo(NOW)
        assertThat(trainer.asked).hasSize(1)
    }

    /** A restored review without a workout sits under a negative workout id (D88); its feedback still counts as earlier feedback. */
    @Test
    fun `feedback on a review without a workout is still sent as earlier feedback`() = runTest {
        store.putReview(TrainerReview(workoutId = -1, planId = null, felt = null, words = null, feedback = FEEDBACK, feedbackAtMillis = NOW - HOUR))
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        ask().suggest(ANSWERS)

        assertThat(trainer.asked.single().earlierFeedback).containsExactly(FEEDBACK)
    }

    /**
     * D89, D90: the request reads a year back — the record begins on 1 June 2026, and June's walk is
     * in June's line, though it is far outside the 42 days — and carries the owner's note.
     */
    @Test
    fun `the request holds the months and the note`() = runTest {
        val juneWalk = walk(id = 2, day = JUNE_1 + 9)
        record.workouts.value = record.workouts.value + juneWalk
        record.days.value = listOf(HealthDay(JUNE_1 + 9, steps = 6_000))
        record.earliest.value = JUNE_1
        aboutMe.save("Invented note.")
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        ask().suggest(ANSWERS)

        val asked = trainer.asked.single()
        assertThat(asked.aboutMe).isEqualTo("Invented note.")
        val june = asked.months.first()
        assertThat(june.firstDay).isEqualTo(JUNE_1)
        assertThat(june.sessions).isEqualTo(1)
        assertThat(june.stepsADay).isEqualTo(6_000)
        assertThat(asked.sessions.map { it.epochDay }).containsExactly(TEST_EPOCH_DAY)
    }

    // --- D93–D98 -----------------------------------------------------------------------------------

    @Test
    fun `an evaluation is stored as offered, with the form's answers, and sent with the start it would have`() = runTest {
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")

        val outcome = ask().evaluate(ProgrammeAsk(2, 2, "Invented words.")) as AskTheTrainer.Evaluated.Offered

        assertThat(outcome.programme.status).isEqualTo(ProgrammeStatus.OFFERED)
        assertThat(outcome.programme.evaluation).isEqualTo(EVALUATION)
        assertThat(programmes.rows.value).hasSize(1)
        val question = trainer.asked.single().question as TrainerQuestion.Evaluate
        assertThat(question.startEpochDay).isEqualTo(MONDAY)
        assertThat(question.last).isNull()
    }

    @Test
    fun `a failed evaluation stores nothing and says why`() = runTest {
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)

        assertThat(ask().evaluate(ProgrammeAsk(2, 2))).isEqualTo(AskTheTrainer.Evaluated.Failed(EstimateResult.NoKey))
        assertThat(programmes.rows.value).isEmpty()
    }

    @Test
    fun `keeping starts it from this week's Monday and replaces a running plan`() = runTest {
        val old = programmes.add(offered())
        programmes.keep(old, MONDAY - 14, TEST_EPOCH_DAY - 14)
        val new = programmes.add(offered())

        val start = ask().keepProgramme(new)

        assertThat(start).isEqualTo(MONDAY)
        assertThat(programmes.running()!!.id).isEqualTo(new)
        assertThat(programmes.rows.value.first { it.id == old }.status).isEqualTo(ProgrammeStatus.REPLACED)
    }

    /**
     * A stale screen's write is refused by the store; the refusal reaches the screen as a throw (its
     * `guarded` says ActionRefused), is not swallowed here, and changes nothing.
     */
    @Test
    fun `a refused keep, keep-adjusted or stop throws and changes nothing`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        val version = programmes.add(offered().copy(replacesId = id))
        programmes.stop(id, TEST_EPOCH_DAY)
        val before = programmes.rows.value

        assertThat(runCatching { ask().keepProgramme(id) }.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(runCatching { ask().keepAdjusted(programmes.rows.value.first { it.id == version }) }.exceptionOrNull())
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(runCatching { ask().stop(id) }.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(programmes.rows.value).isEqualTo(before)
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `the last kept evaluation is sent with the plan that ran and its done-counts`() = runTest {
        val first = programmes.add(offered())
        programmes.keep(first, MONDAY, TEST_EPOCH_DAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY), walk(id = 2, day = MONDAY + 1))
        record.workouts.value = store.workouts.value
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")

        ask().evaluate(ProgrammeAsk(2, 2))

        val last = (trainer.asked.single().question as TrainerQuestion.Evaluate).last!!
        assertThat(last.evaluation).isEqualTo(EVALUATION)
        assertThat(last.doneByWeek).containsExactly(2)
        assertThat(last.weeks.single().sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.DONE)
    }

    @Test
    fun `the plan form's question carries the next planned session`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        assertThat(ask().nextPlanned()).isEqualTo(PlannedTick(1, WALK_30))
        ask().suggest(ANSWERS)

        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).planned).isEqualTo(PlannedTick(1, WALK_30))
    }

    @Test
    fun `feedback is told which planned session the session ticked`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.feedback += TrainerReply.Answered(FEEDBACK.copy(followed = PlanFollowed.NO_PLAN), "a-model")

        ask().save(workoutId = 1, felt = Felt.RIGHT, words = "", planId = null, withFeedback = true)

        assertThat((trainer.asked.single().question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(1, WALK_30))
    }

    @Test
    fun `adjusting sends the phone's counts and composes past weeks, this week's ticks and the answer`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY - 7), walk(id = 2, day = MONDAY))
        record.workouts.value = store.workouts.value
        val rest = WeeksPlan("Invented new", listOf(PlanWeek("lighter", listOf(WALK_20))), "Invented.")
        trainer.adjustments += TrainerReply.Answered(rest, "a-model")

        val outcome = ask().adjust("Invented words.") as AskTheTrainer.Adjusted.Offered

        val question = trainer.asked.single().question as TrainerQuestion.Adjust
        assertThat(question.weekIndex).isEqualTo(1)
        assertThat(question.doneByWeek).containsExactly(1)
        assertThat(question.tickedThisWeek).containsExactly(WALK_30)
        assertThat(question.thisWeekMax).isEqualTo(1)
        val composed = outcome.programme.plan
        assertThat(composed.weeks.first()).isEqualTo(WEEKS.weeks.first())
        assertThat(composed.weeks[1].sessions).containsExactly(WALK_30, WALK_20).inOrder()
        assertThat(outcome.programme.replacesId).isEqualTo(id)
        assertThat(outcome.programme.status).isEqualTo(ProgrammeStatus.OFFERED)
    }

    @Test
    fun `with no plan running, adjusting asks nothing`() = runTest {
        assertThat(ask().adjust("Invented.")).isEqualTo(AskTheTrainer.Adjusted.NotRunning)
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `keeping the adjusted version runs it from the old start, and stopping ends it today`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        val version = programmes.add(offered().copy(replacesId = id))

        ask().keepAdjusted(programmes.rows.value.first { it.id == version })
        assertThat(programmes.running()!!.id).isEqualTo(version)
        assertThat(programmes.running()!!.startEpochDay).isEqualTo(MONDAY)

        ask().stop(version)
        assertThat(programmes.running()).isNull()
        assertThat(programmes.rows.value.first { it.id == version }.stoppedEpochDay).isEqualTo(TEST_EPOCH_DAY)
    }

    /** D96: the plan has ended by date, but a session on its last Sunday still ticked, and feedback is told so. */
    @Test
    fun `feedback the day after the plan ends is told which planned session the last day's session ticked`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 14, TEST_EPOCH_DAY - 14)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY - 1))
        record.workouts.value = store.workouts.value
        day = MONDAY
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")

        ask().save(workoutId = 1, felt = Felt.RIGHT, words = "", planId = null, withFeedback = true)

        assertThat((trainer.asked.single().question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(2, WALK_30))
    }

    /** D94: asked on a Thursday, the plan would start this Monday; kept on the Friday, it starts next Monday. */
    @Test
    fun `evaluated on Thursday and kept on Friday, the plan starts the next Monday`() = runTest {
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")
        val offered = ask().evaluate(ProgrammeAsk(2, 2)) as AskTheTrainer.Evaluated.Offered
        assertThat((trainer.asked.single().question as TrainerQuestion.Evaluate).startEpochDay).isEqualTo(MONDAY)

        day = TEST_EPOCH_DAY + 1
        val start = ask().keepProgramme(offered.programme.id)

        assertThat(start).isEqualTo(MONDAY + 7)
        assertThat(programmes.running()!!.startEpochDay).isEqualTo(MONDAY + 7)
    }

    /** Design question 4: adjusted before week 1 starts, every week is rewritten and nothing is ticked. */
    @Test
    fun `adjusting before week 1 rewrites every week`() = runTest {
        day = TEST_EPOCH_DAY + 1
        val id = programmes.add(offered())
        ask().keepProgramme(id)
        day = TEST_EPOCH_DAY + 2
        val rest = WeeksPlan("Invented new", listOf(PlanWeek("a", listOf(WALK_20)), PlanWeek("b", listOf(WALK_20, WALK_30))), "Invented.")
        trainer.adjustments += TrainerReply.Answered(rest, "a-model")

        val outcome = ask().adjust("Invented.") as AskTheTrainer.Adjusted.Offered

        val question = trainer.asked.single().question as TrainerQuestion.Adjust
        assertThat(question.weekIndex).isEqualTo(0)
        assertThat(question.doneByWeek).isEmpty()
        assertThat(question.tickedThisWeek).isEmpty()
        assertThat(question.thisWeekMax).isEqualTo(2)
        assertThat(outcome.programme.plan.weeks).isEqualTo(rest.weeks)
    }

    /**
     * Design questions 1 and 2: the last evaluation is sent with the newest kept version of its plan,
     * dated the day the evaluation was made; the version was stopped on week 2's Monday, so a session
     * after that day is not counted.
     */
    @Test
    fun `the last evaluation follows its adjusted chain and stops counting on the stop day`() = runTest {
        val first = programmes.add(offered().copy(createdAtMillis = (MONDAY - 8) * DAY + 12 * HOUR))
        programmes.keep(first, MONDAY - 7, MONDAY - 7)
        val adjusted = WeeksPlan("Invented adjusted", listOf(PlanWeek("w", listOf(WALK_30, WALK_30)), PlanWeek("x", listOf(WALK_20, WALK_20))), "Invented.")
        val version = programmes.add(offered().copy(evaluation = null, plan = adjusted, replacesId = first))
        programmes.keepAdjusted(version, first, MONDAY - 5)
        programmes.stop(version, MONDAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY - 7), walk(id = 2, day = MONDAY), walk(id = 3, day = MONDAY + 1))
        record.workouts.value = store.workouts.value
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")

        ask().evaluate(ProgrammeAsk(2, 2))

        val last = (trainer.asked.single().question as TrainerQuestion.Evaluate).last!!
        assertThat(last.plan).isEqualTo(adjusted)
        assertThat(last.epochDay).isEqualTo(MONDAY - 8)
        assertThat(last.evaluation).isEqualTo(EVALUATION)
        assertThat(last.doneByWeek).containsExactly(1, 1).inOrder()
    }

    // --- D105 -------------------------------------------------------------------------------------

    /** Kept at noon on Thursday: walks at 08:00 on the Tuesday and that morning came before it. */
    @Test
    fun `a session before the plan was kept ticks nothing`() = runTest {
        val id = programmes.add(offered().copy(createdAtMillis = TEST_EPOCH_DAY * DAY + 12 * HOUR))
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY + 1), walk(id = 2, day = TEST_EPOCH_DAY))
        record.workouts.value = store.workouts.value

        assertThat(ask().running()!!.progress.done).isEqualTo(0)
        assertThat(ask().plannedTickOf(2)).isNull()
    }

    /**
     * An adjusted version counts from its first version's keep, and the answer is stored under that first
     * version. Invented: kept Wednesday noon, adjusted Thursday; a walk Wednesday morning does not count, a
     * twenty-minute walk on Thursday morning is a candidate for a thirty-minute place.
     */
    @Test
    fun `an adjusted plan counts from its first keep, and a candidate's answer is stored under it`() = runTest {
        val first = programmes.add(offered().copy(createdAtMillis = (MONDAY + 2) * DAY + 12 * HOUR))
        programmes.keep(first, MONDAY, MONDAY + 2)
        val version = programmes.add(offered().copy(evaluation = null, replacesId = first, createdAtMillis = NOW - HOUR))
        programmes.keepAdjusted(version, first, TEST_EPOCH_DAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY + 2), walk(id = 2, day = TEST_EPOCH_DAY).copy(durationMinutes = 20))
        record.workouts.value = store.workouts.value

        val before = ask().running()!!.progress.weeks.first()
        assertThat(before.done).isEqualTo(0)
        assertThat(before.ticks.first().candidate?.id).isEqualTo(2L)

        ask().answerCandidate(2, confirmed = true)

        assertThat(programmes.confirmationRows.value).containsExactly(PlanConfirmation(first, 2, true, NOW))
        val after = ask().running()!!.progress.weeks.first()
        assertThat(after.done).isEqualTo(1)
        assertThat(after.ticks.first().short).isTrue()
    }

    /**
     * A shorter session said yes to keeps its place when an adjustment adds a shorter planned session:
     * the twenty-minute walk stays on the thirty-minute place, and the new twenty-minute one is still to do.
     */
    @Test
    fun `a session said yes to keeps its place after an adjustment adds a shorter one`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, MONDAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY + 1).copy(durationMinutes = 20))
        record.workouts.value = store.workouts.value
        ask().answerCandidate(1, confirmed = true)
        trainer.adjustments += TrainerReply.Answered(
            WeeksPlan("Invented new", listOf(PlanWeek("w", listOf(WALK_20)), PlanWeek("x", listOf(WALK_20))), "Invented."),
            "a-model",
        )
        val version = (ask().adjust("Invented.") as AskTheTrainer.Adjusted.Offered).programme
        assertThat(version.plan.weeks.first().sessions).containsExactly(WALK_30, WALK_20).inOrder()

        ask().keepAdjusted(version)

        val week = ask().running()!!.progress.weeks.first()
        assertThat(week.ticks.map { it.by?.id }).containsExactly(1L, null).inOrder()
        assertThat(week.ticks.first().short).isTrue()
        assertThat(week.next).isEqualTo(WALK_20)
    }

    @Test
    fun `answering a session that is not waiting for an answer is refused, and nothing is stored`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        store.workouts.value = listOf(walk(id = 1, day = MONDAY), walk(id = 2, day = MONDAY + 1).copy(durationMinutes = 20))
        record.workouts.value = store.workouts.value

        assertThat(runCatching { ask().answerCandidate(1, confirmed = true) }.isFailure).isTrue()
        assertThat(runCatching { ask().answerCandidate(9, confirmed = true) }.isFailure).isTrue()
        ask().answerCandidate(2, confirmed = false)
        assertThat(runCatching { ask().answerCandidate(2, confirmed = true) }.isFailure).isTrue()

        assertThat(programmes.confirmationRows.value).containsExactly(PlanConfirmation(id, 2, false, NOW))
    }

    /** Invented: last week a full walk and a confirmed shorter one; this week a ten-minute walk. */
    @Test
    fun `adjusting tells the trainer each session's outcome and the attempts, and counts a confirmed one as done`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        store.workouts.value = listOf(
            walk(id = 1, day = MONDAY - 7),
            walk(id = 2, day = MONDAY - 6).copy(durationMinutes = 20),
            walk(id = 3, day = MONDAY).copy(durationMinutes = 10),
        )
        record.workouts.value = store.workouts.value
        programmes.confirm(PlanConfirmation(id, 2, true, NOW - DAY))
        trainer.adjustments += TrainerReply.Answered(WeeksPlan("Invented new", listOf(PlanWeek("w", listOf(WALK_20))), "Invented."), "a-model")

        ask().adjust("Invented.")

        val question = trainer.asked.single().question as TrainerQuestion.Adjust
        assertThat(question.doneByWeek).containsExactly(2)
        assertThat(question.howItWent.map { it.week }).containsExactly(1, 2).inOrder()
        assertThat(question.howItWent[0].sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.DONE_SHORT).inOrder()
        assertThat(question.howItWent[0].sessions.map { it.minutesDone }).containsExactly(40, 20).inOrder()
        assertThat(question.howItWent[1].attempts).containsExactly(AttemptFacts(WorkoutKind.WALK, 10, 30))
        assertThat(question.thisWeekMax).isEqualTo(2)
    }

    private fun ask() = AskTheTrainer(
        record, store, weights, FakeProfileRepository(aProfile()), trainer, aboutMe, programmes,
        Today { LocalDate.ofEpochDay(day) }, Now { NOW }, CurrentYear { TEST_YEAR },
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
        val JUNE_1 = LocalDate.of(2026, 6, 1).toEpochDay()
        val ANSWERS = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE, "Invented words.")
        val PLAN = SessionPlan(
            "Steady walk",
            listOf(PlanStep(0, 10, "Warm up", "easy pace"), PlanStep(10, 35, "Walk", "zone 2"), PlanStep(35, 45, "Cool down", "")),
            "Invented reason.",
        )
        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
        val MONDAY = TEST_EPOCH_DAY - 3
        val WALK_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
        val WALK_20 = PlannedSession(WorkoutKind.WALK, 20, PlannedEffort.EASY, "Short walk")
        val WEEKS = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK_30, WALK_30)) }, "Invented reason.")
        val EVALUATION = Evaluation("Invented headline.", "Invented.", "Invented.", "")

        /**
         * An evaluation made at noon, so its day is the same in any zone within eleven hours of UTC — on the
         * Sunday before the earliest plan here starts, so every session in these tests comes after it (D105).
         */
        fun offered() = Programme(0, (MONDAY - 15) * DAY + 12 * HOUR, ProgrammeAsk(2, 2), EVALUATION, WEEKS, "a-model")
    }
}
