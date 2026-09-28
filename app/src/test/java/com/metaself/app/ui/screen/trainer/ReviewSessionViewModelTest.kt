package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.TrainerPlan
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.screen.trainer.TrainerScreens.DAY
import com.metaself.app.ui.screen.trainer.TrainerScreens.FEEDBACK
import com.metaself.app.ui.screen.trainer.TrainerScreens.HOUR
import com.metaself.app.ui.screen.trainer.TrainerScreens.storedPlan
import com.metaself.app.ui.screen.trainer.TrainerScreens.walk
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

/**
 * D87 for one session: its figures, the matched plan, how it felt and the words, then feedback. The
 * session is a walk at 07:00 on TEST_EPOCH_DAY; every word and answer is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewSessionViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val trainer = FakeTrainer()
    private val problems = RecordingProblemLog()
    private val session = walk(1)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store.workouts.value = listOf(session)
        record.workouts.value = listOf(session)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening loads the session and the plan kept two hours before it`() = runTest {
        keep(storedPlan(createdAt = session.startedAtMillis - 2 * HOUR))

        val state = opened().state.value

        assertThat(state.workout).isEqualTo(session)
        assertThat(state.plan!!.plan.title).isEqualTo("Steady walk")
        assertThat(state.today).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `a plan kept eight days before is not matched`() = runTest {
        keep(storedPlan(createdAt = session.startedAtMillis - 8 * DAY))

        assertThat(opened().state.value.plan).isNull()
    }

    @Test
    fun `an existing review opens with its felt, words and own plan, kept or not`() = runTest {
        val planId = store.addPlan(storedPlan(createdAt = session.startedAtMillis - 2 * HOUR, kept = false))
        store.putReview(TrainerReview(workoutId = 1, planId = planId, felt = Felt.HARD, words = "Invented words."))

        val state = opened().state.value

        assertThat(state.felt).isEqualTo(Felt.HARD)
        assertThat(state.words).isEqualTo("Invented words.")
        assertThat(state.plan!!.id).isEqualTo(planId)
        assertThat(state.feedback).isNull()
    }

    @Test
    fun `an existing review with feedback opens on the feedback`() = runTest {
        store.putReview(TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = null, feedback = FEEDBACK))

        assertThat(opened().state.value.feedback).isEqualTo(FEEDBACK)
    }

    @Test
    fun `not this plan saves with no plan`() = runTest {
        keep(storedPlan(createdAt = session.startedAtMillis - 2 * HOUR))
        val viewModel = opened()

        viewModel.notThisPlan()
        viewModel.feel(Felt.EASY)
        viewModel.justSave()
        advanceUntilIdle()

        assertThat(viewModel.state.value.plan).isNull()
        assertThat(store.reviewOf(1)!!.planId).isNull()
    }

    @Test
    fun `saving needs a felt effort or words`() = runTest {
        val viewModel = opened()

        assertThat(viewModel.state.value.canSave).isFalse()
        viewModel.words("   ")
        advanceUntilIdle()
        assertThat(viewModel.state.value.canSave).isFalse()
        viewModel.words("Invented.")
        advanceUntilIdle()
        assertThat(viewModel.state.value.canSave).isTrue()
        viewModel.words("")
        viewModel.feel(Felt.RIGHT)
        advanceUntilIdle()
        assertThat(viewModel.state.value.canSave).isTrue()
    }

    @Test
    fun `just save stores and asks nothing`() = runTest {
        val viewModel = opened()
        viewModel.feel(Felt.RIGHT)
        viewModel.words("Invented words.")

        viewModel.justSave()
        advanceUntilIdle()

        assertThat(viewModel.state.value.saved).isTrue()
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented words.")
        assertThat(trainer.asked).isEmpty()
    }

    @Test
    fun `save and ask shows the feedback and the plan is no longer kept`() = runTest {
        keep(storedPlan(createdAt = session.startedAtMillis - 2 * HOUR))
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        val viewModel = opened()
        viewModel.feel(Felt.HARD)

        viewModel.saveAndAsk()
        advanceUntilIdle()

        assertThat(viewModel.state.value.feedback).isEqualTo(FEEDBACK)
        assertThat(viewModel.state.value.working).isFalse()
        assertThat(store.keptPlan()).isNull()
    }

    /** D87: a failed call keeps the words, on screen and in the store. */
    @Test
    fun `a failed feedback call keeps the felt effort and the words`() = runTest {
        trainer.feedback += TrainerReply.Failed(EstimateResult.Unreachable())
        val viewModel = opened()
        viewModel.feel(Felt.RIGHT)
        viewModel.words("Invented words.")

        viewModel.saveAndAsk()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.failure).isEqualTo(EstimateResult.Unreachable())
        assertThat(state.felt).isEqualTo(Felt.RIGHT)
        assertThat(state.words).isEqualTo("Invented words.")
        assertThat(state.feedback).isNull()
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented words.")
    }

    @Test
    fun `a missing session says it is gone`() = runTest {
        store.workouts.value = emptyList()

        assertThat(opened().state.value.gone).isTrue()
    }

    /** The session left the record while the form was open: the words are kept anyway, and it says so. */
    @Test
    fun `a session gone while writing keeps the words and says so`() = runTest {
        val viewModel = opened()
        viewModel.words("Invented words.")
        store.workouts.value = emptyList()

        viewModel.saveAndAsk()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.gone).isTrue()
        assertThat(state.saved).isTrue()
        assertThat(state.working).isFalse()
        assertThat(trainer.asked).isEmpty()
        assertThat(store.reviewOf(1)!!.words).isEqualTo("Invented words.")
    }

    @Test
    fun `a write that fails keeps the words on screen and says it may be partial`() = runTest {
        val viewModel = opened()
        viewModel.words("Invented words.")
        store.failing = setOf("putReview")

        viewModel.justSave()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.refused).isEqualTo(ActionRefused.MAYBE_PARTIAL)
        assertThat(state.words).isEqualTo("Invented words.")
        assertThat(state.working).isFalse()
        assertThat(state.saved).isFalse()
        assertThat(problems.recorded.map { it.kind }).containsExactly("refused")
    }

    private suspend fun keep(plan: TrainerPlan) {
        store.keep(store.addPlan(plan))
    }

    /** Opened on workout 1, its state collected as a screen would, and its load finished. */
    private fun TestScope.opened(): ReviewSessionViewModel {
        val viewModel = ReviewSessionViewModel(
            SavedStateHandle(mapOf(ReviewSessionViewModel.WORKOUT_ID to 1L)),
            TrainerScreens.ask(record, store, trainer), store, FakeAiSettings(), TrainerScreens.today, problems,
        )
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }
}
