package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.letter.FakeLetterStore
import com.metaself.app.data.letter.LETTER_MONDAY
import com.metaself.app.data.letter.InMemoryLetterNoteStore
import com.metaself.app.data.letter.aWeeklyLetter
import com.metaself.app.data.time.Today
import com.metaself.app.ui.RecordingProblemLog
import com.metaself.app.ui.screen.trainer.TrainerScreens
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

/** The day's note about the weekly letter (D103). The letters are invented; their week holds TEST_EPOCH_DAY. */
@OptIn(ExperimentalCoroutinesApi::class)
class UnreadLetterViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = FakeLetterStore()
    private val notes = InMemoryLetterNoteStore()
    private var today = LETTER_MONDAY + 7

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the newest unread letter is noted with its headline`() = runTest {
        store.add(aWeeklyLetter(LETTER_MONDAY - 7))
        store.add(aWeeklyLetter(LETTER_MONDAY))
        val viewModel = watched()
        advanceUntilIdle()

        assertThat(viewModel.unread.value).isEqualTo(UnreadLetterViewModel.Unread(LETTER_MONDAY, "Invented headline."))
    }

    @Test
    fun `a letter read, put away, or more than a week past its Sunday is not noted`() = runTest {
        store.add(aWeeklyLetter(LETTER_MONDAY))
        val viewModel = watched()
        advanceUntilIdle()
        viewModel.dismiss(LETTER_MONDAY)
        advanceUntilIdle()
        assertThat(viewModel.unread.value).isNull()

        val read = FakeLetterStore(aWeeklyLetter(LETTER_MONDAY).copy(readAtMillis = 1_000))
        assertThat(watched(read).unread.also { advanceUntilIdle() }.value).isNull()

        today = LETTER_MONDAY + 6 + 8
        notes.dismissed.value = null
        assertThat(watched().unread.also { advanceUntilIdle() }.value).isNull()
    }

    private fun TestScope.watched(letters: FakeLetterStore = store): UnreadLetterViewModel {
        val make = { UnreadLetterViewModel(letters, notes, Today { LocalDate.ofEpochDay(today) }, RecordingProblemLog()) }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[UnreadLetterViewModel::class.java]
        backgroundScope.launch { viewModel.unread.collect {} }
        return viewModel
    }
}
