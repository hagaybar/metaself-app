package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** The Trainer screen's state (D85). TEST_EPOCH_DAY is Thursday 3 September 2026; every session is invented. */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val record = FakeMovementRecord()
    private val store = FakeTrainerStore()
    private val problems = RecordingProblemLog()
    private val aboutMe = InMemoryAboutMeStore()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /** A review saved on another screen moves the session from waiting to earlier without reopening. */
    @Test
    fun `the state follows the store`() = runTest {
        val walk = walk(1, TEST_EPOCH_DAY)
        record.workouts.value = listOf(walk)
        store.workouts.value = listOf(walk)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        assertThat(viewModel.state.value.home!!.waiting).isEqualTo(walk)
        assertThat(viewModel.state.value.today).isEqualTo(TEST_EPOCH_DAY)

        store.putReview(TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = "Invented."))
        advanceUntilIdle()

        assertThat(viewModel.state.value.home!!.waiting).isNull()
        assertThat(viewModel.state.value.home!!.earlier.single().workout).isEqualTo(walk)
    }

    /** D90: the card shows the note as stored, and follows a save made on its own page. */
    @Test
    fun `the note is followed`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        assertThat(viewModel.state.value.aboutMe).isEmpty()

        aboutMe.save("Invented note.")
        advanceUntilIdle()

        assertThat(viewModel.state.value.aboutMe).isEqualTo("Invented note.")
    }

    @Test
    fun `the recent sessions are read for the last three days`() = runTest {
        val asked = mutableListOf<Pair<Long, Long>>()
        val watching = object : MovementRecord by record {
            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> {
                asked += from to to
                return record.observeWorkouts(from, to)
            }
        }

        viewModel(watching).state.first { it.home != null }

        assertThat(asked).containsExactly((TEST_EPOCH_DAY - 2) to TEST_EPOCH_DAY)
    }

    @Test
    fun `a failing read says so and is logged once`() = runTest {
        val failing = object : MovementRecord by record {
            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> = flow { throw IllegalStateException("disk") }
            override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> = record.observeDays(from, to)
        }

        val state = viewModel(failing).state.first { it.unreadable }

        assertThat(state.home).isNull()
        assertThat(problems.recorded.map { it.kind }).containsExactly("trainer")
    }

    /** Past midnight, the last three days move with the calendar: yesterday's session waits no more. */
    @Test
    fun `back on the screen on a new day, today is the new day`() = runTest {
        val walk = walk(1, TEST_EPOCH_DAY - 2)
        record.workouts.value = listOf(walk)
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        assertThat(viewModel.state.value.home!!.waiting).isEqualTo(walk)

        date = LocalDate.ofEpochDay(TEST_EPOCH_DAY + 1)
        viewModel.lookedAt()
        advanceUntilIdle()

        assertThat(viewModel.state.value.today).isEqualTo(TEST_EPOCH_DAY + 1)
        assertThat(viewModel.state.value.home!!.waiting).isNull()
    }

    private var date: LocalDate = LocalDate.ofEpochDay(TEST_EPOCH_DAY)

    private fun viewModel(movement: MovementRecord = record) = TrainerViewModel(
        movement, store, aboutMe, Today { date }, Now { NOW }, problems,
    )

    /** A synced forty-minute walk at 07:00 on [day]. Invented. */
    private fun walk(id: Long, day: Long) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 7 * HOUR, durationMinutes = 40,
        kind = WorkoutKind.WALK, title = null, distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED, hidden = false, note = null,
    )

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOW = TEST_EPOCH_DAY * DAY + 15 * HOUR
    }
}
