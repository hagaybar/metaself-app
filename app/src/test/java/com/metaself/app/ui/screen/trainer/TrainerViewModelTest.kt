package com.metaself.app.ui.screen.trainer

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.CurrentYear
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.data.trainer.AskTheTrainer
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainer
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.profile.TEST_YEAR
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanCard
import com.metaself.app.domain.trainer.PlanConfirmation
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Programme
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.ui.ActionRefused
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
    private val programmes = FakeProgrammeStore()
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

    /** D95: a plan kept elsewhere shows at once, ticked from the record. Invented. */
    @Test
    fun `a running plan shows with its ticks`() = runTest {
        record.workouts.value = listOf(walk(1, TEST_EPOCH_DAY))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val id = programmes.add(RUNNING_PLAN)
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        advanceUntilIdle()

        val card = viewModel.state.value.home!!.plan as PlanCard.Running
        assertThat(card.progress.weeks.first().done).isEqualTo(1)
    }

    /** D105: a session that started before the plan was kept ticks nothing. Invented. */
    @Test
    fun `a session before the plan was kept does not tick it`() = runTest {
        record.workouts.value = listOf(walk(1, TEST_EPOCH_DAY))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val id = programmes.add(RUNNING_PLAN.copy(createdAtMillis = NOW))
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        advanceUntilIdle()

        val card = viewModel.state.value.home!!.plan as PlanCard.Running
        assertThat(card.progress.weeks.first().done).isEqualTo(0)
    }

    /** D105: a twenty-minute walk against a thirty-minute place is asked about; Yes ticks it at once. Invented. */
    @Test
    fun `a shorter session is asked about, and a yes ticks it at once`() = runTest {
        record.workouts.value = listOf(walk(1, TEST_EPOCH_DAY).copy(durationMinutes = 20))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val id = programmes.add(RUNNING_PLAN)
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        advanceUntilIdle()
        val before = (viewModel.state.value.home!!.plan as PlanCard.Running).progress.weeks.first()
        assertThat(before.ticks.first().candidate?.id).isEqualTo(1L)

        viewModel.answer(1, confirmed = true)
        viewModel.answer(1, confirmed = false)
        advanceUntilIdle()

        val after = (viewModel.state.value.home!!.plan as PlanCard.Running).progress.weeks.first()
        assertThat(after.done).isEqualTo(1)
        assertThat(after.ticks.first().short).isTrue()
        assertThat(programmes.confirmationRows.value).containsExactly(PlanConfirmation(id, 1, true, NOW))
        assertThat(viewModel.state.value.answering).isEmpty()
        assertThat(viewModel.state.value.refused).isNull()
    }

    @Test
    fun `an answer that cannot be stored is said and logged, and nothing changes`() = runTest {
        record.workouts.value = listOf(walk(1, TEST_EPOCH_DAY).copy(durationMinutes = 20))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val id = programmes.add(RUNNING_PLAN)
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        advanceUntilIdle()
        programmes.failing = true

        viewModel.answer(1, confirmed = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isEqualTo(ActionRefused.NOTHING_CHANGED)
        assertThat(viewModel.state.value.answering).isEmpty()
        assertThat(problems.recorded.map { it.kind }).contains("refused")
        assertThat(programmes.confirmationRows.value).isEmpty()

        // The sentence belongs to the card it was refused against: a session synced since changes the card.
        record.workouts.value = record.workouts.value + walk(2, TEST_EPOCH_DAY - 1)
        advanceUntilIdle()
        assertThat(viewModel.state.value.refused).isNull()
    }

    /** A tap after the answer was written, before or after the card caught up, does nothing and says nothing. */
    @Test
    fun `a second tap after the answer was written is not refused`() = runTest {
        record.workouts.value = listOf(walk(1, TEST_EPOCH_DAY).copy(durationMinutes = 20))
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.state.collect {} }
        val id = programmes.add(RUNNING_PLAN)
        programmes.keep(id, TEST_EPOCH_DAY - 3, TEST_EPOCH_DAY)
        advanceUntilIdle()

        viewModel.answer(1, confirmed = false)
        advanceUntilIdle()
        viewModel.answer(1, confirmed = true)
        advanceUntilIdle()

        assertThat(viewModel.state.value.refused).isNull()
        assertThat(problems.recorded).isEmpty()
        assertThat(programmes.confirmationRows.value).containsExactly(PlanConfirmation(id, 1, false, NOW))
    }

    private var date: LocalDate = LocalDate.ofEpochDay(TEST_EPOCH_DAY)

    private fun viewModel(movement: MovementRecord = record) = TrainerViewModel(
        movement, store, programmes, aboutMe, Today { date }, Now { NOW }, problems,
        AskTheTrainer(
            movement, store, InMemoryWeightRepository(), FakeProfileRepository(aProfile()), FakeTrainer(), aboutMe, programmes,
            Today { date }, Now { NOW }, CurrentYear { TEST_YEAR },
        ),
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
        val EASY_30 = PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Invented line")
        /** Kept at midnight on the Monday its first week starts, before any session here. */
        val RUNNING_PLAN = Programme(0, (TEST_EPOCH_DAY - 3) * DAY, ProgrammeAsk(2, 2), null, WeeksPlan("Invented", List(2) { PlanWeek("w", listOf(EASY_30, EASY_30)) }, "Invented."), "a-model")
    }
}
