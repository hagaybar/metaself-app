package com.metaself.app.ui.screen.propose

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.food.FakeFoodRepository
import com.metaself.app.data.food.MealKeeper
import com.metaself.app.data.food.ToLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.MealEstimator
import com.metaself.app.domain.ai.aProposal
import com.metaself.app.domain.ai.aProposedItem
import com.metaself.app.ui.ActionRefused
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** *Keep as a meal*, logging nothing (D58 §5.2, §12.7). Every meal here is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepOnlyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `kept, the rows go to the keeper under the name, and he is taken on`() = runTest {
        val keeper = RecordingKeeper(MealKeeper.Kept.Made(7))
        val viewModel = proposed(keeper)
        var kept = 0

        viewModel.openKeepOnly()
        assertThat(proposedState(viewModel).keeping).isEqualTo(KeepOnly())
        viewModel.keepOnly("Counter lunch") { kept++ }
        advanceUntilIdle()

        assertThat(keeper.calls.single().first).isEqualTo("Counter lunch")
        assertThat(keeper.calls.single().second.map { it.item.name }).containsExactly("Beef burger", "Hamburger bun")
        assertThat(kept).isEqualTo(1)
        assertThat(proposedState(viewModel).keeping).isNull()
    }

    @Test
    fun `a name taken is said in the sheet, and nothing moves on`() = runTest {
        val viewModel = proposed(RecordingKeeper(MealKeeper.Kept.NameTaken("Counter lunch")))
        var kept = 0

        viewModel.openKeepOnly()
        viewModel.keepOnly("Counter lunch") { kept++ }
        advanceUntilIdle()

        val sheet = proposedState(viewModel).keeping!!
        assertThat(sheet.refusal).isEqualTo("You already have a meal called “Counter lunch”.")
        assertThat(sheet.canLogInstead).isFalse()
        assertThat(kept).isEqualTo(0)
    }

    @Test
    fun `parts that cannot join are said, with log it instead`() = runTest {
        val why = "Hamburger bun is in bun, and your Hamburger bun is counted in slice."
        val viewModel = proposed(RecordingKeeper(MealKeeper.Kept.Refused(listOf(why))))

        viewModel.openKeepOnly()
        viewModel.keepOnly("Counter lunch") {}
        advanceUntilIdle()

        val sheet = proposedState(viewModel).keeping!!
        assertThat(sheet.refusal).isEqualTo(why)
        assertThat(sheet.canLogInstead).isTrue()
    }

    @Test
    fun `a keep that throws says nothing was changed, and the rows stay`() = runTest {
        val viewModel = proposed(RecordingKeeper(null))

        viewModel.openKeepOnly()
        viewModel.keepOnly("Counter lunch") {}
        advanceUntilIdle()

        val state = proposedState(viewModel)
        assertThat(state.keeping!!.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(state.rows).hasSize(2)
    }

    @Test
    fun `one row is a food, and is not kept as a meal`() = runTest {
        val viewModel = proposed(RecordingKeeper(MealKeeper.Kept.Made(7)), oneRow = true)

        viewModel.openKeepOnly()

        assertThat(proposedState(viewModel).keeping).isNull()
    }

    @Test
    fun `not now closes the sheet and keeps the rows`() = runTest {
        val viewModel = proposed(RecordingKeeper(MealKeeper.Kept.Made(7)))

        viewModel.openKeepOnly()
        viewModel.closeKeepOnly()

        assertThat(proposedState(viewModel).keeping).isNull()
        assertThat(proposedState(viewModel).rows).hasSize(2)
    }

    private fun TestScope.proposed(keeper: MealKeeper, oneRow: Boolean = false): ProposalViewModel {
        val proposal = if (oneRow) aProposal(items = listOf(aProposedItem())) else aProposal()
        val estimator = object : MealEstimator {
            override suspend fun estimate(description: String, moreDetail: String?) =
                EstimateResult.Proposed(proposal)
        }
        val viewModel = ProposalViewModel(
            estimator, ProblemLog.NONE, FakeFoodRepository(), SavedStateHandle(), EstimatingAsker(estimator), keeper,
        )
        viewModel.describe("a burger in a bun")
        advanceUntilIdle()
        return viewModel
    }

    private fun proposedState(viewModel: ProposalViewModel) = viewModel.state.value as ProposalUiState.Proposed

    /** Answers with [kept], or throws when it is null. */
    private class RecordingKeeper(private val kept: MealKeeper.Kept?) : MealKeeper {
        val calls = mutableListOf<Pair<String, List<ToLog>>>()

        override suspend fun keep(name: String, rows: List<ToLog>): MealKeeper.Kept {
            calls += name to rows
            return kept ?: throw IllegalStateException("disk full")
        }
    }
}
