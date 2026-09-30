package com.metaself.app.data.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.TrainerPrompt
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.PlannedTick
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerPath
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
 * D106. For each path the workbench's request equals the one the real ask sends for the same inputs and
 * record — built by the same builders, so this cannot pass by coincidence — and a run writes nothing.
 * Today is [TEST_EPOCH_DAY]; every figure and word is invented.
 */
class TrainerWorkbenchTest {

    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val trainer = FakeTrainer()
    private val programmes = FakeProgrammeStore()
    private val sender = RecordingWorkbenchSender()
    private val weights = InMemoryWeightRepository(listOf(WeightReading(TEST_EPOCH_DAY - 10, 74.0)))
    private val aboutMe = InMemoryAboutMeStore("Invented note.")
    private val ask = AskTheTrainer(
        record, store, weights, FakeProfileRepository(aProfile()), trainer, aboutMe, programmes,
        Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { NOW }, CurrentYear { TEST_YEAR },
    )
    private val workbench = TrainerWorkbench(ask, store, record, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, sender)

    @BeforeEach
    fun setUp() {
        store.workouts.value = listOf(walk(id = 1, day = TEST_EPOCH_DAY))
        record.workouts.value = store.workouts.value
        record.days.value = listOf(HealthDay(TEST_EPOCH_DAY, steps = 8_000, distanceM = 4_000, activeKcal = 300))
    }

    @Test
    fun `feedback's request is the real one, from the stored review`() = runTest {
        val planId = storedReview()
        planRunning()

        val bench = workbench.request(TrainerWorkbench.Inputs.Feedback(1))!!
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        ask.save(1, Felt.HARD, "Invented words.", planId, withFeedback = true)

        assertThat(bench).isEqualTo(trainer.asked.single())
        assertThat(TrainerPrompt.userMessage(bench)).isEqualTo(TrainerPrompt.userMessage(trainer.asked.single()))
        assertThat((bench.question as TrainerQuestion.Review).planned).isEqualTo(PlannedTick(1, 2, WALK_30))
        // The asked session's own earlier feedback is excluded, as the real ask excludes it too (exceptWorkoutId).
        assertThat(bench.earlierFeedback).contains(OTHER_FEEDBACK)
        assertThat(bench.earlierFeedback).doesNotContain(OWN_FEEDBACK)
    }

    @Test
    fun `the plan form's request is the real one, with the next planned session`() = runTest {
        planRunning()

        val bench = workbench.request(TrainerWorkbench.Inputs.Plan(ANSWERS))
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        ask.suggest(ANSWERS)

        assertThat(bench).isEqualTo(trainer.asked.single())
    }

    @Test
    fun `the evaluation's request is the real one, with the start and the last evaluation`() = runTest {
        planRunning()
        val form = ProgrammeAsk(2, 2, "Invented words.")

        val bench = workbench.request(TrainerWorkbench.Inputs.Evaluate(form))
        trainer.evaluations += TrainerReply.Answered(EvaluationAndPlan(EVALUATION, WEEKS), "a-model")
        ask.evaluate(form)

        assertThat(bench).isEqualTo(trainer.asked.single())
    }

    @Test
    fun `the adjustment's request is the real one`() = runTest {
        planRunning()

        val bench = workbench.request(TrainerWorkbench.Inputs.Adjust(" Invented words. "))
        trainer.adjustments += TrainerReply.Answered(WEEKS, "a-model")
        ask.adjust(" Invented words. ")

        assertThat(bench).isEqualTo(trainer.asked.single())
    }

    @Test
    fun `with no plan running, adjusting sends nothing and says so`() = runTest {
        assertThat(workbench.send(TrainerWorkbench.Inputs.Adjust("Invented."), SYSTEM)).isEqualTo(TrainerWorkbench.Run.NotRunning)
        assertThat(sender.sent).isEmpty()
    }

    @Test
    fun `a session no longer in the record sends nothing`() = runTest {
        assertThat(workbench.send(TrainerWorkbench.Inputs.Feedback(99), SYSTEM)).isEqualTo(TrainerWorkbench.Run.SessionGone)
        assertThat(sender.sent).isEmpty()
    }

