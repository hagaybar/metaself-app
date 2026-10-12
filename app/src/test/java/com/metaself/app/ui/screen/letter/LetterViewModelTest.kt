package com.metaself.app.ui.screen.letter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.letter.WeeklyLetterJob
import com.metaself.app.data.letter.WriteWeeklyLetter
import com.metaself.app.data.letter.aCoachLetter
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.trainer.TrainerWording
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.time.Now
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.screen.trainer.TrainerScreens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * One weekly letter's page (D103): shown, and marked read the first time; the newest written again on a tap
 * and a confirmation (D144). Every letter is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LetterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val problems = RecordingProblemLog()
    private val outliving = CoroutineScope(SupervisorJob() + dispatcher)
    private val again = mutableListOf<Long>()

    /** What the job's writeAgain does; by default, an invented letter put in place of the week's. */
    private var writeAgain: suspend (LetterStore, Long) -> WriteWeeklyLetter.Outcome = { store, week ->
        val letter = aWeeklyLetter(week, aCoachLetter(nextWeek = "Invented again."))
        store.replace(letter)
        WriteWeeklyLetter.Outcome.Written(letter)
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() {
        outliving.cancel()
        Dispatchers.resetMain()
    }

    private fun opened(store: LetterStore, week: Long = LETTER_MONDAY): LetterViewModel {
        val job = object : WeeklyLetterJob {
            override suspend fun write(weekMonday: Long, copy: Boolean) = error("never from a letter's page")
            override suspend fun wanted(weekMonday: Long) = error("never from a letter's page")
            override suspend fun writeAgain(weekMonday: Long): WriteWeeklyLetter.Outcome {
                again += weekMonday
                return writeAgain(store, weekMonday)
            }
        }
        val make = { LetterViewModel(SavedStateHandle(mapOf(LetterViewModel.WEEK to week)), store, Now { READ_AT }, problems, job, outliving) }
        return ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[LetterViewModel::class.java]
    }

    @Test
    fun `the letter is shown and marked read, once`() = runTest {
        val store = FakeLetterStore(aWeeklyLetter())
        val viewModel = opened(store)
        advanceUntilIdle()

        assertThat((viewModel.state.value as LetterViewModel.State.Shown).letter.texts.headline).isEqualTo("Invented headline.")
        assertThat(store.letters.value.single().readAtMillis).isEqualTo(READ_AT)

        opened(store)
        advanceUntilIdle()
        assertThat(store.letters.value.single().readAtMillis).isEqualTo(READ_AT)
    }

    @Test
    fun `a letter already read keeps its first reading`() = runTest {
        val store = FakeLetterStore(aWeeklyLetter().copy(readAtMillis = 1_000))
        opened(store)
        advanceUntilIdle()

        assertThat(store.letters.value.single().readAtMillis).isEqualTo(1_000)
    }

    @Test
    fun `a store that cannot mark it read still shows the letter, and logs the failure by kind`() = runTest {
        val failing = object : LetterStore by FakeLetterStore(aWeeklyLetter()) {
            override suspend fun markRead(weekMonday: Long, atMillis: Long) = throw IllegalStateException("Invented headline.")
        }
        val viewModel = opened(failing)
        advanceUntilIdle()

        assertThat(viewModel.state.value).isInstanceOf(LetterViewModel.State.Shown::class.java)
        assertThat(problems.recorded.map { it.kind to it.detail }).containsExactly("letter" to "not marked read: IllegalStateException")
    }

    @Test
    fun `a week with no letter says so, and a read that fails says it could not be read`() = runTest {
        val missing = opened(FakeLetterStore())
        val failing = opened(object : LetterStore by FakeLetterStore() {
            override suspend fun of(weekMonday: Long) = throw IllegalStateException("Invented.")
        })
        advanceUntilIdle()

        assertThat(missing.state.value).isEqualTo(LetterViewModel.State.Missing)
        assertThat(failing.state.value).isEqualTo(LetterViewModel.State.Unreadable)
        assertThat(problems.recorded.map { it.detail }).containsExactly("not read: IllegalStateException")
    }

    @Test
    fun `only the newest letter can be written again, and only after its confirmation`() = runTest {
        val store = FakeLetterStore(aWeeklyLetter(LETTER_MONDAY), aWeeklyLetter(LETTER_MONDAY - 7))
        val older = opened(store, LETTER_MONDAY - 7)
        val newest = opened(store)
        advanceUntilIdle()

        assertThat(shown(older).newest).isFalse()
        older.offerAgain()
        older.writeAgain()
        assertThat(shown(older).confirming).isFalse()

        assertThat(shown(newest).newest).isTrue()
        // Not confirmed: nothing asked.
        newest.writeAgain()
        advanceUntilIdle()
        assertThat(again).isEmpty()

        newest.offerAgain()
        assertThat(shown(newest).confirming).isTrue()
        newest.keep()
        assertThat(shown(newest).confirming).isFalse()
        advanceUntilIdle()
        assertThat(again).isEmpty()
    }

    @Test
    fun `written again, the new letter replaces the old on the page and in the store`() = runTest {
        val store = FakeLetterStore(aWeeklyLetter())
        val viewModel = opened(store)
        advanceUntilIdle()

        viewModel.offerAgain()
        viewModel.writeAgain()
        advanceUntilIdle()

        assertThat(again).containsExactly(LETTER_MONDAY)
        val now = shown(viewModel)
        assertThat(now.letter.texts.nextWeek).isEqualTo("Invented again.")
        assertThat(now.writing).isFalse()
        assertThat(now.newest).isTrue()
        assertThat(store.letters.value.single().texts.nextWeek).isEqualTo("Invented again.")
    }

    @Test
    fun `a failure keeps the old letter and says why`() = runTest {
        val store = FakeLetterStore(aWeeklyLetter())
        writeAgain = { _, _ -> WriteWeeklyLetter.Outcome.Retry(EstimateResult.Unreachable()) }
        val viewModel = opened(store)
        advanceUntilIdle()
        val before = shown(viewModel).letter

        viewModel.offerAgain()
        viewModel.writeAgain()
        advanceUntilIdle()

        assertThat(shown(viewModel).letter).isEqualTo(before)
        assertThat(shown(viewModel).said).isEqualTo(TrainerWording.failure(EstimateResult.Unreachable()))
        assertThat(store.letters.value.single().texts.nextWeek).isEqualTo("Invented.")
    }

    @Test
    fun `a job that throws keeps the old letter, logged by kind`() = runTest {
        val store = FakeLetterStore(aWeeklyLetter())
        writeAgain = { _, _ -> throw IllegalStateException("Invented headline.") }
        val viewModel = opened(store)
        advanceUntilIdle()

        viewModel.offerAgain()
        viewModel.writeAgain()
        advanceUntilIdle()

        assertThat(shown(viewModel).refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(store.letters.value.single().texts.nextWeek).isEqualTo("Invented.")
        assertThat(problems.recorded.map { it.detail }).containsExactly("not written again: IllegalStateException")
    }

    private fun shown(viewModel: LetterViewModel) = viewModel.state.value as LetterViewModel.State.Shown

    private companion object {
        const val READ_AT = 5_000L
    }
}
