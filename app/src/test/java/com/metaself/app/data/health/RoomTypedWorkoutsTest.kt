package com.metaself.app.data.health

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.time.Now
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.movement.aTypedWorkout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * The typed-workout store over a fake DAO and store, so it runs on this machine; the real DAO and
 * the real summarise are covered in CI (`HealthRecordStoreTest`, which also drives this store over
 * them). Every figure is invented.
 */
class RoomTypedWorkoutsTest {

    private val dao = FakeWorkoutDao()
    private val transaction = TrackedTransaction()
    private val store = SummarisingStore(transaction)
    private val typed = RoomTypedWorkouts(dao, transaction, store, Now { STAMP })

    @Test
    fun `a logged workout is stored as typed, with no origin, and its day is summarised again`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0))

        val row = dao.rows.single()
        assertThat(row.id).isEqualTo(id)
        assertThat(row.source).isEqualTo("TYPED")
        assertThat(row.origin).isNull()
        assertThat(row.originId).isNull()
        assertThat(row.kind).isEqualTo("STRENGTH")
        assertThat(row.effort).isEqualTo("MODERATE")
        assertThat(row.energyKcal).isEqualTo(150)
        assertThat(row.energySource).isEqualTo("MET_ESTIMATE")
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
        assertThat(store.totalsGiven.single()).isEqualTo(TotalsResult.ALL_FAILED)
        assertThat(store.nowGiven.single()).isEqualTo(STAMP)
    }

    /** Plan design question 15: the summary moves with the write, never half of it. */
    @Test
    fun `the day is summarised inside the same transaction as the write`() = runTest {
        typed.log(aTypedWorkout(id = 0))

        assertThat(store.insideTransaction).containsExactly(true)
    }

    @Test
    fun `only typed workouts are observed`() = runTest {
        dao.rows += syncedRow(id = 50)
        typed.log(aTypedWorkout(id = 0))

        val seen = typed.observe(TEST_EPOCH_DAY, TEST_EPOCH_DAY).first()

        assertThat(seen.map { it.source }).containsExactly(WorkoutSource.TYPED)
    }

    @Test
    fun `a change replaces the typed row in place, keeping its day, start and hidden flag`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0, startedAtMillis = 1_000))
        dao.rows[0] = dao.rows[0].copy(hidden = true)
        store.summarised.clear()

        val changed = typed.change(aTypedWorkout(id = id, minutes = 60, energyKcal = 200, startedAtMillis = 9_999))

        assertThat(changed).isTrue()
        val row = dao.rows.single()
        assertThat(row.durationMinutes).isEqualTo(60)
        assertThat(row.energyKcal).isEqualTo(200)
        assertThat(row.startedAtMillis).isEqualTo(1_000)
        assertThat(row.epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(row.hidden).isTrue()
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
    }

    /**
     * D82: a workout a file added, opened and saved in the sheet, keeps each figure the owner did not
     * change with the file as its source; a distance he changed becomes his own. Invented figures.
     */
    @Test
    fun `a change keeps the file's steps, and each file figure left as it was`() = runTest {
        val fromFile = aTypedWorkout(id = 0, kind = WorkoutKind.WALK, distanceM = 3_000, energyKcal = 150, energySource = EnergySource.FILE)
            .copy(effort = null, distanceSource = WorkoutFigureSource.FILE, steps = 4_000, stepsSource = WorkoutFigureSource.FILE)
        val id = typed.log(fromFile)
        // What the sheet saves: the same figures, its energy as the owner's own, no steps, no sources.
        val saved = aTypedWorkout(id = id, kind = WorkoutKind.WALK, distanceM = 3_000, energyKcal = 150, energySource = EnergySource.TYPED)

        typed.change(saved)

        val kept = dao.rows.single()
        assertThat(kept.distanceSource).isEqualTo("FILE")
        assertThat(kept.energySource).isEqualTo("FILE")
        assertThat(kept.steps).isEqualTo(4_000)
        assertThat(kept.stepsSource).isEqualTo("FILE")

        typed.change(saved.copy(distanceM = 3_500, energyKcal = 160))

        val changed = dao.rows.single()
        assertThat(changed.distanceM).isEqualTo(3_500)
        assertThat(changed.distanceSource).isEqualTo("TYPED")
        assertThat(changed.energySource).isEqualTo("TYPED")
        assertThat(changed.steps).isEqualTo(4_000)
    }

    /** D76: a synced session is not editable here. */
    @Test
    fun `a change never touches a synced session`() = runTest {
        dao.rows += syncedRow(id = 50)

        assertThat(typed.change(aTypedWorkout(id = 50, minutes = 60))).isFalse()

        assertThat(dao.rows.single()).isEqualTo(syncedRow(id = 50))
        assertThat(store.summarised).isEmpty()
    }

    /**
     * The row is found by its id, not searched for on the day the caller's copy carries: a copy with
     * another day still changes its row, which keeps its own day, and that stored day is summarised.
     */
    @Test
    fun `a change finds its row by id, whatever day the copy carries, and summarises the stored day`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0))
        store.summarised.clear()

        val changed = typed.change(aTypedWorkout(id = id, epochDay = TEST_EPOCH_DAY - 2, minutes = 60))

        assertThat(changed).isTrue()
        assertThat(dao.rows.single().durationMinutes).isEqualTo(60)
        assertThat(dao.rows.single().epochDay).isEqualTo(TEST_EPOCH_DAY)
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
    }

    @Test
    fun `a change of an id with no row does nothing and says so`() = runTest {
        assertThat(typed.change(aTypedWorkout(id = 77))).isFalse()

        assertThat(dao.rows).isEmpty()
        assertThat(store.summarised).isEmpty()
    }

    @Test
    fun `a delete removes a typed workout and summarises its day again`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0))
        store.summarised.clear()

        assertThat(typed.delete(aTypedWorkout(id = id))).isTrue()

        assertThat(dao.rows).isEmpty()
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
    }

    /** The stored row's day is summarised again, not the day the caller's copy carries. */
    @Test
    fun `a delete finds its row by id and summarises the stored day`() = runTest {
        val id = typed.log(aTypedWorkout(id = 0))
        store.summarised.clear()

        assertThat(typed.delete(aTypedWorkout(id = id, epochDay = TEST_EPOCH_DAY - 2))).isTrue()

        assertThat(dao.rows).isEmpty()
        assertThat(store.summarised).containsExactly(setOf(TEST_EPOCH_DAY))
    }

    @Test
    fun `a delete never removes a synced session, and says nothing was done`() = runTest {
        dao.rows += syncedRow(id = 50)

        assertThat(typed.delete(aTypedWorkout(id = 50).copy(source = WorkoutSource.SYNCED))).isFalse()
        assertThat(typed.delete(aTypedWorkout(id = 50))).isFalse()

        assertThat(dao.rows.single()).isEqualTo(syncedRow(id = 50))
        assertThat(store.summarised).isEmpty()
    }

    @Test
    fun `a delete of an id with no row does nothing and says so`() = runTest {
        assertThat(typed.delete(aTypedWorkout(id = 77))).isFalse()

        assertThat(store.summarised).isEmpty()
    }

    private fun syncedRow(id: Long) = WorkoutEntity(
        id = id, epochDay = TEST_EPOCH_DAY, startedAtMillis = 0, durationMinutes = 32, kind = "RUN",
        title = "Running", distanceM = 6_200, energyKcal = null, energySource = "NONE", effort = null,
        source = "SYNCED", origin = "com.example.band", originId = "session-1", note = null,
    )

    private class TrackedTransaction : DatabaseTransaction {
        var open = false
        override suspend fun run(block: suspend () -> Unit) {
            open = true
            try {
                block()
            } finally {
                open = false
            }
        }
    }

    private class SummarisingStore(private val transaction: TrackedTransaction) : HealthStore {
        val summarised = mutableListOf<Set<Long>>()
        val totalsGiven = mutableListOf<TotalsResult>()
        val nowGiven = mutableListOf<Long>()
        val insideTransaction = mutableListOf<Boolean>()

        override suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long) {
            summarised += days
            totalsGiven += totals
            nowGiven += nowMillis
            insideTransaction += transaction.open
        }

        override suspend fun bookmark(kind: HealthKind): HealthSyncEntity? = error("not used")
        override suspend fun saveBookmark(bookmark: HealthSyncEntity): Unit = error("not used")
        override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> = error("not used")
        override suspend fun replaceWindow(
            kind: HealthKind,
            fromMillis: Long,
            toMillis: Long,
            records: List<ReadRecord>,
        ): Set<Long> = error("not used")
        override suspend fun historyActedOn(): Boolean = error("not used")
        override suspend fun markHistoryActedOn(): Unit = error("not used")
        override suspend fun sessionGaps(days: Set<Long>): List<SessionGap> = error("not used")
        override suspend fun fillSessionTotals(id: Long, totals: SessionTotals): Unit = error("not used")
    }

    /** Only what the typed store calls behaves; the rest says it was not expected. */
    private class FakeWorkoutDao : WorkoutDao {
        val rows = mutableListOf<WorkoutEntity>()
        private val changes = MutableStateFlow(0)
        private var nextId = 1L

        override fun observeBetween(from: Long, to: Long): Flow<List<WorkoutEntity>> =
            changes.map { rows.filter { it.epochDay in from..to }.sortedBy { it.startedAtMillis } }

        override suspend fun insert(workout: WorkoutEntity): Long {
            val id = nextId++
            rows += workout.copy(id = id)
            changes.value++
            return id
        }

        override suspend fun update(workout: WorkoutEntity) {
            val at = rows.indexOfFirst { it.id == workout.id }
            if (at >= 0) rows[at] = workout
            changes.value++
        }

        /** As the SQL does: only a TYPED row. */
        override suspend fun deleteTyped(id: Long) {
            rows.removeAll { it.id == id && it.source == "TYPED" }
            changes.value++
        }

        override suspend fun byId(id: Long): WorkoutEntity? = rows.firstOrNull { it.id == id }

        override suspend fun onDay(epochDay: Long): List<WorkoutEntity> = error("not used")

        override fun observeWeeklyRunning(weeks: Int): Flow<List<WeekOfRunning>> = error("not used")
        override fun observeEarliest(): Flow<Long?> = error("not used")
        override suspend fun synced(origin: String, originId: String): WorkoutEntity? = error("not used")
        override suspend fun syncedIdsBetween(from: Long, to: Long): List<String> = error("not used")
        override suspend fun dropSyncedNotIn(from: Long, to: Long, keep: List<String>): Unit = error("not used")
        override suspend fun setHidden(id: Long, hidden: Boolean): Unit = error("not used")
        override suspend fun all(): List<WorkoutEntity> = error("not used")
        override suspend fun insertAll(workouts: List<WorkoutEntity>): Unit = error("not used")
        override suspend fun deleteAll(): Unit = error("not used")
        override suspend fun daysOfSynced(originId: String): List<Long> = error("not used")
        override suspend fun deleteSynced(originId: String): Unit = error("not used")
        override suspend fun visibleSyncedBetween(from: Long, to: Long): List<WorkoutEntity> = error("not used")
        override suspend fun deleteSyncedRow(id: Long): Unit = error("not used")
        override suspend fun syncedWalkDays(origin: String): List<Long> = error("not used")
        override suspend fun idsWithOwnDistance(from: Long, to: Long): List<Long> = error("not used")
        override suspend fun missingTotalsOn(days: List<Long>): List<WorkoutEntity> = error("not used")
    }

    private companion object {
        const val STAMP = 1_000_000L
    }
}
