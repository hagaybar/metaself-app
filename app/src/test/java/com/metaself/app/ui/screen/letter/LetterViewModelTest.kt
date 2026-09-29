package com.metaself.app.ui.screen.letter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.LetterStore
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.time.Now
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.screen.trainer.TrainerScreens
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

/** One weekly letter's page (D103): shown, and marked read the first time. Every letter is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class LetterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun opened(store: LetterStore, week: Long = LETTER_MONDAY): LetterViewModel {
        val make = { LetterViewModel(SavedStateHandle(mapOf(LetterViewModel.WEEK to week)), store, Now { READ_AT }, problems) }
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

    private companion object {
        const val READ_AT = 5_000L
    }
}
