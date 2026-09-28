package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

/** The stored rows, as the Movement screen reads them. Every figure is invented. */
class MovementRecordTest {

    @Test
    fun `a stored day keeps every figure and where it came from`() {
        val stored = HealthDayEntity(
            epochDay = 20_699, computedAtMillis = 0,
            steps = 9_000, stepsSource = "CORRECTED",
            distanceM = 8_000, distanceSource = "TOTAL",
            activeKcal = 410, activeKcalSource = "TOTAL",
            restingHeartRate = 58, restingHeartRateSource = "READ",
            hrvMs = 42.0, hrvSource = "COMPUTED",
            oxygenPct = 97.0, oxygenSource = "COMPUTED",
            respiratoryRate = 14.0, respiratoryRateSource = "COMPUTED",
            sleepMinutes = 430, deepMinutes = 80, lightMinutes = 255, remMinutes = 95, sleepSource = "COMPUTED",
        )

        assertThat(stored.toHealthDay()).isEqualTo(
            HealthDay(
                epochDay = 20_699,
                steps = 9_000, stepsSource = FigureSource.CORRECTED,
                distanceM = 8_000,
                activeKcal = 410, activeKcalSource = FigureSource.TOTAL,
                restingHeartRate = 58, hrvMs = 42.0, oxygenPct = 97.0, respiratoryRate = 14.0,
                sleepMinutes = 430, deepMinutes = 80, remMinutes = 95, lightMinutes = 255,
            ),
        )
    }

    @Test
    fun `a day with nothing recorded reads as nothing, not zeros`() {
        assertThat(HealthDayEntity(epochDay = 20_699, computedAtMillis = 0).toHealthDay())
            .isEqualTo(HealthDay(epochDay = 20_699))
    }

    @Test
    fun `a stored workout keeps whether it is hidden, and its heart rate`() {
        val workout = aWorkout(hidden = true, avgHeartRate = 142).toWorkout()

        assertThat(workout.kind).isEqualTo(WorkoutKind.RUN)
        assertThat(workout.title).isEqualTo("Running")
        assertThat(workout.durationMinutes).isEqualTo(32)
        assertThat(workout.distanceM).isEqualTo(6_200)
        assertThat(workout.hidden).isTrue()
        assertThat(workout.avgHeartRate).isEqualTo(142)
    }

    /** D81: whether it counts is worked out as it is read; hidden stays its own flag. */
    @Test
    fun `a walk from an app switched off reads as not counted, its run as counted`() {
        val out = setOf("com.example.band")

        assertThat(aWorkout(kind = "WALK").toWorkout(out).counted).isFalse()
        assertThat(aWorkout(kind = "WALK").toWorkout(out).hidden).isFalse()
        assertThat(aWorkout(kind = "RUN").toWorkout(out).counted).isTrue()
        assertThat(aWorkout(kind = "WALK").toWorkout().counted).isTrue()
    }

    /**
     * D81: switching an app off while the Movement screen is open shows at once — the workouts are
     * read again with the new choice, without a change to the table. Only the one DAO read the record
     * uses is answered; any other call fails the test.
     */
    @Test
    fun `the workouts are read again when the choice changes`() = runTest {
        val choices = FakeWalkChoices()
        val rows = MutableStateFlow(listOf(aWorkout(kind = "WALK")))
        val record = RoomMovementRecord(only<HealthDayDao>(), workoutDaoOver(rows), choices, InMemorySplitDao())
        val seen = mutableListOf<Boolean>()

        val job = launch { record.observeWorkouts(20_699, 20_699).collect { seen += it.single().counted } }
        runCurrent()
        choices.setCounted("com.example.band", counted = false)
        runCurrent()
        job.cancel()

        assertThat(seen).containsExactly(true, false).inOrder()
    }

    /** D83: as far back as the record holds days — the earlier of the two tables; none with neither. */
    @Test
    fun `the earliest day is the earlier of the first summary and the first workout`() = runTest {
        val healthEarliest = MutableStateFlow<Long?>(20_689)
        val workoutEarliest = MutableStateFlow<Long?>(20_682)
        val record = RoomMovementRecord(
            only<HealthDayDao> { if (it == "observeEarliest") healthEarliest else null },
            only<WorkoutDao> { if (it == "observeEarliest") workoutEarliest else null },
            FakeWalkChoices(),
            InMemorySplitDao(),
        )

        assertThat(record.observeEarliestDay().first()).isEqualTo(20_682L)
        workoutEarliest.value = null
        assertThat(record.observeEarliestDay().first()).isEqualTo(20_689L)
        healthEarliest.value = null
        assertThat(record.observeEarliestDay().first()).isNull()
    }

    /**
     * D92: two stored copies of one session are read as one, and read again as two once split; a
     * witness whose own app recorded distance gives the session its distance. Invented figures.
     */
    @Test
    fun `overlapping workouts are read as one session, and as two once split`() = runTest {
        val rows = MutableStateFlow(listOf(aWorkout().copy(id = 1), aWorkout().copy(id = 2, originId = "session-2", durationMinutes = 30, distanceM = 7_000)))
        val splits = InMemorySplitDao()
        val dao = only<WorkoutDao> { method ->
            when (method) {
                "observeBetween" -> rows
                "idsWithOwnDistance" -> listOf(2L)
                else -> null
            }
        }
        val record = RoomMovementRecord(only<HealthDayDao>(), dao, FakeWalkChoices(), splits)
        val seen = mutableListOf<List<List<Long>>>()

        val job = launch { record.observeWorkouts(20_699, 20_699).collect { sessions -> seen += sessions.map { it.witnessIds } } }
        runCurrent()
        val combined = record.observeWorkouts(20_699, 20_699).first().single()
        splits.insertAll(listOf(SessionSplitEntity(1, 2)))
        runCurrent()
        job.cancel()

        assertThat(combined.id).isEqualTo(1L)
        assertThat(combined.distanceM).isEqualTo(7_000)
        assertThat(seen).containsExactly(listOf(listOf(1L, 2L)), listOf(listOf(1L), listOf(2L))).inOrder()
    }

