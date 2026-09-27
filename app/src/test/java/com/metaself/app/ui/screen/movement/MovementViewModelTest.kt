package com.metaself.app.ui.screen.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * TEST_EPOCH_DAY is Thursday 3 September 2026: its week began on Monday 31 August (20,696), and the
 * four weeks before that on 3 August (20,668). Every figure is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var date: LocalDate = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
    private val today = Today { date }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `today is first and starts open`() = runTest {
        val state = viewModel().state.first { it.week != null }

        assertThat(state.openDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(state.week!!.days.first().epochDay).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `it reads this week, and the four before it for their distances`() = runTest {
        val record = FakeRecord()

        viewModel(record).state.first { it.week != null }

        assertThat(record.daysAsked).containsExactly(20_668L to TEST_EPOCH_DAY)
        assertThat(record.workoutsAsked).containsExactly(20_696L to TEST_EPOCH_DAY)
    }

    /** D73: one day open at a time; tapping an open day closes it. */
    @Test
    fun `tapping another day opens it and closes today, and tapping it again closes it`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.toggle(20_698L)
        assertThat(model.state.first { it.openDay != TEST_EPOCH_DAY }.openDay).isEqualTo(20_698L)

        model.toggle(20_698L)
        assertThat(model.state.first { it.openDay != 20_698L }.openDay).isNull()
    }

    @Test
    fun `a copy that lands while the screen is open shows at once`() = runTest {
        val record = FakeRecord()
        val model = viewModel(record)
        model.state.first { it.week != null }

        record.days.value = listOf(HealthDay(epochDay = TEST_EPOCH_DAY, distanceM = 5_000))

        assertThat(model.state.first { it.week?.distanceM != null }.week!!.distanceM).isEqualTo(5_000)
    }

    @Test
    fun `what was eaten comes from the meals logged that day, and follows a new one`() = runTest {
        val meals = InMemoryMealRepository(
            listOf(aMeal(epochDay = TEST_EPOCH_DAY, items = listOf(anItem(kcal = 600), anItem(kcal = 400)))),
        )
        val model = MovementViewModel(FakeRecord(), meals, today, ProblemLog.NONE)

        assertThat(model.state.first { it.week != null }.week!!.days.first().eatenKcal).isEqualTo(1_000)

        meals.log(aMeal(epochDay = TEST_EPOCH_DAY, items = listOf(anItem(kcal = 500))))

        assertThat(model.state.first { it.week?.days?.first()?.eatenKcal == 1_500 }).isNotNull()
    }

    @Test
    fun `back on the screen after midnight, the new day is today and open`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }
        model.toggle(20_698L)

        date = LocalDate.ofEpochDay(TEST_EPOCH_DAY + 1)
        model.lookedAt()

        val state = model.state.first { it.week?.days?.first()?.epochDay == TEST_EPOCH_DAY + 1 }
        assertThat(state.openDay).isEqualTo(TEST_EPOCH_DAY + 1)
    }

    @Test
    fun `back on the screen the same day, the open day is left alone`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }
        model.toggle(20_698L)

        model.lookedAt()

        assertThat(model.state.first { it.openDay != TEST_EPOCH_DAY }.openDay).isEqualTo(20_698L)
    }

    /** D8: a read that fails is said on the screen and logged, never thrown. */
    @Test
    fun `a record that cannot be read is said and logged`() = runTest {
        val problems = RecordingProblemLog()
        val broken = object : MovementRecord {
            override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
                flow { throw IllegalStateException("disk full") }

            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> = flowOf(emptyList())
        }
        val model = MovementViewModel(broken, InMemoryMealRepository(), today, problems)

        assertThat(model.state.first { it.unreadable }.week).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    private fun viewModel(record: MovementRecord = FakeRecord()) =
        MovementViewModel(record, InMemoryMealRepository(), today, ProblemLog.NONE)

    private class FakeRecord : MovementRecord {
        val days = MutableStateFlow<List<HealthDay>>(emptyList())
        val workouts = MutableStateFlow<List<Workout>>(emptyList())
        val daysAsked = mutableListOf<Pair<Long, Long>>()
        val workoutsAsked = mutableListOf<Pair<Long, Long>>()

        override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> {
            daysAsked += from to to
            return days.map { all -> all.filter { it.epochDay in from..to } }
        }

        override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> {
            workoutsAsked += from to to
            return workouts.map { all -> all.filter { it.epochDay in from..to } }
        }
    }
}
