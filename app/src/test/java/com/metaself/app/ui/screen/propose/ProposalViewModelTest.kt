package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.PortionScale
import com.metaself.app.domain.ai.aProposal
import com.metaself.app.domain.ai.aProposedItem
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProposalViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a described meal becomes rows, one per component`() = runTest {
        val viewModel = ProposalViewModel(FakeEstimator(EstimateResult.Proposed(aProposal())))

        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.rows).hasSize(2)
        assertThat(proposed.totalKcal).isEqualTo(685)
    }

    @Test
    fun `scaling one row leaves the others alone`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.scale(0, PortionScale.MORE)

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.rows[0].current.kcal).isEqualTo(608)
        assertThat(proposed.rows[1].current.kcal).isEqualTo(280)
    }

    @Test
    fun `scaling always works from what the model said, never from an already scaled row`() =
        runTest {
            val viewModel = proposedViewModel()

            viewModel.scale(0, PortionScale.MORE)
            viewModel.scale(0, PortionScale.AS_DESCRIBED)

            val proposed = viewModel.state.value as ProposalUiState.Proposed
            assertThat(proposed.rows[0].current).isEqualTo(proposed.rows[0].asProposed)
        }

    @Test
    fun `a count sets how many there were`() = runTest {
        val pizza = aProposedItem(
            name = "Pizza",
            portionAmount = 1.0,
            portionUnit = "slice",
            kcal = 285,
            proteinG = 12,
            carbsG = 36,
            fatG = 10,
        )
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.Proposed(aProposal(items = listOf(pizza)))),
        )
        viewModel.describe("pizza")
        advanceUntilIdle()

        viewModel.setCount(0, 3)

        val proposed = viewModel.state.value as ProposalUiState.Proposed
        assertThat(proposed.rows[0].current.kcal).isEqualTo(855)
    }

    @Test
    fun `a row can be removed, and removing the last one starts over`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.remove(1)
        assertThat((viewModel.state.value as ProposalUiState.Proposed).rows).hasSize(1)

        viewModel.remove(0)
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    @Test
    fun `what is accepted is an AI estimate with its confidence intact`() = runTest {
        val viewModel = proposedViewModel()

        viewModel.scale(0, PortionScale.MORE)
        val accepted = viewModel.accepted()

        assertThat(accepted).hasSize(2)
        assertThat(accepted.all { it.source == Source.AI_ESTIMATE }).isTrue()
        assertThat(accepted[0].confidence).isEqualTo(Confidence.MEDIUM)
        assertThat(accepted[0].kcal).isEqualTo(608)
        assertThat(accepted[0].portion).isEqualTo("420 g")
    }

    @Test
    fun `a failure keeps the owner's words and says what went wrong`() = runTest {
        val viewModel = ProposalViewModel(FakeEstimator(EstimateResult.Unreachable))

        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()

        val describing = viewModel.state.value as ProposalUiState.Describing
        assertThat(describing.failure).contains("Could not reach")
        assertThat(viewModel.description).isEqualTo("risotto with mozzarella")
    }

    /**
     * No amount even when asked twice: nothing is proposed, and the owner is told which items and
     * how to give amounts himself (D34). His words stay, so saying the amount costs one sentence.
     */
    @Test
    fun `a reply that would not give amounts says which, and how to give them`() = runTest {
        val viewModel = ProposalViewModel(FakeEstimator(EstimateResult.AmountMissing(listOf("Stew"))))

        viewModel.describe("stew")
        advanceUntilIdle()

        val failure = (viewModel.state.value as ProposalUiState.Describing).failure
        assertThat(failure).contains("Stew")
        assertThat(failure).contains("Say the amounts in your description")
        assertThat(viewModel.description).isEqualTo("stew")
    }

    @Test
    fun `no key says so, and does not pretend the network failed`() = runTest {
        val viewModel = ProposalViewModel(FakeEstimator(EstimateResult.NoKey))

        viewModel.describe("risotto")
        advanceUntilIdle()

        assertThat((viewModel.state.value as ProposalUiState.Describing).failure)
            .contains("No API key")
    }

    @Test
    fun `telling it more asks again with both the original and the addition`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))
        val viewModel = ProposalViewModel(estimator)
        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()

        viewModel.tellItMore("small bowl, half the cheese")
        advanceUntilIdle()

        assertThat(estimator.lastDescription).isEqualTo("risotto with mozzarella")
        assertThat(estimator.lastDetail).isEqualTo("small bowl, half the cheese")
    }

    @Test
    fun `an empty description asks nothing at all`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))

        ProposalViewModel(estimator).describe("   ")
        advanceUntilIdle()

        assertThat(estimator.calls).isEqualTo(0)
    }

    @Test
    fun `words carried in from the search start the description already filled`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.Proposed(aProposal())),
            SavedStateHandle(mapOf("text" to "shakshuka")),
        )

        assertThat(viewModel.description).isEqualTo("shakshuka")
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    /**
     * D8, and the most important assertion in this file: arriving with the box filled is not the
     * same as asking. The model is asked when the owner presses the button, never on his behalf.
     */
    @Test
    fun `carried words do not ask the model by themselves`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))

        ProposalViewModel(estimator, SavedStateHandle(mapOf("text" to "shakshuka")))
        advanceUntilIdle()

        assertThat(estimator.calls).isEqualTo(0)
    }

    /** Guards every call site above, which constructs the view model with no handle at all. */
    @Test
    fun `no carried words leaves the description empty`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))

        val viewModel = ProposalViewModel(estimator, SavedStateHandle())
        advanceUntilIdle()

        assertThat(viewModel.description).isEqualTo("")
        assertThat(estimator.calls).isEqualTo(0)
    }

    /** The seed is read once, at construction, so starting over cannot resurrect it. */
    @Test
    fun `starting over clears words that were carried in`() = runTest {
        val viewModel = ProposalViewModel(
            FakeEstimator(EstimateResult.Proposed(aProposal())),
            SavedStateHandle(mapOf("text" to "shakshuka")),
        )

        viewModel.startOver()

        assertThat(viewModel.description).isEqualTo("")
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    /**
     * Starting over leaves nothing that could be accepted a second time (D46, issue #24).
     *
     * Both ways off this screen end the same way, and must: accepting plainly starts over, and so
     * does keeping what was accepted as a named meal. Left standing, the answer would still be on
     * the screen when he came back to it — with rows already on the day — and one more press would
     * log every one of them again.
     */
    @Test
    fun `starting over leaves nothing that could be accepted again`() = runTest {
        val viewModel = ProposalViewModel(FakeEstimator(EstimateResult.Proposed(aProposal())))
        viewModel.describe("risotto with mozzarella")
        advanceUntilIdle()
        assertThat(viewModel.accepted()).isNotEmpty()

        viewModel.startOver()

        assertThat(viewModel.accepted()).isEmpty()
        assertThat(viewModel.state.value).isInstanceOf(ProposalUiState.Describing::class.java)
    }

    @Test
    fun `telling it more works from words that were carried in`() = runTest {
        val estimator = FakeEstimator(EstimateResult.Proposed(aProposal()))
        val viewModel = ProposalViewModel(estimator, SavedStateHandle(mapOf("text" to "shakshuka")))

        viewModel.describe("shakshuka")
        advanceUntilIdle()
        viewModel.tellItMore("with two eggs")
        advanceUntilIdle()

        assertThat(estimator.lastDescription).isEqualTo("shakshuka")
        assertThat(estimator.lastDetail).isEqualTo("with two eggs")
    }

    private fun proposedViewModel(): ProposalViewModel {
        val viewModel = ProposalViewModel(FakeEstimator(EstimateResult.Proposed(aProposal())))
        viewModel.describe("risotto with mozzarella")
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private class FakeEstimator(private val result: EstimateResult) : MealEstimator {
        var calls = 0
            private set
        var lastDescription: String? = null
            private set
        var lastDetail: String? = null
            private set

        override suspend fun estimate(description: String, moreDetail: String?): EstimateResult {
            calls++
            lastDescription = description
            lastDetail = moreDetail
            return result
        }
    }
}