    /**
     * The real ask (AskTheTrainer) writes only to the trainer store and the programme store; both are
     * made to fail here, so a workbench write would fail the test.
     */
    @Test
    fun `a run on every path writes nothing, and sends the given instructions with the real request`() = runTest {
        storedReview()
        planRunning()
        val plans = store.plans.value
        val reviews = store.reviews.value
        val rows = programmes.rows.value
        val confirmations = programmes.confirmationRows.value
        store.failing = setOf("addPlan", "keep", "unkeep", "putReview")
        programmes.failing = true

        val inputs = listOf(
            TrainerWorkbench.Inputs.Feedback(1),
            TrainerWorkbench.Inputs.Plan(ANSWERS),
            TrainerWorkbench.Inputs.Evaluate(ProgrammeAsk(2, 2)),
            TrainerWorkbench.Inputs.Adjust("Invented words."),
        )
        inputs.forEach { assertThat(workbench.send(it, SYSTEM)).isInstanceOf(TrainerWorkbench.Run.Replied::class.java) }

        assertThat(store.plans.value).isEqualTo(plans)
        assertThat(store.reviews.value).isEqualTo(reviews)
        assertThat(programmes.rows.value).isEqualTo(rows)
        assertThat(programmes.confirmationRows.value).isEqualTo(confirmations)
        assertThat(sender.sent.map { it.first }).containsExactly(SYSTEM, SYSTEM, SYSTEM, SYSTEM)
        inputs.zip(sender.sent).forEach { (input, sent) -> assertThat(sent.second).isEqualTo(workbench.request(input)) }
    }

    @Test
    fun `the sessions offered are the visible, counted ones of the 42 days, newest first`() = runTest {
        record.workouts.value = listOf(
            walk(id = 1, day = TEST_EPOCH_DAY),
            walk(id = 2, day = TEST_EPOCH_DAY - 41),
            walk(id = 3, day = TEST_EPOCH_DAY - 42),
            walk(id = 4, day = TEST_EPOCH_DAY - 1).copy(hidden = true),
            walk(id = 5, day = TEST_EPOCH_DAY - 2).copy(counted = false),
        )

        assertThat(workbench.sessions().map { it.id }).containsExactly(1L, 2L).inOrder()
    }

    @Test
    fun `the app's instructions are the prompt's own`() {
        TrainerPath.entries.forEach { assertThat(workbench.appInstructions(it)).isEqualTo(TrainerPrompt.instructions(it)) }
    }

    @Test
    fun `whether a plan runs`() = runTest {
        assertThat(workbench.planRuns()).isFalse()

        planRunning()

        assertThat(workbench.planRuns()).isTrue()
    }

    /**
     * A stored review of session 1 naming a stored single-session plan, with its own earlier feedback
     * (which the real ask excludes when asking about session 1 again); a second session's review carries
     * a different feedback (which the real ask includes). Returns the plan's id.
     */
    private suspend fun storedReview(): Long {
        val planId = store.addPlan(TrainerPlan(0, NOW - HOUR, ANSWERS, PLAN, "a-model", kept = false))
        store.putReview(
            TrainerReview(
                workoutId = 1, planId = planId, felt = Felt.HARD, words = "Invented words.",
                feedback = OWN_FEEDBACK, feedbackAtMillis = NOW - HOUR,
            ),
        )
        store.workouts.value = store.workouts.value + walk(id = 2, day = TEST_EPOCH_DAY - 1)
        record.workouts.value = store.workouts.value
        store.putReview(
            TrainerReview(
                workoutId = 2, planId = null, felt = Felt.EASY, words = "Invented words too.",
                feedback = OTHER_FEEDBACK, feedbackAtMillis = NOW - 2 * HOUR,
            ),
        )
        return planId
    }

    /** A two-week plan, evaluated on a Sunday at noon and kept to run from this week's Monday. */
    private suspend fun planRunning() {
        val id = programmes.add(Programme(0, (MONDAY - 15) * DAY + 12 * HOUR, ProgrammeAsk(2, 2), EVALUATION, WEEKS, "a-model"))
        programmes.keep(id, MONDAY, TEST_EPOCH_DAY)
    }

    private fun walk(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 8 * HOUR, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = 4_000, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
        const val SYSTEM = "Invented instructions."
        val MONDAY = TEST_EPOCH_DAY - 3
        val ANSWERS = PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE, "Invented words.")
        val PLAN = SessionPlan(
            "Steady walk",
            listOf(PlanStep(0, 10, "Warm up", "easy pace"), PlanStep(10, 35, "Walk", "zone 2"), PlanStep(35, 45, "Cool down", "")),
            "Invented reason.",
        )
        val FEEDBACK = Feedback("Invented headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
        val OWN_FEEDBACK = Feedback("Invented own headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.YES)
        val OTHER_FEEDBACK = Feedback("Invented other headline.", "Invented.", "Invented.", "Invented.", "Invented.", PlanFollowed.PARTLY)
        val WALK_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Easy walk")
        val WEEKS = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK_30, WALK_30)) }, "Invented reason.")
        val EVALUATION = Evaluation("Invented headline.", "Invented.", "Invented.", "")
    }
}
