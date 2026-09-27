package com.metaself.app.ui.screen.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.InMemoryMealRepository
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.FakeTypedWorkouts
import com.metaself.app.data.health.MovementRecord
import com.metaself.app.data.health.TypedWorkouts
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutDraft
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.profile.aProfile
import com.metaself.app.ui.RecordingProblemLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * TEST_EPOCH_DAY is Thursday 3 September 2026: its week began on Monday 31 August (20,696), and the
 * four weeks before that on 3 August (20,668). Every figure is invented.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MovementViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var date: LocalDate = LocalDate.ofEpochDay(TEST_EPOCH_DAY)
    private val today = Today { date }

    /** 15:00 UTC on TEST_EPOCH_DAY; only its clock time reaches a saved workout. */
    private val now = Now { TEST_EPOCH_DAY * 86_400_000L + 15 * 3_600_000L }

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
        val model = MovementViewModel(FakeRecord(), meals, today, ProblemLog.NONE, FakeTypedWorkouts(), FakeProfileRepository(aProfile()), now)

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
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        model.toggle(20_698L)
        model.lookedAt()
        advanceUntilIdle()

        assertThat(model.state.value.openDay).isEqualTo(20_698L)

        job.cancel()
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
        val model = MovementViewModel(broken, InMemoryMealRepository(), today, problems, FakeTypedWorkouts(), FakeProfileRepository(aProfile()), now)

        assertThat(model.state.first { it.unreadable }.week).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    /** The failure ends the health-record flow, not the whole state stream: toggling still works. */
    @Test
    fun `after a failing read, toggling still changes openDay`() = runTest {
        val broken = object : MovementRecord {
            override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
                flow { throw IllegalStateException("disk full") }

            override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> = flowOf(emptyList())
        }
        val model = MovementViewModel(broken, InMemoryMealRepository(), today, RecordingProblemLog(), FakeTypedWorkouts(), FakeProfileRepository(aProfile()), now)
        val job = launch { model.state.collect {} }
        advanceUntilIdle()

        assertThat(model.state.value.unreadable).isTrue()

        model.toggle(20_698L)
        advanceUntilIdle()

        assertThat(model.state.value.openDay).isEqualTo(20_698L)

        job.cancel()
    }

    @Test
    fun `logging a workout opens an empty sheet on today, priced on the profile's weight`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.logWorkout()

        val sheet = model.state.first { it.sheet != null }.sheet!!
        assertThat(sheet.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(sheet.weightKg).isEqualTo(80.0)
        assertThat(sheet.draft).isEqualTo(WorkoutDraft())
        assertThat(sheet.editing).isNull()
    }

    /** D76: onto the day that is open; today when none is. */
    @Test
    fun `a workout is logged onto the open day, and onto today when none is open`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.toggle(20_698L)
        model.logWorkout()
        assertThat(model.state.first { it.sheet != null }.sheet!!.epochDay).isEqualTo(20_698L)

        model.closeSheet()
        model.toggle(20_698L)
        model.logWorkout()
        assertThat(model.state.first { it.sheet != null && it.openDay == null }.sheet!!.epochDay)
            .isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `with no profile the sheet opens with no weight`() = runTest {
        val model = viewModel(profiles = FakeProfileRepository(null))
        model.state.first { it.week != null }

        model.logWorkout()

        assertThat(model.state.first { it.sheet != null }.sheet!!.weightKg).isNull()
    }

    /** D8: a profile that cannot be read never crashes the sheet — it opens with no weight, logged. */
    @Test
    fun `a profile that cannot be read opens the sheet with no weight, and is logged`() = runTest {
        val problems = RecordingProblemLog()
        val profiles = FakeProfileRepository(aProfile()).apply { failing = IllegalStateException("disk full") }
        val model = viewModel(problems = problems, profiles = profiles)
        model.state.first { it.week != null }

        model.logWorkout()

        assertThat(model.state.first { it.sheet != null }.sheet!!.weightKg).isNull()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    /**
     * A save started on one sheet must not land on a later one: while the first write is still in
     * flight, the sheet is closed and a fresh one opened; when the slow write finally lands, the fresh
     * sheet is exactly as it was.
     */
    @Test
    fun `a slow write never touches a later sheet`() = runTest {
        val typed = FakeTypedWorkouts()
        val gate = CompletableDeferred<Unit>()
        typed.beforeWrite = { gate.await() }
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }
        model.changeDraft(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"))

        model.saveWorkout()
        val saving = model.state.first { it.sheet?.saving == true }
        assertThat(saving.sheet!!.saving).isTrue()

        // The sheet is closed, and a fresh one opened, while the first save is still parked at the gate.
        model.closeSheet()
        model.logWorkout()
        val fresh = model.state.first { it.sheet?.saving == false }.sheet!!

        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(model.state.value.sheet).isEqualTo(fresh)
        assertThat(typed.logged).hasSize(1)
    }

    /** 45 minutes of moderate strength on 80 kg: (3.5 − 1) × 80 × 0.75 = 150. */
    @Test
    fun `saving logs a typed workout on that day with its estimate, and closes the sheet`() = runTest {
        val typed = FakeTypedWorkouts()
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }

        model.changeDraft(WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45"))
        model.saveWorkout()

        model.state.first { it.sheet == null }
        val saved = typed.logged.single()
        assertThat(saved.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(saved.source).isEqualTo(WorkoutSource.TYPED)
        assertThat(saved.energyKcal).isEqualTo(150)
        assertThat(saved.energySource).isEqualTo(EnergySource.MET_ESTIMATE)
        assertThat(Instant.ofEpochMilli(saved.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay())
            .isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `a draft that cannot be saved is not, and the sheet stays`() = runTest {
        val typed = FakeTypedWorkouts()
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }

        model.saveWorkout()
        advanceUntilIdle()

        assertThat(typed.logged).isEmpty()
        assertThat(model.state.first { it.sheet != null }.sheet!!.draft).isEqualTo(WorkoutDraft())
    }

    /** One hour of moderate strength on 80 kg: (3.5 − 1) × 80 × 1 = 200. */
    @Test
    fun `tapping a typed workout opens it filled, and saving changes it in place`() = runTest {
        val workout = aTypedWorkout(id = 9, epochDay = 20_698, startedAtMillis = 1_000)
        val typed = FakeTypedWorkouts(listOf(workout))
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }

        model.openWorkout(workout)
        val sheet = model.state.first { it.sheet != null }.sheet!!
        assertThat(sheet.editing).isEqualTo(workout)
        assertThat(sheet.epochDay).isEqualTo(20_698L)
        assertThat(sheet.draft).isEqualTo(WorkoutDraft.from(workout))

        model.changeDraft(sheet.draft.copy(minutes = "60"))
        model.saveWorkout()

        model.state.first { it.sheet == null }
        val changed = typed.changed.single()
        assertThat(changed.id).isEqualTo(9L)
        assertThat(changed.epochDay).isEqualTo(20_698L)
        assertThat(changed.startedAtMillis).isEqualTo(1_000L)
        assertThat(changed.durationMinutes).isEqualTo(60)
        assertThat(changed.energyKcal).isEqualTo(200)
    }

    /** D76: a synced workout is not editable here. */
    @Test
    fun `a synced workout does not open the sheet`() = runTest {
        val model = viewModel()
        model.state.first { it.week != null }

        model.openWorkout(aTypedWorkout().copy(source = WorkoutSource.SYNCED))
        advanceUntilIdle()

        assertThat(model.state.first { it.week != null }.sheet).isNull()
    }

    /** The app's rule for a thing it can rebuild: delete at once, then offer Undo (the weight screen's). */
    @Test
    fun `deleting a typed workout deletes it, closes the sheet, and offers Undo`() = runTest {
        val workout = aTypedWorkout(id = 9)
        val typed = FakeTypedWorkouts(listOf(workout))
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        assertThat(model.state.value.canUndo).isFalse()
        model.openWorkout(workout)
        model.state.first { it.sheet != null }

        model.deleteWorkout()

        val state = model.state.first { it.sheet == null && it.canUndo }
        assertThat(state.canUndo).isTrue()
        assertThat(typed.deleted).containsExactly(workout)
    }

    /**
     * Undo puts back the workout as it was — its day, start, kind, figures, energy's source, effort,
     * note and hidden flag — through the store's log, which gives it a new id and works its day out
     * again. Every figure is invented.
     */
    @Test
    fun `Undo puts the deleted workout back as it was, and is then spent`() = runTest {
        val workout = aTypedWorkout(
            id = 9, epochDay = 20_698, kind = WorkoutKind.RUN, minutes = 30, distanceM = 5_000,
            energyKcal = 300, energySource = EnergySource.TYPED, note = "a note", hidden = true,
            startedAtMillis = 1_000,
        )
        val typed = FakeTypedWorkouts(listOf(workout))
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.openWorkout(workout)
        model.state.first { it.sheet != null }
        model.deleteWorkout()
        model.state.first { it.canUndo }

        model.undoDelete()

        model.state.first { !it.canUndo }
        advanceUntilIdle()
        assertThat(typed.logged).containsExactly(workout)
        assertThat(typed.workouts.value.single().copy(id = 9)).isEqualTo(workout)

        model.undoDelete()
        advanceUntilIdle()
        assertThat(typed.logged).hasSize(1)
    }

    @Test
    fun `two deleted workouts come back most recent first, one Undo at a time`() = runTest {
        val first = aTypedWorkout(id = 1, minutes = 20)
        val second = aTypedWorkout(id = 2, minutes = 40)
        val typed = FakeTypedWorkouts(listOf(first, second))
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        listOf(first, second).forEach { workout ->
            model.openWorkout(workout)
            model.state.first { it.sheet != null }
            model.deleteWorkout()
            model.state.first { it.sheet == null }
        }

        model.undoDelete()
        advanceUntilIdle()
        assertThat(typed.logged).containsExactly(second)
        assertThat(model.state.first { it.week != null }.canUndo).isTrue()

        model.undoDelete()
        advanceUntilIdle()
        assertThat(typed.logged).containsExactly(second, first).inOrder()
        assertThat(model.state.first { !it.canUndo }.canUndo).isFalse()
    }

    /** A delete that failed leaves the workout there, so there is nothing to undo. */
    @Test
    fun `a delete that fails offers no Undo, and says so on the sheet`() = runTest {
        val typed = FakeTypedWorkouts().apply { failing = IllegalStateException("disk full") }
        val model = viewModel(typed = typed)
        model.state.first { it.week != null }
        model.openWorkout(aTypedWorkout(id = 9))
        model.state.first { it.sheet != null }

        model.deleteWorkout()

        val state = model.state.first { it.sheet?.failed == true }
        assertThat(state.canUndo).isFalse()
        assertThat(state.sheet!!.failure).isEqualTo(WriteFailure.DELETE)
    }

    /**
     * The store found nothing to delete — the row was gone, or was not a typed one. Not silent: the
     * sheet says so, the log says why, and there is nothing to undo.
     */
    @Test
    fun `a delete that finds nothing to delete is said and logged, and offers no Undo`() = runTest {
        val typed = FakeTypedWorkouts()
        val problems = RecordingProblemLog()
        val model = viewModel(typed = typed, problems = problems)
        model.state.first { it.week != null }
        model.openWorkout(aTypedWorkout(id = 9))
        model.state.first { it.sheet != null }

        model.deleteWorkout()

        val state = model.state.first { it.sheet?.failed == true }
        assertThat(state.sheet!!.failure).isEqualTo(WriteFailure.DELETE)
        assertThat(state.canUndo).isFalse()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    /** As a delete that finds nothing: a change the store could not make is said, not swallowed. */
    @Test
    fun `a change that finds nothing to change is said and logged, and the sheet stays open`() = runTest {
        val typed = FakeTypedWorkouts()
        val problems = RecordingProblemLog()
        val model = viewModel(typed = typed, problems = problems)
        model.state.first { it.week != null }
        model.openWorkout(aTypedWorkout(id = 9))
        val opened = model.state.first { it.sheet != null }.sheet!!

        model.changeDraft(opened.draft.copy(minutes = "60"))
        model.saveWorkout()

        val sheet = model.state.first { it.sheet?.failed == true }.sheet!!
        assertThat(sheet.failure).isEqualTo(WriteFailure.SAVE)
        assertThat(sheet.draft.minutes).isEqualTo("60")
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    /** D8: logged and said; the receipt goes back, so Undo is still there to try again. */
    @Test
    fun `an Undo that fails is said and logged, and Undo stays offered`() = runTest {
        val workout = aTypedWorkout(id = 9)
        val typed = FakeTypedWorkouts(listOf(workout))
        val problems = RecordingProblemLog()
        val model = viewModel(typed = typed, problems = problems)
        model.state.first { it.week != null }
        model.openWorkout(workout)
        model.state.first { it.sheet != null }
        model.deleteWorkout()
        model.state.first { it.canUndo }

        typed.failing = IllegalStateException("disk full")
        model.undoDelete()

        val state = model.state.first { it.undoFailed }
        assertThat(state.canUndo).isTrue()
        assertThat(problems.recorded.single().kind).isEqualTo("movement")

        typed.failing = null
        model.undoDelete()
        val after = model.state.first { !it.canUndo }
        assertThat(after.undoFailed).isFalse()
        assertThat(typed.logged).containsExactly(workout)
    }

    /** D8: said on the sheet, logged, never thrown; what was typed is kept. */
    @Test
    fun `a save that fails is said on the sheet and logged, and the sheet stays open`() = runTest {
        val typed = FakeTypedWorkouts().apply { failing = IllegalStateException("disk full") }
        val problems = RecordingProblemLog()
        val model = viewModel(typed = typed, problems = problems)
        model.state.first { it.week != null }
        model.logWorkout()
        model.state.first { it.sheet != null }
        val draft = WorkoutDraft(kind = WorkoutKind.STRENGTH, minutes = "45")

        model.changeDraft(draft)
        model.saveWorkout()

        val sheet = model.state.first { it.sheet?.failed == true }.sheet!!
        assertThat(sheet.failure).isEqualTo(WriteFailure.SAVE)
        assertThat(sheet.saving).isFalse()
        assertThat(sheet.draft).isEqualTo(draft)
        assertThat(problems.recorded.single().kind).isEqualTo("movement")
    }

    private fun viewModel(
        record: MovementRecord = FakeRecord(),
        typed: TypedWorkouts = FakeTypedWorkouts(),
        profiles: ProfileRepository = FakeProfileRepository(aProfile()),
        problems: ProblemLog = ProblemLog.NONE,
    ) = MovementViewModel(record, InMemoryMealRepository(), today, problems, typed, profiles, now)

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
