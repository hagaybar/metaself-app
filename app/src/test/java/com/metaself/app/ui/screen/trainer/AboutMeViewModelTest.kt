package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.ui.ActionRefused
import com.metaself.app.ui.RecordingProblemLog
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

/** D90: the page where the owner writes the note the trainer always gets. Every word is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutMeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val store = InMemoryAboutMeStore("Invented note.")
    private val problems = RecordingProblemLog()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the page opens on the stored note`() = runTest {
        val viewModel = AboutMeViewModel(store, problems)
        advanceUntilIdle()

        assertThat(viewModel.state.value.text).isEqualTo("Invented note.")
        assertThat(viewModel.state.value.loaded).isTrue()
    }

    @Test
    fun `the field takes at most a thousand characters`() = runTest {
        val viewModel = AboutMeViewModel(store, problems)
        advanceUntilIdle()

        viewModel.edit("b".repeat(1_200))

        assertThat(viewModel.state.value.text).hasLength(1_000)
    }

    @Test
    fun `the field's limit never splits a character in two`() = runTest {
        val viewModel = AboutMeViewModel(store, problems)
        advanceUntilIdle()

        viewModel.edit("b".repeat(999) + "\uD83D\uDE00")

        assertThat(viewModel.state.value.text).isEqualTo("b".repeat(999))
    }

    @Test
    fun `save stores the note and says it is saved`() = runTest {
        val viewModel = AboutMeViewModel(store, problems)
        advanceUntilIdle()
        viewModel.edit("  An invented knee, and a treadmill at home.  ")

        viewModel.save()
        advanceUntilIdle()

        assertThat(store.note.value).isEqualTo("An invented knee, and a treadmill at home.")
        assertThat(viewModel.state.value.saved).isTrue()
        assertThat(viewModel.state.value.saving).isFalse()
    }

    /** Before the stored note is read, Save would write over it with an empty field: it waits. */
    @Test
    fun `nothing is saved before the note has been read`() = runTest {
        val viewModel = AboutMeViewModel(store, problems)

        viewModel.save()
        advanceUntilIdle()

        assertThat(store.note.value).isEqualTo("Invented note.")
        assertThat(viewModel.state.value.saved).isFalse()
    }

    @Test
    fun `a failed save says nothing changed and keeps the words on screen`() = runTest {
        val viewModel = AboutMeViewModel(store, problems)
        advanceUntilIdle()
        viewModel.edit("A different invented note.")
        store.failing = true

        viewModel.save()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.saved).isFalse()
        assertThat(viewModel.state.value.text).isEqualTo("A different invented note.")
        assertThat(problems.recorded.map { it.kind }).containsExactly("refused")
    }
}
