package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.TrainerStore
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.Trainer
import com.metaself.app.domain.trainer.TrainerRequest
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    /**
     * What stands in for the app's own scope: on the test's dispatcher, so the test runs it, and apart
     * from the view model's, so clearing the view model leaves it running. Not `backgroundScope`, whose
     * work the test's `advanceUntilIdle` does not wait for.
     */
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
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
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

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

    /** A failed call's line gives way once anything is changed, so it never claims words that are not saved. */
    @Test
    fun `changing anything after a failed feedback call clears the failure`() = runTest {
        trainer.feedback += TrainerReply.Failed(EstimateResult.Unreachable())
        trainer.feedback += TrainerReply.Failed(EstimateResult.Unreachable())
        keep(storedPlan(createdAt = session.startedAtMillis - 2 * HOUR))
        val viewModel = opened()
        viewModel.words("Invented words.")

        viewModel.saveAndAsk()
        advanceUntilIdle()
        viewModel.words("Invented words, changed.")
        advanceUntilIdle()
        assertThat(viewModel.state.value.failure).isNull()
        assertThat(viewModel.state.value.saved).isFalse()

        viewModel.saveAndAsk()
        advanceUntilIdle()
        viewModel.feel(Felt.EASY)
        advanceUntilIdle()
        assertThat(viewModel.state.value.failure).isNull()

        viewModel.saveAndAsk()
        advanceUntilIdle()
        viewModel.notThisPlan()
        advanceUntilIdle()
        assertThat(viewModel.state.value.failure).isNull()
    }

    /** While a save is under way the form is fixed: what it saves is what is on screen. */
    @Test
    fun `nothing can be changed while a save is under way`() = runTest {
        keep(storedPlan(createdAt = session.startedAtMillis - 2 * HOUR))
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        val gate = CompletableDeferred<Unit>()
        val viewModel = opened(gated(gate))
        viewModel.feel(Felt.RIGHT)
        viewModel.words("Invented words.")

        viewModel.saveAndAsk()
        advanceUntilIdle()
        viewModel.words("Typed while saving.")
        viewModel.feel(Felt.HARD)
        viewModel.notThisPlan()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.working).isTrue()
        assertThat(state.words).isEqualTo("Invented words.")
        assertThat(state.felt).isEqualTo(Felt.RIGHT)
        assertThat(state.plan).isNotNull()
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `only a save that asks the trainer says it is asking`() = runTest {
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        val gate = CompletableDeferred<Unit>()
        val slowStore = object : TrainerStore by store {
            override suspend fun putReview(review: TrainerReview): Long {
                gate.await()
                return store.putReview(review)
            }
        }
        val viewModel = opened(store = slowStore)
        viewModel.words("Invented words.")

        viewModel.justSave()
        advanceUntilIdle()
        assertThat(viewModel.state.value.working).isTrue()
        assertThat(viewModel.state.value.askingTrainer).isFalse()
        gate.complete(Unit)
        advanceUntilIdle()

        val second = CompletableDeferred<Unit>()
        val asking = opened(gated(second))
        asking.words("Invented words.")
        asking.saveAndAsk()
        advanceUntilIdle()
        assertThat(asking.state.value.askingTrainer).isTrue()
        second.complete(Unit)
        advanceUntilIdle()
        assertThat(asking.state.value.askingTrainer).isFalse()
    }

    /** A paid request is not thrown away by leaving: its feedback is stored all the same. */
    @Test
    fun `leaving while the trainer is asked still stores the feedback`() = runTest {
        trainer.feedback += TrainerReply.Answered(FEEDBACK, "a-model")
        val gate = CompletableDeferred<Unit>()
        val entry = ViewModelStore()
        val viewModel = opened(gated(gate), entry = entry)
        viewModel.words("Invented words.")

        viewModel.saveAndAsk()
        advanceUntilIdle()
        entry.clear()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(store.reviewOf(1)!!.feedback!!.headline).isEqualTo(FEEDBACK.headline)
    }

    /**
     * Feedback asked for on an earlier visit can land after that screen was left. Reopened, the review
     * screen follows the store, so the feedback shows when it lands, and nothing is asked twice.
     */
    @Test
    fun `feedback stored after the screen opened shows, and is not asked for again`() = runTest {
        store.putReview(TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = "Invented words."))
        val viewModel = opened()
        assertThat(viewModel.state.value.feedback).isNull()

        store.putReview(store.reviewOf(1)!!.copy(feedback = FEEDBACK, feedbackAtMillis = TrainerScreens.NOW, model = "a-model"))
        advanceUntilIdle()

        assertThat(viewModel.state.value.feedback).isEqualTo(FEEDBACK)
        viewModel.saveAndAsk()
        advanceUntilIdle()
        assertThat(trainer.asked).isEmpty()
    }

    /** [trainer], waiting for [gate] before it gives feedback. */
    private fun gated(gate: CompletableDeferred<Unit>): Trainer = object : Trainer by trainer {
        override suspend fun feedback(request: TrainerRequest): TrainerReply<Feedback> {
            gate.await()
            return trainer.feedback(request)
        }
    }

    private suspend fun keep(plan: TrainerPlan) {
        store.keep(store.addPlan(plan))
    }

    /** Opened on workout 1, its state collected as a screen would, and its load finished. */
    private fun TestScope.opened(
        trainer: Trainer = this@ReviewSessionViewModelTest.trainer,
        store: TrainerStore = this@ReviewSessionViewModelTest.store,
        entry: ViewModelStore = ViewModelStore(),
    ): ReviewSessionViewModel {
        val make = {
            ReviewSessionViewModel(
                SavedStateHandle(mapOf(ReviewSessionViewModel.WORKOUT_ID to 1L)),
                TrainerScreens.ask(record, store, trainer), store, FakeAiSettings(),
                TrainerScreens.today, problems, outliving,
            )
        }
        // Held in a store, as the nav host's entry holds it, so clearing the store is leaving the screen.
        val viewModel = ViewModelProvider(entry, TrainerScreens.factory(make))[ReviewSessionViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }
}
