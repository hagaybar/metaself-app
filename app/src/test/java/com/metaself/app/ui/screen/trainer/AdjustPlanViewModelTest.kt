package com.metaself.app.ui.screen.trainer

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
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.ProgrammeStatus
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * D97: the running plan's page — one ask, the new version, Keep this version or Keep the old one; Stop,
 * asked first. The plan runs from Monday 31 August 2026 (TEST_EPOCH_DAY - 3); every answer is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustPlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val programmes = FakeProgrammeStore()
    private val trainer = FakeTrainer()
    private val settings = FakeAiSettings()
    private val problems = RecordingProblemLog()
    private var runningId = 0L

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking {
            runningId = programmes.add(Programme(0, 0, ProgrammeAsk(2, 2), null, PLAN, "a-model"))
            programmes.keep(runningId, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        }
    }

    @Test
    fun `it opens on the running plan, and adjusting shows the new version without keeping it`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.running!!.programme.id).isEqualTo(runningId)

        viewModel.words("Invented words.")
        viewModel.adjust()
        viewModel.adjust()
        advanceUntilIdle()

        assertThat(trainer.asked).hasSize(1)
        assertThat(viewModel.state.value.shown!!.replacesId).isEqualTo(runningId)
        assertThat(programmes.running()!!.id).isEqualTo(runningId)
    }

    @Test
    fun `keep this version runs it and closes`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.adjust()
        advanceUntilIdle()

        viewModel.keepNew()
        advanceUntilIdle()

        assertThat(programmes.running()!!.id).isEqualTo(viewModel.state.value.shown!!.id)
        assertThat(viewModel.state.value.finished).isTrue()
    }

    /** Design question 6: the new version stays offered; the old one runs on. */
    @Test
    fun `keep the old one closes and changes nothing`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.adjust()
        advanceUntilIdle()
        val offered = viewModel.state.value.shown!!.id

        viewModel.keepOld()
        advanceUntilIdle()

        assertThat(viewModel.state.value.finished).isTrue()
        assertThat(programmes.running()!!.id).isEqualTo(runningId)
        assertThat(programmes.all().single { it.id == offered }.status).isEqualTo(ProgrammeStatus.OFFERED)
    }

    @Test
    fun `stop asks first, then ends the plan today and closes`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.askStop()
        advanceUntilIdle()
        assertThat(viewModel.state.value.confirmStop).isTrue()
        viewModel.cancelStop()
        advanceUntilIdle()
        assertThat(viewModel.state.value.confirmStop).isFalse()
        assertThat(programmes.running()).isNotNull()

        viewModel.askStop()
        viewModel.confirmStop()
        advanceUntilIdle()

        assertThat(programmes.running()).isNull()
        assertThat(viewModel.state.value.finished).isTrue()
    }

    /** A second tap on Keep this version before the first has written is ignored: one write, no refusal. */
    @Test
    fun `a double tap on keep this version writes once and says no refusal`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.adjust()
        advanceUntilIdle()

        viewModel.keepNew()
        viewModel.keepNew()
        advanceUntilIdle()

        assertThat(viewModel.state.value.finished).isTrue()
        assertThat(viewModel.state.value.refused).isNull()
        assertThat(problems.recorded).isEmpty()
        assertThat(programmes.running()!!.id).isEqualTo(viewModel.state.value.shown!!.id)
    }

    @Test
    fun `a double tap on stop it stops once and says no refusal`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.askStop()
        viewModel.confirmStop()
        viewModel.confirmStop()
        advanceUntilIdle()

        assertThat(programmes.running()).isNull()
        assertThat(viewModel.state.value.finished).isTrue()
        assertThat(viewModel.state.value.refused).isNull()
        assertThat(problems.recorded).isEmpty()
    }

    /** While an adjustment is being asked for, Stop is not taken: the answer would adjust a stopped plan. */
    @Test
    fun `stop is ignored while an adjustment is asked for`() = runTest {
        trainer.adjustments += TrainerReply.Answered(REST, "a-model")
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.adjust()
        viewModel.askStop()
        viewModel.confirmStop()
        advanceUntilIdle()

        assertThat(viewModel.state.value.confirmStop).isFalse()
        assertThat(programmes.running()!!.id).isEqualTo(runningId)
        assertThat(viewModel.state.value.shown).isNotNull()
    }

    @Test
    fun `a failed adjustment says why and keeps the words`() = runTest {
        trainer.adjustments += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.words("Invented words.")

        viewModel.adjust()
        advanceUntilIdle()

        assertThat(viewModel.state.value.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(viewModel.state.value.words).isEqualTo("Invented words.")
        assertThat(viewModel.state.value.shown).isNull()
    }

    @AfterEach
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.watched(): AdjustPlanViewModel {
        val make = {
            AdjustPlanViewModel(TrainerScreens.ask(record, store, trainer, programmes), settings, TrainerScreens.today, problems, outliving)
        }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[AdjustPlanViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        val WALK = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        val PLAN = WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(WALK, WALK)) }, "Invented.")
        val REST = WeeksPlan("Invented new", listOf(PlanWeek("now", listOf(WALK)), PlanWeek("next", listOf(WALK))), "Invented.")
    }
}
