package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.letter.FakeFoodTotals
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.target.CurrentTarget
import com.metaself.app.domain.trainer.AttemptFacts
import com.metaself.app.domain.trainer.Block
import com.metaself.app.domain.trainer.BlockAnswer
import com.metaself.app.domain.trainer.FilledWeek
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.OUTLINE_15
import com.metaself.app.domain.trainer.Phase
import com.metaself.app.domain.trainer.PlanAheadAsk
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.STEADY_OUTLINE
import com.metaself.app.domain.trainer.target
import com.metaself.app.domain.trainer.DatedEvaluation
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.KindLedger
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanPlace
import com.metaself.app.domain.trainer.PlanStanding
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedOutcome
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.GoalSuggestion
import com.metaself.app.domain.trainer.GoalVerdict
import com.metaself.app.domain.trainer.GoalView
import com.metaself.app.domain.trainer.RunningGoal
import com.metaself.app.domain.trainer.GOAL_150_MIN
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeekNote
import com.metaself.app.domain.trainer.WeekNoteSource
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
    private val food = FakeFoodTotals()
    private val weekNotes = InMemoryWeekNoteStore()

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

    /**
     * D110, D111: six ledgers, newest first. The plan (two weeks of two 30-minute walks) began last Monday;
     * every walk is forty minutes. Last week one walk ticked one of two; this week two tick both and a third
     * is beyond the plan. Two days of food this week. Invented.
     */
    @Test
    fun `the request carries six week ledgers, with the plan's practice, the food and the target`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, TEST_EPOCH_DAY - 7)
        store.workouts.value = listOf(
            walk(id = 1, day = MONDAY - 7), walk(id = 2, day = MONDAY), walk(id = 3, day = MONDAY + 1), walk(id = 4, day = TEST_EPOCH_DAY),
        )
        record.workouts.value = store.workouts.value
        food.days = mapOf(MONDAY to DayTotals(2_000, 100, 200, 70), MONDAY + 1 to DayTotals(2_200, 120, 200, 70))
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        ask().suggest(ANSWERS)

        val weeks = trainer.asked.single().weeks
        assertThat(weeks.map { it.monday })
            .containsExactly(MONDAY, MONDAY - 7, MONDAY - 14, MONDAY - 21, MONDAY - 28, MONDAY - 35).inOrder()
        assertThat(weeks[0].practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.DONE)
        assertThat(weeks[0].beyond.kinds).containsExactly(KindLedger(WorkoutKind.WALK, 1, 40, 4_000, null, emptyList()))
        assertThat(weeks[1].practice!!.sessions.map { it.outcome }).containsExactly(PlannedOutcome.DONE, PlannedOutcome.NOT_DONE).inOrder()
        assertThat(weeks[1].beyond.kinds).isEmpty()
        assertThat(weeks[2].practice).isNull()
        assertThat(weeks[0].food).isEqualTo(FoodContext(2, 2_100, 110, CurrentTarget.of(aProfile(), null, TEST_YEAR, 0).kcal))
        assertThat(weeks[1].food.daysLogged).isEqualTo(0)
    }

    /** D110: the sixth week's usual needs the 28 days before its Monday; seven nights there are enough. Invented. */
    @Test
    fun `the request reads the 28 days before its first week, for recovery's usuals`() = runTest {
        val sixth = MONDAY - 35
        record.days.value = (1L..7L).map { HealthDay(sixth - it * 4, sleepMinutes = 420) }
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        ask().suggest(ANSWERS)

        assertThat(trainer.asked.single().weeks.last().recovery.usualSleepMinutes).isEqualTo(420)
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

        assertThat(ask().nextPlanned()).isEqualTo(PlannedTick(1, 2, WALK_30))
        ask().suggest(ANSWERS)

        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).planned).isEqualTo(PlannedTick(1, 2, WALK_30))
    }

    @Test
    fun `feedback is told which planned session the session ticked`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.feedback += TrainerReply.Answered(FEEDBACK.copy(followed = PlanFollowed.NO_PLAN), "a-model")

        ask().save(workoutId = 1, felt = Felt.RIGHT, words = "", planId = null, withFeedback = true)

        assertThat((trainer.asked.single().question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(1, 2, WALK_30))
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

    /** D116: every answer's week_note is kept, about this week, with the moment it came — whatever is done with the answer after. */
    @Test
    fun `each answer keeps its week_note, with the moment and this week`() = runTest {
        fun kept(text: String) = WeekNote(text, NOW, MONDAY, WeekNoteSource.TRAINER)

        trainer.plans += TrainerReply.Answered(PLAN, "a-model", "Invented plan note.")
        ask().suggest(ANSWERS)
        assertThat(weekNotes.note.value).isEqualTo(kept("Invented plan note."))

        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model", "Invented feedback note.")
        ask().save(1, Felt.RIGHT, "Invented.", null, withFeedback = true)
        assertThat(weekNotes.note.value).isEqualTo(kept("Invented feedback note."))

        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model", "Invented evaluation note.")
        ask().evaluate(ProgrammeAsk(2, 2))
        assertThat(weekNotes.note.value).isEqualTo(kept("Invented evaluation note."))

        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.adjustments += TrainerReply.Answered(WeeksPlan("Invented new", listOf(PlanWeek("w", listOf(WALK_20)), PlanWeek("w", listOf(WALK_20))), "Invented."), "a-model", "Invented adjust note.")
        assertThat(ask().adjust("Invented.")).isInstanceOf(AskTheTrainer.Adjusted.Offered::class.java)
        assertThat(weekNotes.note.value).isEqualTo(kept("Invented adjust note."))
    }

    /** D116: an answer without a note keeps none and still answers; a note that cannot be stored never fails the answer. */
    @Test
    fun `an answer without a note keeps none, and a note that cannot be kept fails nothing`() = runTest {
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        assertThat(ask().suggest(ANSWERS)).isInstanceOf(AskTheTrainer.Suggested.Planned::class.java)
        assertThat(weekNotes.note.value).isNull()

        weekNotes.failing = true
        trainer.plans += TrainerReply.Answered(PLAN, "a-model", "Invented plan note.")
        assertThat(ask().suggest(ANSWERS)).isInstanceOf(AskTheTrainer.Suggested.Planned::class.java)
        assertThat(weekNotes.note.value).isNull()
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

        assertThat((trainer.asked.single().question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(2, 2, WALK_30))
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

    // --- D117, D118 ------------------------------------------------------------------------------

    /**
     * Kept from the Monday a week ago: week 1 is past — two walks ticked its two planned walks, the third is
     * the week's own — and week 2 is this week, its first walk done Monday. The evaluation was made the Sunday
     * fifteen days before this Monday, at noon (in any zone within eleven hours of UTC, that Sunday).
     */
    @Test
    fun `the plan shown is the running one, drawn as its weeks, with its evaluation's day`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, MONDAY - 7)
        record.workouts.value = listOf(walk(1, MONDAY - 7), walk(2, MONDAY - 6), walk(3, MONDAY - 5), walk(4, MONDAY))

        val shown = ask().planShown()!!

        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.InWeek(2))
        assertThat(shown.timeline.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 2, planned = 2, own = 1))
        assertThat(shown.timeline.weeks[1].place).isEqualTo(
            TimelinePlace.ThisWeek(listOf(PlanTile(WALK_30, TileState.DONE, MONDAY, 40), PlanTile(WALK_30, TileState.OPEN))),
        )
        assertThat(shown.running!!.programme.id).isEqualTo(id)
        assertThat(shown.evaluation).isEqualTo(DatedEvaluation(EVALUATION, MONDAY - 15))
    }

    @Test
    fun `with nothing kept there is no plan to show, and a stopped plan is shown stopped for fourteen days`() = runTest {
        assertThat(ask().planShown()).isNull()
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, MONDAY - 7)
        programmes.stop(id, MONDAY + 1)

        val shown = ask().planShown()!!
        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.Stopped(MONDAY + 1))
        assertThat(shown.running).isNull()
        assertThat(shown.timeline.weeks.all { it.place is TimelinePlace.Past }).isTrue()

        day = MONDAY + 1 + 15
        assertThat(ask().planShown()).isNull()
    }

    @Test
    fun `an ended plan is shown, ended, with no running card`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 14, MONDAY - 14)

        val shown = ask().planShown()!!

        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.Ended(MONDAY - 1))
        assertThat(shown.running).isNull()
    }

    /** D118: the adjusted version offered keeps the weeks already past, and this week's done walk shows done. */
    @Test
    fun `an adjusted version not yet kept is drawn as it would be counted, with its chain's evaluation`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY - 7, MONDAY - 7)
        record.workouts.value = listOf(walk(1, MONDAY - 7), walk(2, MONDAY))
        val adjusted = WeeksPlan("Invented adjusted", listOf(PlanWeek("w", listOf(WALK_30, WALK_30)), PlanWeek("x", listOf(WALK_30, WALK_20))), "Invented.")
        val version = programmes.add(offered().copy(evaluation = null, plan = adjusted, replacesId = id))

        val shown = ask().planShownOf(programmes.rows.value.first { it.id == version }, MONDAY - 7)

        assertThat(shown.timeline.title).isEqualTo("Invented adjusted")
        assertThat(shown.timeline.weeks[0].place).isEqualTo(TimelinePlace.Past(done = 1, planned = 2, own = 0))
        assertThat(shown.timeline.weeks[1].place).isEqualTo(
            TimelinePlace.ThisWeek(listOf(PlanTile(WALK_30, TileState.DONE, MONDAY, 40), PlanTile(WALK_20, TileState.OPEN))),
        )
        assertThat(shown.running).isNull()
        assertThat(shown.evaluation).isEqualTo(DatedEvaluation(EVALUATION, MONDAY - 15))
    }

    /** D118: an evaluation's answer, never kept, counts nothing — this Monday's walk ticks no tile. */
    @Test
    fun `an answer never kept has every session open`() = runTest {
        record.workouts.value = listOf(walk(1, MONDAY))
        val id = programmes.add(offered())
        val programme = programmes.rows.value.first { it.id == id }

        val timeline = ask().timelineOf(programme, MONDAY)

        assertThat(timeline.weeks[0].place).isEqualTo(TimelinePlace.ThisWeek(listOf(PlanTile(WALK_30, TileState.OPEN), PlanTile(WALK_30, TileState.OPEN))))
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

    /**
     * D122: Plan my next session, while a plan runs, is for its next open session — today's walk ticked the
     * first, so the second — and keeping a second suggestion for it replaces the first.
     */
    @Test
    fun `keeping a suggestion attaches it to the next open session, and keeping another replaces it`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        val first = (ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned).plan
        ask().keep(first.id)
        val second = (ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned).plan
        ask().keep(second.id)

        assertThat(first.slot).isEqualTo(SessionSlot(id, 0, 1))
        assertThat(store.keptPlans().map { it.id }).containsExactly(second.id)
    }

    /** D123: Get the steps names a session of this week or next week; anything else attaches to nothing. */
    @Test
    fun `Get the steps attaches to the named session, this week's or next week's, and nothing further`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")

        assertThat(ask().plannedAt(SessionSlot(id, 1, 1))).isEqualTo(AskTheTrainer.PlannedFor(SessionSlot(id, 1, 1), PlannedTick(2, 2, WALK_30)))
        assertThat(ask().plannedAt(SessionSlot(id, 0, 0))).isNotNull()
        assertThat(ask().plannedAt(SessionSlot(id, 2, 0))).isNull()
        assertThat(ask().plannedAt(SessionSlot(id, 1, 2))).isNull()
        assertThat(ask().plannedAt(SessionSlot(id + 1, 1, 1))).isNull()

        val plan = (ask().suggest(ANSWERS, SessionSlot(id, 1, 1)) as AskTheTrainer.Suggested.Planned).plan

        assertThat(plan.slot).isEqualTo(SessionSlot(id, 1, 1))
        assertThat((trainer.asked.single().question as TrainerQuestion.Plan).planned).isEqualTo(PlannedTick(2, 2, WALK_30))
    }

    /** D122: the steps belong to the planned session; feedback on a session done with them leaves them there. */
    @Test
    fun `feedback leaves steps that belong to a planned session`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        val plan = (ask().suggest(ANSWERS) as AskTheTrainer.Suggested.Planned).plan
        ask().keep(plan.id)

        ask().save(1, Felt.RIGHT, "Invented.", plan.id, withFeedback = true)

        assertThat(store.keptPlans().map { it.id }).containsExactly(plan.id)
    }

    /** D122: with no planned session named, a kept suggestion is the Next session, for seven days. */
    @Test
    fun `the next session is the kept unlinked suggestion of the last seven days`() = runTest {
        val linked = store.addPlan(aStoredPlan().copy(slot = SessionSlot(9, 0, 0)))
        store.keep(linked)
        assertThat(ask().nextSession()).isNull()

        val recent = store.addPlan(aStoredPlan())
        store.keep(recent)
        assertThat(ask().nextSession()!!.id).isEqualTo(recent)

        val old = store.addPlan(aStoredPlan().copy(createdAtMillis = NOW - 8 * DAY))
        store.keep(old)
        assertThat(ask().nextSession()).isNull()
        assertThat(store.keptPlans().map { it.id }).containsExactly(linked, old)
    }

    /** D122, D123: the plan screen carries its own chain's steps by place, and the open tile with steps is ready. */
    @Test
    fun `the plan shown carries its chain's steps, and an open tile with steps is ready`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        val steps = store.addPlan(aStoredPlan().copy(slot = SessionSlot(id, 0, 1)))
        store.keep(steps)
        val elsewhere = store.addPlan(aStoredPlan().copy(slot = SessionSlot(id + 50, 0, 0)))
        store.keep(elsewhere)

        val shown = ask().planShown()!!

        assertThat(shown.steps.keys).containsExactly(PlanPlace(0, 1))
        assertThat(shown.steps.getValue(PlanPlace(0, 1)).id).isEqualTo(steps)
        val tiles = (shown.timeline.weeks[0].place as TimelinePlace.ThisWeek).tiles
        assertThat(tiles.map { it.state to it.stepsReady }).containsExactly(TileState.DONE to false, TileState.OPEN to true).inOrder()
    }

    /**
     * D124, release 1's limit 3: while a plan runs, a new one replaces it from next Monday; the old one stops on its
     * Sunday, replaced, and is the plan in effect until then.
     */
    @Test
    fun `while a plan runs, keeping a new one starts it next Monday and the old one counts through Sunday`() = runTest {
        assertThat(ask().replacingFrom()).isNull()
        val old = programmes.add(offered())
        programmes.keep(old, MONDAY, TEST_EPOCH_DAY)
        val new = programmes.add(offered())

        assertThat(ask().replacingFrom()).isEqualTo(MONDAY + 7)
        assertThat(ask().keepProgramme(new)).isEqualTo(MONDAY + 7)

        val stopped = programmes.rows.value.first { it.id == old }
        assertThat(stopped.status to stopped.stoppedEpochDay).isEqualTo(ProgrammeStatus.REPLACED to MONDAY + 6)
        assertThat(programmes.running()!!.let { it.id to it.startEpochDay }).isEqualTo(new to MONDAY + 7)
        // The rest of the week is the old plan's: its card, its open session, its ticks.
        val thisWeek = ask().running()!!
        assertThat(thisWeek.programme.id).isEqualTo(old)
        assertThat(thisWeek.replacedFrom).isEqualTo(MONDAY + 7)
        assertThat(thisWeek.next).isEqualTo(PlannedTick(1, 2, WALK_30))
        assertThat(ask().nextFor()!!.slot).isEqualTo(SessionSlot(old, 0, 1))
        assertThat(ask().replacingFrom()).isEqualTo(MONDAY + 7)
        assertThat(ask().adjust("Invented.")).isEqualTo(AskTheTrainer.Adjusted.NotRunning)
        assertThat(ask().planShown()!!.timeline.weeks.map { it.number }).containsExactly(1)

        day = MONDAY + 5
        record.workouts.value = record.workouts.value + walk(id = 2, day = MONDAY + 5)
        assertThat(ask().running()!!.progress.weeks.first().done).isEqualTo(2)

        day = MONDAY + 7
        val next = ask().running()!!
        assertThat(next.programme.id to next.weekIndex).isEqualTo(new to 0)
        assertThat(next.replacedFrom).isNull()
    }

    /** Release 1's limit 2: an adjustment puts the done session first; the open session's steps go with it. */
    @Test
    fun `steps follow their session when an adjustment reorders the week`() = runTest {
        val steady60 = PlannedSession(WorkoutKind.WALK, 60, PlannedEffort.STEADY, "Long walk")
        val plan = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(steady60, WALK_30)), PlanWeek("w", listOf(WALK_30))), "Invented.")
        val id = programmes.add(offered().copy(plan = plan))
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        // The forty-minute walk today ticks the thirty-minute session, the second; the first is open.
        val steps = store.addPlan(aStoredPlan().copy(slot = SessionSlot(id, 0, 0)))
        store.keep(steps)
        trainer.adjustments += TrainerReply.Answered(
            WeeksPlan("Invented new", listOf(PlanWeek("w", listOf(steady60)), PlanWeek("w", listOf(WALK_20))), "Invented."), "a-model",
        )

        val version = (ask().adjust("Invented.") as AskTheTrainer.Adjusted.Offered).programme
        assertThat(version.plan.weeks.first().sessions).containsExactly(WALK_30, steady60).inOrder()
        ask().keepAdjusted(version)

        assertThat(store.keptPlans().single().slot).isEqualTo(SessionSlot(id, 0, 1))
        assertThat(ask().planShown()!!.steps.keys).containsExactly(PlanPlace(0, 1))
    }

    /** Release 1's limit 2: a session the new version no longer has takes its steps with it. */
    @Test
    fun `steps whose session an adjustment removed are no longer kept`() = runTest {
        val swim = PlannedSession(WorkoutKind.SWIM, 30, PlannedEffort.EASY, "Easy swim")
        val plan = WeeksPlan("Invented", listOf(PlanWeek("w", listOf(WALK_30, swim)), PlanWeek("w", listOf(WALK_30))), "Invented.")
        val id = programmes.add(offered().copy(plan = plan))
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        val steps = store.addPlan(aStoredPlan().copy(slot = SessionSlot(id, 0, 1)))
        store.keep(steps)
        trainer.adjustments += TrainerReply.Answered(
            WeeksPlan("Invented new", listOf(PlanWeek("w", emptyList()), PlanWeek("w", listOf(WALK_20))), "Invented."), "a-model",
        )

        ask().keepAdjusted((ask().adjust("Invented.") as AskTheTrainer.Adjusted.Offered).programme)

        assertThat(store.keptPlans()).isEmpty()
    }

    /** Release 1's limit 1: a review is offered the steps of the planned session its workout ticked. */
    @Test
    fun `a review gets the steps kept for the session it ticked, else the newest kept of the seven days`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        val forFirst = store.addPlan(aStoredPlan().copy(createdAtMillis = NOW - 3 * HOUR, slot = SessionSlot(id, 0, 0)))
        store.keep(forFirst)
        val forSecond = store.addPlan(aStoredPlan().copy(createdAtMillis = NOW - 2 * HOUR, slot = SessionSlot(id, 0, 1)))
        store.keep(forSecond)
        val swim = walk(id = 5, day = TEST_EPOCH_DAY).copy(kind = WorkoutKind.SWIM, startedAtMillis = NOW + HOUR)

        assertThat(ask().stepsFor(walk(id = 1, day = TEST_EPOCH_DAY))!!.id).isEqualTo(forFirst)
        assertThat(ask().stepsFor(swim)!!.id).isEqualTo(forSecond)
    }

    // --- D126: suggested goals. Every figure, title and word is invented. -------------------------------------

    /** D126: one ask with the form's sessions a week and words, the running goal and outline; nothing stored but the note. */
    @Test
    fun `suggesting goals asks with the form and the running goal, keeps the note and stores nothing`() = runTest {
        keptOutlined(filled = 1)
        val suggested = GoalSuggestion("Invented title", GOAL_150_MIN, 3, "Invented why.")
        trainer.suggestions += TrainerReply.Answered(listOf(suggested), "a-model", "Invented note.")
        val before = programmes.all()

        val outcome = ask().suggestGoals(3, " Invented words. ")

        assertThat(outcome).isEqualTo(AskTheTrainer.Suggestions.Offered(listOf(suggested)))
        val question = trainer.asked.single().question as TrainerQuestion.SuggestGoals
        assertThat(question.perWeek).isEqualTo(3)
        assertThat(question.words).isEqualTo("Invented words.")
        assertThat(question.running).isEqualTo(RunningGoal(GOAL_15K, OUTLINE_15))
        assertThat(question.startEpochDay).isEqualTo(MONDAY + 7)
        assertThat(trainer.asked.single().weeks).hasSize(6)
        assertThat(weekNotes.note.value!!.text).isEqualTo("Invented note.")
        assertThat(programmes.all()).isEqualTo(before)

        trainer.suggestions += TrainerReply.Failed(EstimateResult.NoKey)
        assertThat(ask().suggestGoals(null, "")).isEqualTo(AskTheTrainer.Suggestions.Failed(EstimateResult.NoKey))
    }

    /** D126: with no plan running, no running goal is sent. */
    @Test
    fun `with no plan running, suggesting goals sends none`() = runTest {
        trainer.suggestions += TrainerReply.Failed(EstimateResult.NoKey)

        ask().suggestGoals(null, "")

        val question = trainer.asked.single().question as TrainerQuestion.SuggestGoals
        assertThat(question.running).isNull()
        assertThat(question.perWeek).isNull()
        assertThat(question.startEpochDay).isEqualTo(MONDAY)
    }

    /** D129: the trainer's stand on the goal is kept with the answer, and the plan page reads it with the evaluation. */
    @Test
    fun `the stand on the goal is stored with the answer and shown with the kept plan`() = runTest {
        val view = GoalView(GoalVerdict.TOO_BIG, "Invented stand.", GOAL_150_MIN)
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, outlined(filled = 2), view), "a-model")

        val offered = (ask().planAhead(PlanAheadAsk(GOAL_15K, 3)) as AskTheTrainer.Evaluated.Offered).programme
        assertThat(offered.goalView).isEqualTo(view)
        assertThat(programmes.all().single().goalView).isEqualTo(view)

        ask().keepProgramme(offered.id)
        assertThat(ask().planShown()!!.evaluation!!.goalView).isEqualTo(view)
    }

    // --- D119–D121, D124: goals and blocks. The goal fixture; every figure is invented. -----------------

    /** D124: Plan ahead's weeks follow from the goal, counted from the Monday keeping it would give. */
    @Test
    fun `planning ahead to a distance asks for the weeks to the goal, and keeps the goal with the plan`() = runTest {
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, outlined(filled = 2)), "a-model")

        val offered = (ask().planAhead(PlanAheadAsk(GOAL_15K, 3, "Invented.")) as AskTheTrainer.Evaluated.Offered).programme

        val question = trainer.asked.single().question as TrainerQuestion.Evaluate
        assertThat(question.ask).isEqualTo(ProgrammeAsk(15, 3, "Invented.", GOAL_15K))
        assertThat(question.startEpochDay).isEqualTo(MONDAY)
        assertThat(offered.ask.goal).isEqualTo(GOAL_15K)
        assertThat(offered.plan.outline).isEqualTo(OUTLINE_15)
        assertThat(ask().keepProgramme(offered.id)).isEqualTo(MONDAY)
    }

    /** Choice 15: while a plan runs the answer starts next Monday, so the goal's weeks count from there. */
    @Test
    fun `while a plan runs, Plan ahead's question starts next Monday`() = runTest {
        val id = programmes.add(offered())
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)

        ask().planAhead(PlanAheadAsk(GOAL_15K, 3))

        val question = trainer.asked.single().question as TrainerQuestion.Evaluate
        assertThat(question.startEpochDay).isEqualTo(MONDAY + 7)
        assertThat(question.ask.weeks).isEqualTo(14)
    }

    /** D121: from Friday, next week is filled in — kept at once as the plan's next version, its target as answered. */
    @Test
    fun `filling in next week keeps a new version with that week's sessions and its target`() = runTest {
        val id = keptOutlined(filled = 1)
        assertThat(ask().fillWeek()).isEqualTo(AskTheTrainer.Filled.NotOffered)
        assertThat(trainer.asked).isEmpty()

        day = MONDAY + 4
        trainer.fills += TrainerReply.Answered(FilledWeek(PlanWeek("Invented", listOf(WALK_30, WALK_20)), target(110, 30), "Invented reason."), "a-model")
        val filled = ask().fillWeek() as AskTheTrainer.Filled.Done

        assertThat((trainer.asked.single().question as TrainerQuestion.FillWeek).week).isEqualTo(1)
        assertThat(filled.why).isEqualTo("Invented reason.")
        val running = programmes.running()!!
        assertThat(running.id).isEqualTo(filled.programme.id)
        assertThat(running.replacesId).isEqualTo(id)
        assertThat(running.startEpochDay).isEqualTo(MONDAY)
        assertThat(running.plan.weeks[1].sessions).containsExactly(WALK_30, WALK_20).inOrder()
        assertThat(running.plan.weeks.drop(2).all { it.sessions.isEmpty() }).isTrue()
        assertThat(running.plan.outline!!.target(1)).isEqualTo(target(110, 30))
        assertThat(running.ask.goal).isEqualTo(GOAL_15K)
        assertThat(programmes.rows.value.first { it.id == id }.status).isEqualTo(ProgrammeStatus.ADJUSTED)
    }

    /** D121: once a week has begun with no sessions, it is planned. */
    @Test
    fun `an empty week that has begun is planned, any day`() = runTest {
        keptOutlined(filled = 0)
        trainer.fills += TrainerReply.Answered(FilledWeek(PlanWeek("Invented", listOf(WALK_30)), target(120, 40), ""), "a-model")

        assertThat(ask().fillWeek()).isInstanceOf(AskTheTrainer.Filled.Done::class.java)
        assertThat((trainer.asked.single().question as TrainerQuestion.FillWeek).week).isEqualTo(0)
        assertThat(programmes.running()!!.plan.weeks[0].sessions).containsExactly(WALK_30)
    }

    @Test
    fun `a failed fill stores nothing`() = runTest {
        val id = keptOutlined(filled = 0)
        trainer.fills += TrainerReply.Failed(EstimateResult.NoKey)

        assertThat(ask().fillWeek()).isEqualTo(AskTheTrainer.Filled.Failed(EstimateResult.NoKey))
        assertThat(programmes.running()!!.id).isEqualTo(id)
    }

    /** D121: a steady plan's last week plans the next four weeks' outline, kept at once. */
    @Test
    fun `a steady plan's next block adds four weeks and the block`() = runTest {
        val steady = offered().copy(
            ask = ProgrammeAsk(4, 3, goal = PlanGoal.Steady),
            plan = WeeksPlan("Invented", List(4) { PlanWeek("w", listOf(WALK_30)) }, "Invented reason.", STEADY_OUTLINE),
        )
        val id = programmes.add(steady)
        programmes.keep(id, MONDAY - 21, TEST_EPOCH_DAY - 21)
        val block = Block(Phase.STEADY, "Invented focus", List(4) { target(130, 40) })
        trainer.blocks += TrainerReply.Answered(BlockAnswer(block, "Invented reason."), "a-model")

        assertThat(ask().nextBlock()).isInstanceOf(AskTheTrainer.Filled.Done::class.java)

        assertThat(trainer.asked.single().question).isInstanceOf(TrainerQuestion.NextBlock::class.java)
        val running = programmes.running()!!
        assertThat(running.ask.weeks).isEqualTo(8)
        assertThat(running.plan.weeks).hasSize(8)
        assertThat(running.plan.weeks.drop(4).all { it.sessions.isEmpty() }).isTrue()
        assertThat(running.plan.outline!!.blocks).containsExactly(STEADY_OUTLINE.blocks.single(), block).inOrder()
        assertThat(running.startEpochDay).isEqualTo(MONDAY - 21)
        assertThat(ask().running()!!.weekIndex).isEqualTo(3)
    }

    @Test
    fun `the next block is not asked for before the last week, nor for a distance`() = runTest {
        keptOutlined(filled = 2)

        assertThat(ask().nextBlock()).isEqualTo(AskTheTrainer.Filled.NotOffered)
        assertThat(trainer.asked).isEmpty()
    }

    /** D121: Adjust rewrites the filled weeks and may change the later targets; the outline's blocks stay. */
    @Test
    fun `adjusting an outlined plan rewrites the two filled weeks and replaces the targets from this week`() = runTest {
        keptOutlined(filled = 2)
        trainer.adjustments += TrainerReply.Answered(
            WeeksPlan("Invented new", listOf(PlanWeek("a", listOf(WALK_20)), PlanWeek("b", listOf(WALK_20, WALK_30))), "Invented."), "a-model",
        )
        trainer.adjustTargets += List(15) { target(100, 30) }

        val version = (ask().adjust("Invented.") as AskTheTrainer.Adjusted.Offered).programme

        assertThat(version.plan.weeks).hasSize(15)
        assertThat(version.plan.weeks[0].sessions).containsExactly(WALK_30, WALK_20).inOrder()
        assertThat(version.plan.weeks[1].sessions).containsExactly(WALK_20, WALK_30).inOrder()
        assertThat(version.plan.weeks.drop(2).all { it.sessions.isEmpty() }).isTrue()
        assertThat(version.plan.outline!!.targets).containsExactlyElementsIn(List(15) { target(100, 30) })
        assertThat(version.plan.outline!!.blocks.map { it.phase }).isEqualTo(OUTLINE_15.blocks.map { it.phase })
        assertThat(version.ask.goal).isEqualTo(GOAL_15K)
    }

    /** D123: the plan shown carries its goal and its Month; a plan made before outlines carries neither. */
    @Test
    fun `the plan shown carries its goal and month, and an older plan neither`() = runTest {
        keptOutlined(filled = 2)

        val shown = ask().planShown()!!

        assertThat(shown.goal).isEqualTo(GOAL_15K)
        assertThat(shown.month!!.blocks.flatMap { it.weeks }.single { it.here }.number).isEqualTo(1)
        assertThat(shown.timeline.weeks[2].target).isEqualTo(OUTLINE_15.target(2))

        programmes.rows.value = emptyList()
        val legacy = programmes.add(offered())
        programmes.keep(legacy, MONDAY, TEST_EPOCH_DAY)
        assertThat(ask().planShown()!!.month).isNull()
        assertThat(ask().planShown()!!.goal).isNull()
    }

    /** The goal fixture's plan, [filled] weeks with two thirty-minute walks each. */
    private fun outlined(filled: Int) = WeeksPlan(
        "Invented", List(15) { if (it < filled) PlanWeek("w", listOf(WALK_30, WALK_30)) else PlanWeek("", emptyList()) }, "Invented reason.", OUTLINE_15,
    )

    /** The goal fixture's plan, kept from [MONDAY]; its id. */
    private suspend fun keptOutlined(filled: Int): Long {
        val id = programmes.add(offered().copy(ask = ProgrammeAsk(15, 3, goal = GOAL_15K), plan = outlined(filled)))
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
        return id
    }

    private fun ask() = AskTheTrainer(
        record, store, weights, FakeProfileRepository(aProfile()), trainer, aboutMe, programmes,
        Today { LocalDate.ofEpochDay(day) }, Now { NOW }, CurrentYear { TEST_YEAR }, food, weekNotes,
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
        /** D108: a new reply is a headline and a note; the four parts are empty. */
        val FEEDBACK = Feedback("Invented headline.", "", "", "", "", PlanFollowed.YES, note = "Invented note.")
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
