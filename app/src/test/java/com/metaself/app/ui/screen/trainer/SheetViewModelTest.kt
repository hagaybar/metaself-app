package com.metaself.app.ui.screen.trainer

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.InMemorySheetStore
import com.metaself.app.data.trainer.TrainerSheets
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetMadeBy
import com.metaself.app.domain.trainer.Sheets
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.trainer.SheetWording
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
import java.time.LocalDate

/** What I know about you (D149, D150): edit, remove, go back, and refusals said. Every fact is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class SheetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sheets = InMemorySheetStore()
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an edit is saved as his words, and the dialog closes`() = runTest {
        sheets.add(NOW - 1, listOf(FACT), SheetMadeBy.REWRITE, "a-model")
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.startEdit(1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.editing).isEqualTo(SheetViewModel.Editing(1, "An invented fact."))
        viewModel.editText("His invented words.")
        viewModel.saveEdit()
        advanceUntilIdle()

        assertThat(viewModel.state.value.editing).isNull()
        assertThat(viewModel.state.value.newest!!.current.single().text).isEqualTo("His invented words.")
        assertThat(sheets.versions.value.last().madeBy).isEqualTo(SheetMadeBy.OWNER)
    }

    @Test
    fun `a refused edit keeps the dialog open and says why`() = runTest {
        sheets.add(NOW - 1, listOf(FACT), SheetMadeBy.REWRITE, "a-model")
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.startEdit(1)
        advanceUntilIdle()
        viewModel.editText("w".repeat(Sheets.MAX_FACT + 1))
        viewModel.saveEdit()
        advanceUntilIdle()

        assertThat(viewModel.state.value.editing).isNotNull()
        assertThat(viewModel.state.value.editRefused).isEqualTo("A fact is at most 300 characters.")
        assertThat(sheets.versions.value).hasSize(1)
    }

    @Test
    fun `remove asks first, then moves the fact to no longer true`() = runTest {
        sheets.add(NOW - 1, listOf(FACT), SheetMadeBy.REWRITE, "a-model")
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.askRemove(1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.removing).isEqualTo(1L)
        viewModel.confirmRemove()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.newest!!.current).isEmpty()
        assertThat(state.dropped.single().removedByOwner).isTrue()
    }

    @Test
    fun `go back is offered for the newest rewrite, asks first, and restores the version before it`() = runTest {
        sheets.add(NOW - 2, listOf(FACT), SheetMadeBy.REWRITE, "a-model")
        sheets.add(NOW - 1, listOf(FACT.copy(id = 2, text = "Wrong.")), SheetMadeBy.REWRITE, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.canGoBack).isTrue()

        viewModel.askGoBack()
        viewModel.confirmGoBack()
        advanceUntilIdle()

        assertThat(viewModel.state.value.newest!!.current).containsExactly(FACT)
        assertThat(viewModel.state.value.canGoBack).isFalse()
    }

    @Test
    fun `a write that fails says nothing changed`() = runTest {
        sheets.add(NOW - 1, listOf(FACT), SheetMadeBy.REWRITE, "a-model")
        val viewModel = watched()
        advanceUntilIdle()
        sheets.failWrites = true

        viewModel.askRemove(1)
        viewModel.confirmRemove()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.busy).isFalse()
        assertThat(SheetWording.refused(TrainerSheets.Edit.GONE)).isNotNull()
    }

    private fun TestScope.watched(): SheetViewModel {
        val owner = TrainerSheets(sheets, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, Now { NOW })
        val make = { SheetViewModel(owner, Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }, problems) }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[SheetViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    private companion object {
        const val NOW = TEST_EPOCH_DAY * 86_400_000L + 12 * 3_600_000L
        val FACT = SheetFact(1, FactGroup.TRAINING, "An invented fact.", TEST_EPOCH_DAY - 5, FactSource.SAID)
    }
}
