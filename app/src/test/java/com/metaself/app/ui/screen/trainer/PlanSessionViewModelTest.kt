package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.Wish
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.screen.trainer.TrainerScreens.ANSWERS
import com.metaself.app.ui.screen.trainer.TrainerScreens.HOUR
import com.metaself.app.ui.screen.trainer.TrainerScreens.NOW
import com.metaself.app.ui.screen.trainer.TrainerScreens.PLAN
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** D86: the form, one ask, the suggestion, Keep and Ask again. Every answer and word is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val trainer = FakeTrainer()
    private val settings = FakeAiSettings()
    private val problems = RecordingProblemLog()

    private val filled = PlanSessionViewModel.Form(ANSWERS.activity, ANSWERS.time, ANSWERS.feeling, ANSWERS.wish, ANSWERS.words)

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `ask is not possible until all four rows are answered`() = runTest {
        val viewModel = watched()

        assertThat(viewModel.state.value.canAsk).isFalse()
        viewModel.change(PlanSessionViewModel.Form(PlanActivity.RUN, TimeAvailable.MIN_20, Feeling.FRESH, wish = null))
        viewModel.ask()
        advanceUntilIdle()

        assertThat(viewModel.state.value.canAsk).isFalse()
        assertThat(trainer.asked).isEmpty()
        viewModel.change(PlanSessionViewModel.Form(PlanActivity.RUN, TimeAvailable.MIN_20, Feeling.FRESH, Wish.EASY))
        advanceUntilIdle()
        assertThat(viewModel.state.value.canAsk).isTrue()
    }

    @Test
    fun `asking shows the plan, with one request only`() = runTest {
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        val gate = CompletableDeferred<Unit>()
        val slow = object : Trainer by trainer {
            override suspend fun suggest(request: TrainerRequest): TrainerReply<SessionPlan> {
                gate.await()
                return trainer.suggest(request)
            }
        }
        val viewModel = watched(trainer = slow)
        viewModel.change(filled)

        viewModel.ask()
        viewModel.ask()
        advanceUntilIdle()
        assertThat(viewModel.state.value.asking).isTrue()
        assertThat(viewModel.state.value.canAsk).isFalse()
        gate.complete(Unit)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.asking).isFalse()
        assertThat(state.shown!!.plan).isEqualTo(PLAN)
        assertThat(state.kept).isFalse()
        assertThat(trainer.asked).hasSize(1)
    }

    @Test
    fun `a failed ask keeps the answers and the words and says why`() = runTest {
        trainer.plans += TrainerReply.Failed(EstimateResult.NoKey)
        val viewModel = watched()
        viewModel.change(filled)

        viewModel.ask()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.form).isEqualTo(filled)
        assertThat(state.failure).isEqualTo(EstimateResult.NoKey)
        assertThat(state.shown).isNull()
        assertThat(state.asking).isFalse()
    }

    @Test
    fun `keep keeps the shown plan, and ask again returns to the form with the answers`() = runTest {
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        val viewModel = watched()
        viewModel.change(filled)
        viewModel.ask()
        advanceUntilIdle()

        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.kept).isTrue()
        assertThat(store.keptPlan()!!.id).isEqualTo(viewModel.state.value.shown!!.id)

        viewModel.askAgain()
        advanceUntilIdle()

        assertThat(viewModel.state.value.shown).isNull()
        assertThat(viewModel.state.value.kept).isFalse()
        assertThat(viewModel.state.value.form).isEqualTo(filled)
    }

    /** Design question 19: opened from the kept-plan card, the plan is shown as kept, the form empty. */
    @Test
    fun `opened on the kept plan it shows it as kept`() = runTest {
        val id = store.addPlan(TrainerScreens.storedPlan(createdAt = NOW - HOUR))
        store.keep(id)

        val viewModel = watched(SavedStateHandle(mapOf(PlanSessionViewModel.SHOW to PlanSessionViewModel.KEPT)))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.shown!!.id).isEqualTo(id)
        assertThat(state.kept).isTrue()
        assertThat(state.form).isEqualTo(PlanSessionViewModel.Form())
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `a keep that fails says nothing changed and is logged`() = runTest {
        trainer.plans += TrainerReply.Answered(PLAN, "a-model")
        val viewModel = watched()
        viewModel.change(filled)
        viewModel.ask()
        advanceUntilIdle()
        store.failing = setOf("keep")

        viewModel.keep()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.kept).isFalse()
        assertThat(problems.recorded.map { it.kind }).containsExactly("refused")
    }

    @Test
    fun `the ceiling is the settings' daily ceiling`() = runTest {
        settings.current.value = AiSettings(dailyCeiling = 20)

        val viewModel = watched()
        advanceUntilIdle()

        assertThat(viewModel.state.value.ceiling).isEqualTo(20)
    }

    /** A view model whose state is collected for the length of the test, as a screen would. */
    private fun TestScope.watched(saved: SavedStateHandle = SavedStateHandle(), trainer: Trainer = this@PlanSessionViewModelTest.trainer): PlanSessionViewModel {
        val viewModel = PlanSessionViewModel(saved, TrainerScreens.ask(record, store, trainer), store, settings, problems)
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

}