    /** D92: the review screen's session is the one its workout is a witness of. */
    @Test
    fun `the session of a workout that does not lead is the combined session`() = runTest {
        val second = aWorkout().copy(id = 2, originId = "session-2", durationMinutes = 30)
        val rows = MutableStateFlow(listOf(aWorkout().copy(id = 1), second))
        val dao = only<WorkoutDao> { method ->
            when (method) {
                "observeBetween" -> rows
                "idsWithOwnDistance" -> emptyList<Long>()
                "byId" -> second
                else -> null
            }
        }
        val record = RoomMovementRecord(only<HealthDayDao>(), dao, FakeWalkChoices(), InMemorySplitDao())

        val session = record.sessionOf(2)!!

        assertThat(session.id).isEqualTo(1L)
        assertThat(session.witnessIds).containsExactly(1L, 2L).inOrder()
    }

    private fun workoutDaoOver(rows: Flow<List<WorkoutEntity>>): WorkoutDao = only { method ->
        if (method == "observeBetween") rows else null
    }

    /** An [T] that answers only what [answer] returns non-null for, and fails loudly on anything else. */
    private inline fun <reified T> only(crossinline answer: (String) -> Any? = { null }): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            answer(method.name) ?: throw UnsupportedOperationException("not in this test: ${method.name}")
        } as T

    /** D82: what a file added, and where it came from, is read and written back as stored. */
    @Test
    fun `a file's figures and their sources survive the mapping both ways`() {
        val stored = aWorkout(kind = "WALK", energySource = "FILE", source = "TYPED")
            .copy(origin = null, originId = null, energyKcal = 150, distanceSource = "FILE", steps = 4_000, stepsSource = "FILE")

        val workout = stored.toWorkout()

        assertThat(workout.energySource).isEqualTo(EnergySource.FILE)
        assertThat(workout.distanceSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(workout.steps).isEqualTo(4_000)
        assertThat(workout.stepsSource).isEqualTo(WorkoutFigureSource.FILE)
        assertThat(workout.fromFile).isTrue()
        assertThat(workout.toTypedEntity()).isEqualTo(stored)
        assertThat(aWorkout().copy(distanceSource = "SOMETHING_NEW").toWorkout().distanceSource).isNull()
    }

    @Test
    fun `a stored name this version does not know never breaks the read`() {
        val workout = aWorkout(kind = "SKATEBOARD", energySource = "GUESSED", effort = "BRUTAL", source = "BEAMED")
            .toWorkout()

        assertThat(workout.kind).isEqualTo(WorkoutKind.UNRECOGNISED)
        assertThat(workout.energySource).isEqualTo(EnergySource.NONE)
        assertThat(workout.effort).isNull()
        assertThat(workout.source).isEqualTo(WorkoutSource.SYNCED)
    }

    @Test
    fun `a typed workout is stored with no origin and reads back as it was`() {
        val typed = aTypedWorkout(id = 7, note = "a note")

        val entity = typed.toTypedEntity()

        assertThat(entity.source).isEqualTo("TYPED")
        assertThat(entity.origin).isNull()
        assertThat(entity.originId).isNull()
        assertThat(entity.effort).isEqualTo("MODERATE")
        assertThat(entity.energySource).isEqualTo("MET_ESTIMATE")
        assertThat(entity.toWorkout()).isEqualTo(typed)
    }

    private fun aWorkout(
        kind: String = "RUN",
        energySource: String = "NONE",
        effort: String? = null,
        source: String = "SYNCED",
        hidden: Boolean = false,
        avgHeartRate: Int? = null,
    ) = WorkoutEntity(
        id = 7, epochDay = 20_699, startedAtMillis = 1_000, durationMinutes = 32,
        kind = kind, title = "Running", distanceM = 6_200, energyKcal = null,
        energySource = energySource, effort = effort, source = source,
        origin = "com.example.band", originId = "session-1",
        hidden = hidden, note = null, avgHeartRate = avgHeartRate,
    )

    /** D84 sends these; D70 worked them out from the readings. */
    @Test
    fun `a stored workout keeps its highest heart rate and its zones`() {
        val stored = WorkoutEntity(
            epochDay = 20_699, startedAtMillis = 0, durationMinutes = 40, kind = "WALK", title = null,
            distanceM = 3_000, energyKcal = 200, energySource = "BAND", effort = null, source = "SYNCED",
            origin = "com.example.band", originId = "w-1", note = null,
            avgHeartRate = 110, maxHeartRate = 130, zoneSeconds = "600,1200,600,0,0", zoneMaxSource = "ESTIMATED",
        )

        val workout = stored.toWorkout()

        assertThat(workout.maxHeartRate).isEqualTo(130)
        assertThat(workout.zoneSeconds).containsExactly(600, 1200, 600, 0, 0).inOrder()
        assertThat(workout.zoneMaxSource).isEqualTo("ESTIMATED")
    }
}
