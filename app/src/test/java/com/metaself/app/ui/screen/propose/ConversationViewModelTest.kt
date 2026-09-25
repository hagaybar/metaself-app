package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.FoodRepository
import com.metaself.app.domain.ai.Asked
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealConversationAsker
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.Question
import com.metaself.app.domain.ai.StepResult
import com.metaself.app.domain.ai.aProposal
import com.metaself.app.domain.food.Food
import com.metaself.app.ui.ActionRefused
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** A conversation through the view model (D58 §2, §7, §9, §12). Every meal and answer is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private val q1 = Question("How big was the container?", listOf("Small box", "Standard box", "Not sure"))
    private val q2 = Question("What was the sauce like?", listOf("Thin", "Creamy", "Not sure"))
    private val q3 = Question("How much cheese?", listOf("A sprinkle", "A layer", "Not sure"))

    @Test
    fun `questions are offered, and OK shows the first with nothing sent`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, planned = 3))
        val viewModel = viewModel(asker)

        viewModel.describe(MEAL)
        advanceUntilIdle()
        val offer = viewModel.state.value as ProposalUiState.Offer
        assertThat(offer.chat.cap).isEqualTo(3)

        viewModel.acceptQuestions()

        assertThat((viewModel.state.value as ProposalUiState.Asking).chat.shown).isEqualTo(q1)
        assertThat(asker.nextCalls).isEmpty()
    }

    @Test
    fun `a meal that needs no question opens the rows at once`() = runTest {
        val viewModel = viewModel(ScriptedAsker(opening = StepResult.Estimate(EstimateResult.Proposed(aProposal()))))

        viewModel.describe("an apple")
        advanceUntilIdle()

        assertThat((viewModel.state.value as ProposalUiState.Proposed).afterConversation).isNull()
    }

    @Test
    fun `each answer asks for the next question with every answer so far`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), steps = mutableListOf(StepResult.Ask(q2, 3)))
        val viewModel = asking(asker)

        viewModel.answer("Standard box")
        advanceUntilIdle()

        assertThat(asker.nextCalls.single()).containsExactly(Asked(q1.text, "Standard box"))
        val asking = viewModel.state.value as ProposalUiState.Asking
        assertThat(asking.chat.shown).isEqualTo(q2)
    }

    @Test
    fun `the last offered answer goes to the final analysis, and the result keeps the answers`() = runTest {
        val asker = ScriptedAsker(
            opening = StepResult.Ask(q1, 2),
            steps = mutableListOf(StepResult.Ask(q2, 2)),
        )
        val viewModel = asking(asker)

        viewModel.answer("Standard box")
        advanceUntilIdle()
        viewModel.answer("Creamy")
        advanceUntilIdle()

        val call = asker.finishCalls.single()
        assertThat(call.asked.map { it.answer }).containsExactly("Standard box", "Creamy").inOrder()
        assertThat(call.deep).isTrue()
        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.afterConversation).isEqualTo(call.asked)
    }

    @Test
    fun `that's enough goes to the final analysis with what was said`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3))
        val viewModel = asking(asker)

        viewModel.enough()
        advanceUntilIdle()

        assertThat(asker.finishCalls.single().asked).isEmpty()
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Proposed::class.java)
    }

    @Test
    fun `best guess from the offer sends no answers`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3))
        val viewModel = viewModel(asker)
        viewModel.describe(MEAL)
        advanceUntilIdle()

        viewModel.bestGuess()
        advanceUntilIdle()

        assertThat(asker.finishCalls.single().asked).isEmpty()
    }

    @Test
    fun `the model saying no more questions goes to the final analysis`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), steps = mutableListOf(StepResult.Enough))
        val viewModel = asking(asker)

        viewModel.answer("Standard box")
        advanceUntilIdle()

        assertThat(asker.finishCalls.single().asked).hasSize(1)
    }

    /** The reviewer's case: a changed answer, and the questions after it never come back. */
    @Test
    fun `a changed answer after back drops the later questions for good`() = runTest {
        val asker = ScriptedAsker(
            opening = StepResult.Ask(q1, 3),
            steps = mutableListOf(StepResult.Ask(q2, 3), StepResult.Ask(q3, 3)),
        )
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()

        assertThat(viewModel.back()).isTrue()
        val back = viewModel.state.value as ProposalUiState.Asking
        assertThat(back.chat.shown).isEqualTo(q1)
        assertThat(back.chat.chosen).isEqualTo("Standard box")

        viewModel.answer("Small box")
        advanceUntilIdle()

        val asking = viewModel.state.value as ProposalUiState.Asking
        assertThat(asking.chat.questions).containsExactly(q1, q3).inOrder()
        assertThat(asking.chat.shown).isEqualTo(q3)
        assertThat(asker.nextCalls.last()).containsExactly(Asked(q1.text, "Small box"))
    }

    @Test
    fun `the same answer after back moves forward with nothing sent`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), steps = mutableListOf(StepResult.Ask(q2, 3)))
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()
        viewModel.back()

        viewModel.answer("standard box")
        advanceUntilIdle()

        assertThat((viewModel.state.value as ProposalUiState.Asking).chat.shown).isEqualTo(q2)
        assertThat(asker.nextCalls).hasSize(1)
    }

    /** §12.1: Back while waiting cancels, and a late reply is dropped. */
    @Test
    fun `back while a question is on its way returns, and the late reply is dropped`() = runTest {
        val gate = CompletableDeferred<StepResult>()
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), stepGate = gate)
        val viewModel = asking(asker)

        viewModel.answer("Standard box")
        advanceUntilIdle()
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Waiting::class.java)

        assertThat(viewModel.back()).isTrue()
        gate.complete(StepResult.Ask(q2, 3))
        advanceUntilIdle()

        val asking = viewModel.state.value as ProposalUiState.Asking
        assertThat(asking.chat.shown).isEqualTo(q1)
    }

    @Test
    fun `two taps on an answer send one request`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), steps = mutableListOf(StepResult.Ask(q2, 3)))
        val viewModel = asking(asker)

        viewModel.answer("Standard box")
        viewModel.answer("Standard box")
        advanceUntilIdle()

        assertThat(asker.nextCalls).hasSize(1)
    }

    @Test
    fun `back steps from the first question to the offer, and from the offer to his words`() = runTest {
        val viewModel = asking(ScriptedAsker(opening = StepResult.Ask(q1, 3)))

        assertThat(viewModel.back()).isTrue()
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Offer::class.java)
        assertThat(viewModel.back()).isTrue()
        assertThat(viewModel.state.value).isEqualTo(ProposalUiState.Describing())
        assertThat(viewModel.description).isEqualTo(MEAL)
        assertThat(viewModel.back()).isFalse()
    }

    @Test
    fun `a failed step offers to try again, or to guess with what was said`() = runTest {
        val asker = ScriptedAsker(
            opening = StepResult.Ask(q1, 3),
            steps = mutableListOf(StepResult.Failed(EstimateResult.Unreadable("x", "not the shape")), StepResult.Ask(q2, 3)),
        )
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()

        val failed = viewModel.state.value as ProposalUiState.ConversationFailed
        assertThat(failed.answer).isEqualTo("not the shape")
        assertThat(failed.bestGuessWith).containsExactly(Asked(q1.text, "Standard box"))

        viewModel.retry()
        advanceUntilIdle()
        assertThat((viewModel.state.value as ProposalUiState.Asking).chat.shown).isEqualTo(q2)
        assertThat(asker.nextCalls).hasSize(2)
    }

    /** The reviewer's case: Try again never spends the request kept for the result (§7, §12.4). */
    @Test
    fun `try again with one request left goes to the result, as an everyday request`() = runTest {
        val asker = ScriptedAsker(
            opening = StepResult.Ask(q1, 3),
            steps = mutableListOf(StepResult.Failed(EstimateResult.Unreachable())),
        )
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()
        asker.remaining = 1

        viewModel.retry()
        advanceUntilIdle()

        assertThat(asker.nextCalls).hasSize(1)
        assertThat(asker.finishCalls.single().deep).isFalse()
        assertThat(asker.finishCalls.single().asked).containsExactly(Asked(q1.text, "Standard box"))
    }

    @Test
    fun `back from a failure returns to the question it came from, however often it was retried`() = runTest {
        val asker = ScriptedAsker(
            opening = StepResult.Ask(q1, 3),
            steps = mutableListOf(
                StepResult.Failed(EstimateResult.Unreachable()),
                StepResult.Failed(EstimateResult.Unreachable()),
            ),
        )
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()
        viewModel.retry()
        advanceUntilIdle()
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.ConversationFailed::class.java)

        assertThat(viewModel.back()).isTrue()

        assertThat((viewModel.state.value as ProposalUiState.Asking).chat.shown).isEqualTo(q1)
    }

    /** §12.1: Back during the first request returns to his words, and the late reply is dropped. */
    @Test
    fun `back during the first request returns to his words`() = runTest {
        val gate = CompletableDeferred<StepResult>()
        val viewModel = viewModel(ScriptedAsker(opening = StepResult.Ask(q1, 3), openGate = gate))

        viewModel.describe(MEAL)
        advanceUntilIdle()
        assertThat(viewModel.back()).isTrue()
        gate.complete(StepResult.Ask(q1, 3))
        advanceUntilIdle()

        assertThat(viewModel.state.value).isEqualTo(ProposalUiState.Describing())
        assertThat(viewModel.description).isEqualTo(MEAL)
    }

    @Test
    fun `best guess after a failed step sends the answers so far`() = runTest {
        val asker = ScriptedAsker(
            opening = StepResult.Ask(q1, 3),
            steps = mutableListOf(StepResult.Failed(EstimateResult.Unreachable())),
        )
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()

        viewModel.bestGuessSoFar()
        advanceUntilIdle()

        assertThat(asker.finishCalls.single().asked).containsExactly(Asked(q1.text, "Standard box"))
    }

    @Test
    fun `with one request left, no question is asked and the result is an everyday request`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), remaining = 3, finishGate = CompletableDeferred())
        val viewModel = asking(asker)
        asker.remaining = 1

        viewModel.answer("Standard box")
        advanceUntilIdle()

        // Said while it is worked out (§7).
        val waiting = viewModel.state.value as ProposalUiState.Waiting
        assertThat(waiting.kind).isEqualTo(WaitingFor.RESULT)
        assertThat(waiting.allowanceOnly).isTrue()
        asker.finishGate!!.complete(Unit)
        advanceUntilIdle()

        assertThat(asker.nextCalls).isEmpty()
        assertThat(asker.finishCalls.single().deep).isFalse()
    }

    @Test
    fun `with nothing left, the ceiling is said and his answers kept`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), remaining = 3)
        val viewModel = asking(asker)
        asker.remaining = 0

        viewModel.answer("Standard box")
        advanceUntilIdle()

        val failed = viewModel.state.value as ProposalUiState.ConversationFailed
        assertThat(failed.failure).startsWith("You have used today's estimates")
        assertThat(failed.retry).isNull()
        assertThat(asker.finishCalls).isEmpty()
    }

    @Test
    fun `with nothing left after the first request, no questions are offered`() = runTest {
        val viewModel = viewModel(ScriptedAsker(opening = StepResult.Ask(q1, 3), remaining = 0))

        viewModel.describe(MEAL)
        advanceUntilIdle()

        val describing = viewModel.state.value as ProposalUiState.Describing
        assertThat(describing.failure).startsWith("You have used today's estimates")
    }

    @Test
    fun `ask again after a conversation re-runs the final analysis with the same answers`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 1))
        val viewModel = asking(asker)
        viewModel.answer("Standard box")
        advanceUntilIdle()

        viewModel.tellItMore("no cheese after all")
        advanceUntilIdle()

        val again = asker.finishCalls.last()
        assertThat(again.asked).containsExactly(Asked(q1.text, "Standard box"))
        assertThat(again.moreDetail).isEqualTo("no cheese after all")
        assertThat((viewModel.state.value as ProposalUiState.Proposed).afterConversation).isEqualTo(again.asked)
    }

    /** §12.3: anything thrown mid-conversation is said where he is, and the conversation stays. */
    @Test
    fun `a request that throws keeps the question on screen and says so`() = runTest {
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 3), throwOnStep = true)
        val viewModel = asking(asker)

        viewModel.answer("Standard box")
        advanceUntilIdle()

        val asking = viewModel.state.value as ProposalUiState.Asking
        assertThat(asking.chat.shown).isEqualTo(q1)
        assertThat(asking.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
    }

    /** Nothing is stored before the end choice; his foods are read only once the result is in (D53 §4). */
    @Test
    fun `no food is read until the result arrives`() = runTest {
        val foods = CountingFoods(FakeFoodRepository())
        val asker = ScriptedAsker(opening = StepResult.Ask(q1, 2), steps = mutableListOf(StepResult.Ask(q2, 2)))
        val viewModel = viewModel(asker, foods)
        viewModel.describe(MEAL)
        advanceUntilIdle()
        viewModel.acceptQuestions()
        viewModel.answer("Standard box")
        advanceUntilIdle()
        assertThat(foods.reads).isEqualTo(0)

        viewModel.answer("Creamy")
        advanceUntilIdle()
        assertThat(foods.reads).isEqualTo(1)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.asking(asker: ScriptedAsker): ProposalViewModel {
        val viewModel = viewModel(asker)
        viewModel.describe(MEAL)
        advanceUntilIdle()
        viewModel.acceptQuestions()
        return viewModel
    }

    private fun viewModel(asker: ScriptedAsker, foods: FoodRepository = FakeFoodRepository()) =
        ProposalViewModel(NoEstimator, ProblemLog.NONE, foods, SavedStateHandle(), asker)

    private object NoEstimator : MealEstimator {
        override suspend fun estimate(description: String, moreDetail: String?): EstimateResult =
            error("a conversation's view model asks through the conversation")
    }

    private class CountingFoods(private val inner: FakeFoodRepository) : FoodRepository by inner {
        var reads = 0
            private set

        override fun observeOffered(): Flow<List<Food>> = inner.observeOffered().onStart { reads++ }
    }

    data class FinishCall(val asked: List<Asked>, val moreDetail: String?, val deep: Boolean)

    private class ScriptedAsker(
        private val opening: StepResult,
        private val steps: MutableList<StepResult> = mutableListOf(),
        private val final: EstimateResult = EstimateResult.Proposed(aProposal()),
        var remaining: Int = 20,
        private val stepGate: CompletableDeferred<StepResult>? = null,
        private val throwOnStep: Boolean = false,
        private val openGate: CompletableDeferred<StepResult>? = null,
        val finishGate: CompletableDeferred<Unit>? = null,
    ) : MealConversationAsker {
        val nextCalls = mutableListOf<List<Asked>>()
        val finishCalls = mutableListOf<FinishCall>()

        override suspend fun open(description: String): StepResult = openGate?.await() ?: opening

        override suspend fun next(description: String, asked: List<Asked>, cap: Int): StepResult {
            nextCalls += asked
            if (throwOnStep) throw IllegalStateException("unexpected")
            stepGate?.let { return it.await() }
            return steps.removeAt(0)
        }

        override suspend fun finish(
            description: String,
            asked: List<Asked>,
            moreDetail: String?,
            deep: Boolean,
        ): EstimateResult {
            finishCalls += FinishCall(asked, moreDetail, deep)
            finishGate?.await()
            return final
        }

        override suspend fun remainingToday(): Int = remaining
    }

    private companion object {
        const val MEAL = "pasta with a mushroom sauce from a takeaway counter"
    }
}
