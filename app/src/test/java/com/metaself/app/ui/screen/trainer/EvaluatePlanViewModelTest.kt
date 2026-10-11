package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.DatedEvaluation
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.PlanStanding
import com.metaself.app.domain.trainer.PlanTile
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.FilledWeek
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.GoalSport
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.PlanOffer
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.STEADY_OUTLINE
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.TileState
import com.metaself.app.domain.trainer.TimelinePlace
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** D93, D94: the form, one ask, the answer, Keep and Ask again; and the running plan. Every answer is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class EvaluatePlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val programmes = FakeProgrammeStore()
    private val trainer = FakeTrainer()
    private val settings = FakeAiSettings()
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is asked until both rows are answered, and then once`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()

        viewModel.change(EvaluatePlanViewModel.Form())
        viewModel.ask()
        advanceUntilIdle()
        assertThat(trainer.asked).isEmpty()

        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2, words = "Invented words."))
        viewModel.ask()
        viewModel.ask()
        advanceUntilIdle()

        assertThat(trainer.asked).hasSize(1)
        assertThat(viewModel.state.value.shown!!.evaluation).isEqualTo(ANSWER.evaluation)
        assertThat(viewModel.state.value.startIfKept).isEqualTo(TEST_EPOCH_DAY - 3)
    }

    @Test
    fun `a failure says why and keeps the form`() = runTest {
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2))

        viewModel.ask()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(perWeek = 2))
    }

    @Test
    fun `keep makes it the running plan, and ask again returns to the form with the answers`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2))
        viewModel.ask()
        advanceUntilIdle()

        viewModel.keep()
        advanceUntilIdle()
        assertThat(viewModel.state.value.kept).isTrue()
        assertThat(programmes.running()!!.status).isEqualTo(ProgrammeStatus.RUNNING)

        viewModel.askAgain()
        advanceUntilIdle()
        assertThat(viewModel.state.value.shown).isNull()
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(perWeek = 2))
    }

    @Test
    fun `opened on the running plan, it shows the plan as its weeks with its chain's evaluation`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)

        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        val shown = viewModel.state.value.planShown!!
        assertThat(shown.running!!.programme.id).isEqualTo(id)
        assertThat(shown.timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(shown.evaluation!!.evaluation).isEqualTo(ANSWER.evaluation)
        assertThat(viewModel.state.value.loading).isFalse()
    }

    /** D114: Adjust the plan opens from here, so an adjustment kept there shows when the plan screen comes back. Invented. */
    @Test
    fun `back on the plan screen, the running plan is read again`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        val adjusted = programmes.add(Programme(0, 1, ProgrammeAsk(2, 2), null, ANSWER.plan, "a-model", replacesId = id))
        programmes.keepAdjusted(adjusted, id, TEST_EPOCH_DAY)
        viewModel.lookedAt()
        advanceUntilIdle()

        assertThat(viewModel.state.value.planShown!!.running!!.programme.id).isEqualTo(adjusted)
        assertThat(viewModel.state.value.planShown!!.evaluation!!.evaluation).isEqualTo(ANSWER.evaluation)
    }

    /** D117: Stop this plan returns here; the plan is drawn stopped, with no running card. */
    @Test
    fun `back from Stop this plan, the plan is shown stopped`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        programmes.stop(id, TEST_EPOCH_DAY)
        viewModel.lookedAt()
        advanceUntilIdle()

        assertThat(viewModel.state.value.planShown!!.timeline.standing).isEqualTo(PlanStanding.Stopped(TEST_EPOCH_DAY))
        assertThat(viewModel.state.value.planShown!!.running).isNull()
    }

    /** D118: asked on a Thursday, the answer would start this Monday — week 1 is this week, every session open. */
    @Test
    fun `an answer is drawn as its weeks before it is kept`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2))
        viewModel.ask()
        advanceUntilIdle()

        val offered = viewModel.state.value.offered!!
        assertThat(offered.timeline.standing).isEqualTo(PlanStanding.InWeek(1))
        assertThat(offered.timeline.weeks[0].place).isEqualTo(TimelinePlace.ThisWeek(listOf(PlanTile(WALK, TileState.OPEN), PlanTile(WALK, TileState.OPEN))))
        assertThat(offered.evaluation).isEqualTo(DatedEvaluation(ANSWER.evaluation, TEST_EPOCH_DAY))
        assertThat(offered.running).isNull()

        viewModel.askAgain()
        advanceUntilIdle()
        assertThat(viewModel.state.value.offered).isNull()
    }

    /** D124: the form opens on a steady rhythm and 3 sessions a week, ready; no number of weeks is asked. */
    @Test
    fun `the form opens ready to ask a steady rhythm, and asks with the goal`() = runTest {
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(perWeek = 3))
        assertThat(viewModel.state.value.canAsk).isTrue()

        viewModel.ask()
        advanceUntilIdle()

        val question = trainer.asked.single().question as TrainerQuestion.Evaluate
        assertThat(question.ask).isEqualTo(ProgrammeAsk(4, 3, goal = PlanGoal.Steady))
    }

    /** D119: a distance needs its km; its date moves a week at a time, 6 to 30 weeks ahead. */
    @Test
    fun `a distance goal is ready once its km reads, and its date stays 6 to 30 weeks ahead`() = runTest {
        trainer.evaluations += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        val distance = EvaluatePlanViewModel.Form(goal = EvaluatePlanViewModel.GoalChoice.DISTANCE, perWeek = 3, weeksAhead = 14)

        listOf("", "0", "abc", "1001", "12.34", "-3").forEach { km ->
            viewModel.change(distance.copy(km = km))
            advanceUntilIdle()
            assertThat(viewModel.state.value.canAsk).isFalse()
        }
        viewModel.change(distance.copy(km = "15"))
        advanceUntilIdle()
        assertThat(viewModel.state.value.canAsk).isTrue()
        assertThat(distance.copy(km = "21.1").ask(TEST_EPOCH_DAY)!!.goal).isEqualTo(PlanGoal.Distance(GoalSport.RUN, 21.1, GOAL_15K.byEpochDay))
        assertThat(distance.copy(weeksAhead = 6).earlier().weeksAhead).isEqualTo(6)
        assertThat(distance.copy(weeksAhead = 30).later().weeksAhead).isEqualTo(30)
        assertThat(distance.later().weeksAhead).isEqualTo(15)

        viewModel.ask()
        advanceUntilIdle()

        val question = trainer.asked.single().question as TrainerQuestion.Evaluate
        assertThat(question.ask).isEqualTo(ProgrammeAsk(15, 3, goal = GOAL_15K))
    }

    /** D121: the plan page offers to fill in a week; the answer is kept and the page read again, its sentence said. */
    @Test
    fun `the plan page fills in an empty week, says the trainer's sentence, and shows the week`() = runTest {
        val plan = WeeksPlan("Invented plan", List(4) { PlanWeek("", emptyList()) }, "Invented reason.", STEADY_OUTLINE)
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(4, 3, goal = PlanGoal.Steady), ANSWER.evaluation, plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        trainer.fills += TrainerReply.Failed(EstimateResult.NoKey)
        trainer.fills += TrainerReply.Answered(FilledWeek(PlanWeek("Invented", listOf(WALK)), STEADY_OUTLINE.target(0)!!, "Invented sentence."), "a-model")
        val viewModel = watched(ON_PLAN)
        advanceUntilIdle()
        assertThat(viewModel.state.value.offer).isEqualTo(PlanOffer.PlanThisWeek(0))

        viewModel.fill()
        advanceUntilIdle()
        assertThat(viewModel.state.value.fillFailure).isEqualTo(EstimateResult.NoKey)

        viewModel.fill()
        viewModel.fill()
        advanceUntilIdle()

        assertThat(trainer.asked).hasSize(2)
        assertThat(viewModel.state.value.fillFailure).isNull()
        assertThat(viewModel.state.value.filledLine).isEqualTo("Filled in. Invented sentence.")
        assertThat(viewModel.state.value.offer).isNull()
        assertThat((viewModel.state.value.planShown!!.timeline.weeks[0].place as TimelinePlace.ThisWeek).tiles).hasSize(1)
    }

    /** A second tap on Keep before the first has written is ignored: one write, and no refusal said. */
    @Test
    fun `a double tap on keep writes once and says no refusal`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2))
        viewModel.ask()
        advanceUntilIdle()

        viewModel.keep()
        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.kept).isTrue()
        assertThat(viewModel.state.value.refused).isNull()
        assertThat(problems.recorded).isEmpty()
        assertThat(programmes.running()!!.id).isEqualTo(viewModel.state.value.shown!!.id)
    }

    @Test
    fun `a failed keep is said`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2))
        viewModel.ask()
        advanceUntilIdle()
        programmes.failing = true

        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.kept).isFalse()
    }

    /** D123: the plan page opens on Week; the switch changes the view. */
    @Test
    fun `the plan page opens on Week, and the switch changes the view`() = runTest {
        val viewModel = watched(ON_PLAN)
        advanceUntilIdle()
        assertThat(viewModel.state.value.view).isEqualTo(EvaluatePlanViewModel.PlanView.WEEK)

        viewModel.choose(EvaluatePlanViewModel.PlanView.SESSION)
        advanceUntilIdle()

        assertThat(viewModel.state.value.view).isEqualTo(EvaluatePlanViewModel.PlanView.SESSION)
    }

    /** D122: with no plan running, the kept single session is the plan page's Next session. */
    @Test
    fun `with no plan, the plan page reads the next session`() = runTest {
        val kept = store.addPlan(TrainerScreens.storedPlan(TrainerScreens.NOW - TrainerScreens.HOUR))
        store.keep(kept)

        val viewModel = watched(ON_PLAN)
        advanceUntilIdle()

        assertThat(viewModel.state.value.planShown).isNull()
        assertThat(viewModel.state.value.nextSession!!.id).isEqualTo(kept)
        assertThat(viewModel.state.value.sessions.next!!.id).isEqualTo(kept)
    }

    /** D122, D123: the Session view's cards carry the steps kept for their places. */
    @Test
    fun `the sessions carry the steps kept for them`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        val steps = store.addPlan(TrainerScreens.storedPlan(TrainerScreens.NOW - TrainerScreens.HOUR).copy(slot = SessionSlot(id, 0, 1)))
        store.keep(steps)

        val viewModel = watched(ON_PLAN)
        advanceUntilIdle()

        val sessions = viewModel.state.value.sessions
        assertThat(sessions.thisWeek.map { it.steps?.id }).containsExactly(null, steps).inOrder()
        assertThat(sessions.nextWeek).hasSize(2)
        assertThat(sessions.next).isNull()
    }

    /** D124: while a plan runs, Keep asks first; the answer is drawn from, and starts, next Monday. */
    @Test
    fun `while a plan runs, keeping asks first and the new plan starts next Monday`() = runTest {
        val old = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(old, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.replacingFrom).isEqualTo(TEST_EPOCH_DAY + 4)
        viewModel.change(EvaluatePlanViewModel.Form(perWeek = 2))
        viewModel.ask()
        advanceUntilIdle()
        assertThat(viewModel.state.value.startIfKept).isEqualTo(TEST_EPOCH_DAY + 4)

        viewModel.keep()
        advanceUntilIdle()
        assertThat(viewModel.state.value.confirming).isTrue()
        assertThat(programmes.running()!!.id).isEqualTo(old)
        viewModel.cancelKeep()
        advanceUntilIdle()
        assertThat(viewModel.state.value.confirming).isFalse()

        viewModel.keep()
        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.kept).isTrue()
        assertThat(viewModel.state.value.confirming).isFalse()
        assertThat(programmes.running()!!.let { it.id to it.startEpochDay }).isEqualTo(viewModel.state.value.shown!!.id to TEST_EPOCH_DAY + 4)
    }

    private fun TestScope.watched(saved: SavedStateHandle = SavedStateHandle()): EvaluatePlanViewModel {
        val make = {
            EvaluatePlanViewModel(saved, TrainerScreens.ask(record, store, trainer, programmes), settings, TrainerScreens.today, problems, outliving)
        }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[EvaluatePlanViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        val ON_PLAN: SavedStateHandle get() = SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING))
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val ANSWER = EvaluationAndPlan(
            Evaluation("Invented headline.", "Invented.", "Invented.", ""),
            WeeksPlan("Invented plan", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented reason."),
        )
    }
}
