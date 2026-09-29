package com.metaself.app.ui.screen.letter

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.letter.InMemoryLetterSettingsStore
import com.metaself.app.data.letter.LetterScheduling
import com.metaself.app.ui.ActionRefused
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

/** The weekly letter's setting (D99): the switch, the hour, and the background ask. */
@OptIn(ExperimentalCoroutinesApi::class)
class LetterSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = InMemoryLetterSettingsStore()
    private val problems = RecordingProblemLog()
    private var scheduled = 0
    private var failSchedule = false
    private val scheduler = object : LetterScheduling {
        override suspend fun schedule() {
            if (failSchedule) throw IllegalStateException("invented")
            scheduled++
        }
    }
    private var offered = true
    private var granted = false
    private val background = object : BackgroundHealthRead {
        override suspend fun offered() = offered
        override suspend fun granted() = granted
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `on by default at 20, and each change is written and re-queues the Sunday run`() = runTest {
        val viewModel = watched()
        advanceUntilIdle()
        assertThat(viewModel.state.value.on).isTrue()
        assertThat(viewModel.state.value.hour).isEqualTo(20)

        viewModel.setHour(22)
        advanceUntilIdle()
        viewModel.setOn(false)
        advanceUntilIdle()

        assertThat(settings.settings.value.hour).isEqualTo(22)
        assertThat(settings.settings.value.on).isFalse()
        assertThat(viewModel.state.value.on).isFalse()
        assertThat(scheduled).isEqualTo(2)
    }

    @Test
    fun `a change that cannot be queued is said`() = runTest {
        failSchedule = true
        val viewModel = watched()
        viewModel.setOn(false)
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.MAYBE_PARTIAL)
    }

    @Test
    fun `the background ask shows only where it is offered and not held`() = runTest {
        val viewModel = watched()
        viewModel.lookedAt()
        advanceUntilIdle()
        assertThat(viewModel.state.value.askBackground).isTrue()

        granted = true
        viewModel.lookedAt()
        advanceUntilIdle()
        assertThat(viewModel.state.value.askBackground).isFalse()

        granted = false
        offered = false
        viewModel.lookedAt()
        advanceUntilIdle()
        assertThat(viewModel.state.value.askBackground).isFalse()
    }

    private fun TestScope.watched(): LetterSettingsViewModel {
        val make = { LetterSettingsViewModel(settings, scheduler, background, problems) }
        val viewModel = ViewModelProvider(ViewModelStore(), TrainerScreens.factory(make))[LetterSettingsViewModel::class.java]
        backgroundScope.launch { viewModel.state.collect {} }
        return viewModel
    }
}
