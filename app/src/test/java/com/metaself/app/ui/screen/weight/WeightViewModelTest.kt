package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.Today
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.data.weight.WeightRepository
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.aFortnight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WeightViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = Today { LocalDate.ofEpochDay(TEST_EPOCH_DAY) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with nothing logged there is no trend to show`() = runTest {
        val viewModel = WeightViewModel(
     FakeWeightRepository(),
     FakeProfileRepository(aProfile()),
     today,
 )

        val state = viewModel.state.first { it.readings.isEmpty() || it.trend.isNotEmpty() }

        assertThat(state.trend).isEmpty()
    }

    @Test
    fun `a fortnight of readings produces a trend point for each`() = runTest {
        val viewModel = WeightViewModel(
     FakeWeightRepository(aFortnight()),
     FakeProfileRepository(aProfile()),
     today,
 )

        val state = viewModel.state.first { it.trend.isNotEmpty() }

        assertThat(state.trend).hasSize(14)
    }

    @Test
    fun `logging a weight records it against today`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(
     store,
     FakeProfileRepository(aProfile()),
     today,
 )

        viewModel.log(80.5)
        advanceUntilIdle()

        assertThat(store.logged.single().epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(store.logged.single().kg).isEqualTo(80.5)
    }

    @Test
    fun `a weight can be logged against a day that is not today`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(
     store,
     FakeProfileRepository(aProfile()),
     today,
 )

        viewModel.log(kg = 79.2, epochDay = TEST_EPOCH_DAY - 3)
        advanceUntilIdle()

        assertThat(store.logged.single().epochDay).isEqualTo(TEST_EPOCH_DAY - 3)
    }

    @Test
    fun `logging again on a day replaces that day's reading, which is how a correction works`() =
        runTest {
            val store = FakeWeightRepository()
            val viewModel = WeightViewModel(
     store,
     FakeProfileRepository(aProfile()),
     today,
 )

            viewModel.log(kg = 80.0, epochDay = TEST_EPOCH_DAY)
            advanceUntilIdle()
            viewModel.log(kg = 79.6, epochDay = TEST_EPOCH_DAY)
            advanceUntilIdle()

            val state = viewModel.state.first { it.readings.isNotEmpty() }
            assertThat(state.readings).hasSize(1)
            assertThat(state.readings.single().kg).isEqualTo(79.6)
        }

    @Test
    fun `a reading can be removed`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(
     store,
     FakeProfileRepository(aProfile()),
     today,
 )

        viewModel.log(kg = 80.0)
        advanceUntilIdle()
        viewModel.delete(TEST_EPOCH_DAY)
        advanceUntilIdle()

        assertThat(viewModel.state.first().readings).isEmpty()
    }

    /**
     * The one mis-tap in the app that silently moved the trend, the goal projection and — since the
     * weekly recalculation — the daily calorie target, with nothing anywhere to put it back.
     *
     * Restored by re-logging, because a reading is a date and a number and nothing else: there is
     * no id to lose and no receipt to read back out of the store, unlike a row on the day.
     */
    @Test
    fun `a deleted reading can be put back exactly`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(store, FakeProfileRepository(aProfile()), today)

        viewModel.log(kg = 80.4)
        advanceUntilIdle()
        viewModel.delete(TEST_EPOCH_DAY)
        advanceUntilIdle()
        assertThat(viewModel.state.first().readings).isEmpty()

        viewModel.undoDelete()
        advanceUntilIdle()

        val back = viewModel.state.first().readings.single()
        assertThat(back.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(back.kg).isEqualTo(80.4)
    }

    /** Two deleted in a tidy-up, both recoverable, most recent first. */
    @Test
    fun `more than one deleted reading can be put back`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(store, FakeProfileRepository(aProfile()), today)

        viewModel.log(kg = 80.0, epochDay = TEST_EPOCH_DAY - 1)
        viewModel.log(kg = 81.0, epochDay = TEST_EPOCH_DAY)
        advanceUntilIdle()
        viewModel.delete(TEST_EPOCH_DAY - 1)
        viewModel.delete(TEST_EPOCH_DAY)
        advanceUntilIdle()
        assertThat(viewModel.state.first().readings).isEmpty()

        viewModel.undoDelete()
        advanceUntilIdle()
        assertThat(viewModel.state.first().readings.map { it.epochDay })
            .containsExactly(TEST_EPOCH_DAY)

        viewModel.undoDelete()
        advanceUntilIdle()
        assertThat(viewModel.state.first().readings.map { it.kg })
            .containsExactly(80.0, 81.0)
    }

    @Test
    fun `there is nothing to undo until a reading is deleted, and nothing once it is back`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(store, FakeProfileRepository(aProfile()), today)

        viewModel.log(kg = 80.0)
        advanceUntilIdle()
        assertThat(viewModel.canUndo.value).isFalse()

        viewModel.delete(TEST_EPOCH_DAY)
        advanceUntilIdle()
        assertThat(viewModel.canUndo.value).isTrue()

        viewModel.undoDelete()
        advanceUntilIdle()
        assertThat(viewModel.canUndo.value).isFalse()
    }

    /** Nothing to put back is not an error, and must not put anything back. */
    @Test
    fun `undo with nothing deleted does nothing`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(store, FakeProfileRepository(aProfile()), today)

        viewModel.undoDelete()
        advanceUntilIdle()

        assertThat(viewModel.state.first().readings).isEmpty()
    }

    @Test
    fun `an impossible weight is refused rather than stored`() = runTest {
        val store = FakeWeightRepository()
        val viewModel = WeightViewModel(
     store,
     FakeProfileRepository(aProfile()),
     today,
 )

        viewModel.log(0.0)
        advanceUntilIdle()

        assertThat(store.logged).isEmpty()
    }

    @Test
    fun `the remembered range is what the screen opens on`() = runTest {
        val viewModel = WeightViewModel(
            FakeWeightRepository(aFortnight()),
            FakeProfileRepository(aProfile(), initialChartRange = "Quarter"),
            today,
        )

        val state = viewModel.state.first { it.trend.isNotEmpty() }

        assertThat(state.range).isEqualTo(ChartRange.Quarter)
    }

    @Test
    fun `choosing a range stores it`() = runTest {
        val profiles = FakeProfileRepository(aProfile())
        val viewModel = WeightViewModel(FakeWeightRepository(aFortnight()), profiles, today)

        viewModel.setRange(ChartRange.Month)
        advanceUntilIdle()

        // The enum's NAME, not its position: reordering the ranges later must not silently change
        // which one he had chosen.
        assertThat(profiles.chartRanges).containsExactly("Month")
    }

    @Test
    fun `an unknown stored range falls back to the whole history`() = runTest {
        // Never a real value — a hand-edited store, or one written by a version that had a range
        // this one does not. The forgiving read is what makes widening the list later safe.
        val viewModel = WeightViewModel(
            FakeWeightRepository(aFortnight()),
            FakeProfileRepository(aProfile(), initialChartRange = "Fortnight"),
            today,
        )

        val state = viewModel.state.first { it.trend.isNotEmpty() }

        assertThat(state.range).isEqualTo(ChartRange.All)
    }

    @Test
    fun `a store that has never been written opens on the whole history`() = runTest {
        val viewModel = WeightViewModel(
            FakeWeightRepository(aFortnight()),
            FakeProfileRepository(aProfile(), initialChartRange = null),
            today,
        )

        val state = viewModel.state.first { it.trend.isNotEmpty() }

        assertThat(state.range).isEqualTo(ChartRange.All)
    }

    private class FakeWeightRepository(
        initial: List<WeightReading> = emptyList(),
    ) : WeightRepository {
        private val state = MutableStateFlow(initial)
        val logged = mutableListOf<WeightReading>()

        override val readings: Flow<List<WeightReading>> = state

        override suspend fun log(reading: WeightReading) {
            logged += reading
            state.value = state.value.filterNot { it.epochDay == reading.epochDay } + reading
        }

        override suspend fun delete(epochDay: Long) {
            state.value = state.value.filterNot { it.epochDay == epochDay }
        }
    }
}
