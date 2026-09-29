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
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
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

        viewModel.change(EvaluatePlanViewModel.Form(weeks = 2))
        viewModel.ask()
        advanceUntilIdle()
        assertThat(trainer.asked).isEmpty()

        viewModel.change(EvaluatePlanViewModel.Form(weeks = 2, perWeek = 2, words = "Invented words."))
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
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))

        viewModel.ask()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(2, 2))
    }

    @Test
    fun `keep makes it the running plan, and ask again returns to the form with the answers`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))
        viewModel.ask()
        advanceUntilIdle()

        viewModel.keep()
        advanceUntilIdle()
        assertThat(viewModel.state.value.kept).isTrue()
        assertThat(programmes.running()!!.status).isEqualTo(ProgrammeStatus.RUNNING)

        viewModel.askAgain()
        advanceUntilIdle()
        assertThat(viewModel.state.value.shown).isNull()
        assertThat(viewModel.state.value.form).isEqualTo(EvaluatePlanViewModel.Form(2, 2))
    }

    @Test
    fun `opened on the running plan, it shows the plan with its chain's evaluation`() = runTest {
        val id = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), ANSWER.evaluation, ANSWER.plan, "a-model"))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)

        val viewModel = watched(SavedStateHandle(mapOf(EvaluatePlanViewModel.SHOW to EvaluatePlanViewModel.RUNNING)))
        advanceUntilIdle()

        assertThat(viewModel.state.value.running!!.programme.id).isEqualTo(id)
        assertThat(viewModel.state.value.evaluation).isEqualTo(ANSWER.evaluation)
        assertThat(viewModel.state.value.loading).isFalse()
    }

    @Test
    fun `a failed keep is said`() = runTest {
        trainer.evaluations += TrainerReply.Answered(ANSWER, "a-model")
        val viewModel = watched()
        viewModel.change(EvaluatePlanViewModel.Form(2, 2))
        viewModel.ask()
        advanceUntilIdle()
        programmes.failing = true

        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.kept).isFalse()
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
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val ANSWER = EvaluationAndPlan(
            Evaluation("Invented headline.", "Invented.", "Invented.", ""),
            WeeksPlan("Invented plan", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented reason."),
        )
    }
}
