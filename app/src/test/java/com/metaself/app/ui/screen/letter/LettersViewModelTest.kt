package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.letter.InMemoryLetterSettingsStore
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.WeeklyLetterJob
import com.metaself.app.data.letter.WriteWeeklyLetter
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.letter.LetterWording
import com.metaself.app.ui.screen.trainer.FakeAiSettings
import com.metaself.app.ui.screen.trainer.TrainerScreens
import kotlinx.coroutines.CompletableDeferred
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
import java.time.LocalDate

/**
 * Weekly letters and Write it now (D99, D103, design question 19). Now is Monday 7 September 2026 at 15:00,
 * after the Monday-noon deadline, so the week offered is the one holding TEST_EPOCH_DAY. Every letter and
 * word is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LettersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
    private val problems = RecordingProblemLog()
    private val store = FakeLetterStore()
    private val job = FakeJob()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `nothing is asked when the page opens, and Write it now is offered for the week with no letter`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()

        assertThat(job.written).isEmpty()
        assertThat(viewModel.state.value.writeWeek).isEqualTo(LETTER_MONDAY)
        assertThat(viewModel.state.value.canWriteNow).isTrue()
    }

    @Test
    fun `a week that has its letter, or is quiet, is not offered`() = runTest {
        job.wanted = false
        val viewModel = watched()
        advanceUntilIdle()

        assertThat(viewModel.state.value.canWriteNow).isFalse()
    }

    @Test
    fun `the letters are listed newest first, with New on the unread`() = runTest {
        store.add(aWeeklyLetter(LETTER_MONDAY - 7).copy(readAtMillis = 1_000))
        store.add(aWeeklyLetter(LETTER_MONDAY))
        val viewModel = watched()
        advanceUntilIdle()

        assertThat(viewModel.state.value.letters).containsExactly(
            LettersViewModel.Row(LETTER_MONDAY, "31 Aug – 6 Sep", "Invented headline.", new = true),
            LettersViewModel.Row(LETTER_MONDAY - 7, "24 Aug – 30 Aug", "Invented headline.", new = false),
        ).inOrder()
    }

    @Test
    fun `a double tap asks once, and the letter written takes Write it now away`() = runTest {
        val gate = CompletableDeferred<Unit>()
        job.before = { gate.await() }
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.writeNow()
        viewModel.writeNow()
        advanceUntilIdle()
        assertThat(viewModel.state.value.writing).isTrue()
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(job.written).containsExactly(LETTER_MONDAY to false)
        assertThat(viewModel.state.value.writing).isFalse()
        assertThat(viewModel.state.value.canWriteNow).isFalse()
        assertThat(store.letters.value.map { it.weekMonday }).containsExactly(LETTER_MONDAY)
    }

    @Test
    fun `a quiet week says there is nothing to write`() = runTest {
        job.outcome = { WriteWeeklyLetter.Outcome.Quiet }
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.writeNow()
        advanceUntilIdle()

        assertThat(viewModel.state.value.said).isEqualTo(LetterWording.QUIET)
    }

    @Test
    fun `a failed ask is worded as the trainer's are`() = runTest {
        job.outcome = { WriteWeeklyLetter.Outcome.GiveUp(EstimateResult.NoKey) }
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.writeNow()
        advanceUntilIdle()

        assertThat(viewModel.state.value.said).isEqualTo("No API key yet. Add one in settings.")
    }

    /** D8: as the Sunday run — the failure's kind and class, never its message, which here holds the letter's words. */
    @Test
    fun `a failure with the letter's words in its message is logged by kind alone, and said`() = runTest {
        job.outcome = { throw IllegalStateException("Invented headline. Invented note.") }
        val viewModel = watched()
        advanceUntilIdle()

        viewModel.writeNow()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.writing).isFalse()
        assertThat(problems.recorded.map { it.kind to it.detail }).containsExactly("letter" to "not written: IllegalStateException")
        assertThat(problems.recorded.joinToString { it.detail }).doesNotContain("Invented")
    }

    private fun TestScope.watched(): LettersViewModel {
        val make = {
            LettersViewModel(store, job, InMemoryLetterSettingsStore(), FakeAiSettings(), problems, outliving) { NOW }
        }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[LettersViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }

    /** Writes an invented letter unless told otherwise; [wanted] a week with none. */
    private inner class FakeJob : WeeklyLetterJob {
        val written = mutableListOf<Pair<Long, Boolean>>()
        var wanted = true
        var before: suspend () -> Unit = {}
        var outcome: suspend (Long) -> WriteWeeklyLetter.Outcome = { week ->
            val letter = aWeeklyLetter(week)
            store.add(letter)
            WriteWeeklyLetter.Outcome.Written(letter)
        }

        override suspend fun write(weekMonday: Long, copy: Boolean): WriteWeeklyLetter.Outcome {
            written += weekMonday to copy
            before()
            return outcome(weekMonday)
        }

        override suspend fun wanted(weekMonday: Long): Boolean = wanted && store.of(weekMonday) == null
    }

    private companion object {
        val NOW = LocalDate.ofEpochDay(LETTER_MONDAY + 7).atTime(15, 0)
    }
}
