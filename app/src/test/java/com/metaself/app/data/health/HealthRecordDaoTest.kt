package com.metaself.app.data.health

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every DAO of the health record, in one class so that one class skips locally.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Skipped on aarch64, run in CI. Days sit
 * around [TEST_EPOCH_DAY] (2026-09-03, a Thursday). Every figure is invented and round.
 */
@RunWith(RobolectricTestRunner::class)
class HealthRecordDaoTest {

    private lateinit var db: MetaSelfDatabase

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    // --- Workouts ----------------------------------------------------------------------------------

    @Test
    fun `a day's workouts come back in the order they started`() = runTest {
        val dao = db.workoutDao()
        dao.insert(typed(startedAt = 2_000))
        dao.insert(typed(startedAt = 1_000))
        dao.insert(typed(day = TEST_EPOCH_DAY + 1, startedAt = 500))

        val day = dao.observeBetween(TEST_EPOCH_DAY, TEST_EPOCH_DAY).first()

        assertThat(day.map { it.startedAtMillis }).containsExactly(1_000L, 2_000L).inOrder()
    }

    @Test
    fun `a synced session is found again by where it came from`() = runTest {
        val dao = db.workoutDao()
        val id = dao.insert(synced("abc-1"))

        assertThat(dao.synced(ORIGIN, "abc-1")?.id).isEqualTo(id)
        assertThat(dao.synced(ORIGIN, "abc-2")).isNull()
    }

    @Test
    fun `the same session cannot be stored twice`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("abc-1"))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(synced("abc-1")) }
        }
    }

    /** SQLite lets any number of rows share a null in a unique index. */
    @Test
    fun `any number of typed workouts can be stored, having no origin`() = runTest {
        db.workoutDao().insert(typed())
        db.workoutDao().insert(typed())

        assertThat(db.workoutDao().all()).hasSize(2)
    }

    @Test
    fun `an update keeps the row's id and replaces its figures`() = runTest {
        val dao = db.workoutDao()
        val id = dao.insert(synced("abc-1", distanceM = 5_000))

        dao.update(dao.synced(ORIGIN, "abc-1")!!.copy(distanceM = 6_000, avgHeartRate = 140))

        val row = dao.all().single()
        assertThat(row.id).isEqualTo(id)
        assertThat(row.distanceM).isEqualTo(6_000)
        assertThat(row.avgHeartRate).isEqualTo(140)
    }

    @Test
    fun `the synced ids in a window are listed, and nothing else`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("abc-1"))
        dao.insert(synced("abc-2", day = TEST_EPOCH_DAY - 40))
        dao.insert(typed())

        assertThat(dao.syncedIdsBetween(TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY)).containsExactly("abc-1")
    }

    /** A session deleted in the band's app disappears here; one hidden on purpose stays hidden. */
    @Test
    fun `sessions no longer read are dropped, but hidden and typed ones are kept`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("kept"))
        dao.insert(synced("gone"))
        dao.insert(synced("hidden").copy(hidden = true))
        dao.insert(typed())

        dao.dropSyncedNotIn(TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY, keep = listOf("kept"))

        assertThat(dao.all().map { it.originId }).containsExactly("kept", "hidden", null)
    }

    @Test
    fun `when nothing is read any more, every visible synced session in the window goes, and no typed one`() = runTest {
        val dao = db.workoutDao()
        dao.insert(synced("gone"))
        dao.insert(synced("older", day = TEST_EPOCH_DAY - 40))
        // With an empty list, `originId NOT IN ()` is true even for a typed row's null: only the
        // query's `source = 'SYNCED'` stands between this call and every typed workout in the window.
        dao.insert(typed())

        dao.dropSyncedNotIn(TEST_EPOCH_DAY - 29, TEST_EPOCH_DAY, keep = emptyList())

        assertThat(dao.all().map { it.originId }).containsExactly("older", null)
    }

    @Test
    fun `hiding a session keeps the row, and only a typed workout can be deleted`() = runTest {
        val dao = db.workoutDao()
        val band = dao.insert(synced("abc-1"))
        val mine = dao.insert(typed())

        dao.setHidden(band, true)
        dao.deleteTyped(band)
        dao.deleteTyped(mine)

        assertThat(dao.all().single().hidden).isTrue()
    }

    /**
     * Sunday 2026-08-30 closes one week; Monday 31 August and Tuesday 1 September open the next,
     * across the month boundary.
     */
    @Test
    fun `running distance is grouped into weeks that start on Monday`() = runTest {
        val dao = db.workoutDao()
        dao.insert(run(day = TEST_EPOCH_DAY - 4, metres = 4_000)) // Sunday 30 August
        dao.insert(run(day = TEST_EPOCH_DAY - 3, metres = 5_000)) // Monday 31 August
        dao.insert(run(day = TEST_EPOCH_DAY - 2, metres = 6_000)) // Tuesday 1 September

        val weeks = dao.observeWeeklyRunning(weeks = 4).first()

        assertThat(weeks).containsExactly(
            WeekOfRunning(week = (TEST_EPOCH_DAY + 3) / 7, metres = 11_000, runs = 2),
            WeekOfRunning(week = (TEST_EPOCH_DAY + 3) / 7 - 1, metres = 4_000, runs = 1),
        ).inOrder()
    }

    @Test
    fun `a hidden run, a run without a distance and a walk add nothing to the week`() = runTest {
        val dao = db.workoutDao()
        dao.insert(run(metres = 5_000))
        dao.insert(run(metres = 5_000).copy(hidden = true))
        dao.insert(run(metres = 5_000).copy(distanceM = null))
        dao.insert(run(metres = 5_000).copy(kind = "WALK"))

        val week = dao.observeWeeklyRunning(weeks = 4).first().single()

        assertThat(week.metres).isEqualTo(5_000)
        assertThat(week.runs).isEqualTo(1)
    }

    // --- Readings ----------------------------------------------------------------------------------

    /** A heart-rate record is one record of many samples: one row each, under one record id. */
    @Test
    fun `a record's samples are stored as rows and removed together`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(listOf(beat("hr-1", 0, 60.0), beat("hr-1", 1, 62.0), beat("hr-2", 0, 70.0)))

        dao.deleteRecord(ORIGIN, "hr-1")

        assertThat(dao.onDay(TEST_EPOCH_DAY).map { it.recordId }).containsExactly("hr-2")
    }

    @Test
    fun `the same sample cannot be stored twice`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(listOf(beat("hr-1", 0, 60.0)))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertAll(listOf(beat("hr-1", 0, 61.0))) }
        }
    }

    @Test
    fun `a day's readings of one kind come back in time order`() = runTest {
        val dao = db.healthReadingDao()
        dao.insertAll(
            listOf(
                beat("hr-1", 1, 62.0, at = 2_000),
                beat("hr-1", 0, 60.0, at = 1_000),
                beat("hr-9", 0, 70.0, day = TEST_EPOCH_DAY + 1),
                beat("st-1", 0, 400.0).copy(kind = "STEPS", unit = "count"),
            ),
        )

        val day = dao.ofKindOnDay("HEART_RATE", TEST_EPOCH_DAY)

        assertThat(day.map { it.value }).containsExactly(60.0, 62.0).inOrder()
    }

    // --- Sleep -------------------------------------------------------------------------------------

    @Test
    fun `a night comes back with its stages, and deleting it takes them too`() = runTest {
        val dao = db.sleepDao()
        val id = dao.insertSession(night("s-1"))
        dao.insertStages(
            listOf(
                SleepStageEntity(sessionId = id, stage = "LIGHT", startMillis = 1_000, endMillis = 2_000),
                SleepStageEntity(sessionId = id, stage = "DEEP", startMillis = 2_000, endMillis = 3_000),
            ),
        )

        assertThat(dao.stagesOf(id).map { it.stage }).containsExactly("LIGHT", "DEEP").inOrder()

        dao.deleteRecord(ORIGIN, "s-1")

        assertThat(dao.allSessions()).isEmpty()
        assertThat(dao.allStages()).isEmpty()
    }

    @Test
    fun `the same night cannot be stored twice`() = runTest {
        db.sleepDao().insertSession(night("s-1"))

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.sleepDao().insertSession(night("s-1")) }
        }
    }

    @Test
    fun `nights are found by the day they ended`() = runTest {
        val dao = db.sleepDao()
        dao.insertSession(night("s-1"))
        dao.insertSession(night("s-2").copy(epochDay = TEST_EPOCH_DAY + 1))

        assertThat(dao.sessionsBetween(TEST_EPOCH_DAY, TEST_EPOCH_DAY).map { it.recordId })
            .containsExactly("s-1")
    }

    // --- Daily summary, corrections, bookkeeping --------------------------------------------------

    @Test
    fun `a day's summary is replaced whole when it is recomputed`() = runTest {
        val dao = db.healthDayDao()
        dao.put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1_000, steps = 9_000, stepsSource = "TOTAL"))
        dao.put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 2_000, steps = 8_000, stepsSource = "CORRECTED"))

        val day = dao.day(TEST_EPOCH_DAY)!!
        assertThat(day.steps).isEqualTo(8_000)
        assertThat(day.stepsSource).isEqualTo("CORRECTED")
        assertThat(dao.observeBetween(TEST_EPOCH_DAY - 6, TEST_EPOCH_DAY).first()).hasSize(1)
    }

    /** No data is null, not zero. */
    @Test
    fun `a figure never read stays null`() = runTest {
        db.healthDayDao().put(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1_000))

        val day = db.healthDayDao().day(TEST_EPOCH_DAY)!!
        assertThat(day.steps).isNull()
        assertThat(day.sleepMinutes).isNull()
    }

    @Test
    fun `a correction is one row per day, the later one replacing it`() = runTest {
        val dao = db.movementCorrectionDao()
        dao.insertAll(listOf(MovementCorrectionEntity(TEST_EPOCH_DAY, 9_000, null, 1_000, null)))
        dao.insertAll(listOf(MovementCorrectionEntity(TEST_EPOCH_DAY, 8_000, null, 2_000, null)))

        assertThat(dao.all().single().steps).isEqualTo(8_000)
    }

    @Test
    fun `where copying stands is kept per kind`() = runTest {
        val dao = db.healthBookkeepingDao()
        dao.putSync(HealthSyncEntity(kind = "STEPS", changesToken = "t-1", tokenAtMillis = 1_000))
        dao.putSync(HealthSyncEntity(kind = "STEPS", changesToken = "t-2", tokenAtMillis = 2_000))

        assertThat(dao.sync("STEPS")?.changesToken).isEqualTo("t-2")
        assertThat(dao.sync("SLEEP")).isNull()
    }

    @Test
    fun `a month is out of date until it is written after its last change`() = runTest {
        val dao = db.healthBookkeepingDao()
        dao.putMonth(ArchiveMonthEntity(month = "2026-08", changedAtMillis = 1_000, writtenAtMillis = 2_000))
        dao.putMonth(ArchiveMonthEntity(month = "2026-09", changedAtMillis = 3_000, writtenAtMillis = 2_000))
        dao.putMonth(ArchiveMonthEntity(month = "2026-10", changedAtMillis = 3_000, writtenAtMillis = null))

        assertThat(dao.monthsOutOfDate().map { it.month }).containsExactly("2026-09", "2026-10").inOrder()
    }

    @Test
    fun `deleting all empties every table a backup replaces`() = runTest {
        db.workoutDao().insertAll(listOf(typed(), synced("abc-1")))
        db.sleepDao().insertSession(night("s-1"))
        db.healthDayDao().insertAll(listOf(HealthDayEntity(epochDay = TEST_EPOCH_DAY, computedAtMillis = 1)))
        db.movementCorrectionDao().insertAll(listOf(MovementCorrectionEntity(TEST_EPOCH_DAY, 1, null, 1, null)))

        db.workoutDao().deleteAll()
        db.sleepDao().deleteAll()
        db.healthDayDao().deleteAll()
        db.movementCorrectionDao().deleteAll()

        assertThat(db.workoutDao().all()).isEmpty()
        assertThat(db.sleepDao().allSessions()).isEmpty()
        assertThat(db.healthDayDao().all()).isEmpty()
        assertThat(db.movementCorrectionDao().all()).isEmpty()
    }

    // --- Fixtures ----------------------------------------------------------------------------------

    private fun typed(day: Long = TEST_EPOCH_DAY, startedAt: Long = 1_000) = WorkoutEntity(
        epochDay = day,
        startedAtMillis = startedAt,
        durationMinutes = 45,
        kind = "STRENGTH",
        title = "Strength",
        distanceM = null,
        energyKcal = 150,
        energySource = "MET_ESTIMATE",
        effort = "MODERATE",
        source = "TYPED",
        origin = null,
        originId = null,
        note = null,
    )

    private fun synced(id: String, day: Long = TEST_EPOCH_DAY, distanceM: Int? = null) =
        WorkoutEntity(
            epochDay = day,
            startedAtMillis = 1_000,
            durationMinutes = 30,
            kind = "RUN",
            title = "Running",
            distanceM = distanceM,
            energyKcal = null,
            energySource = "NONE",
            effort = null,
            source = "SYNCED",
            origin = ORIGIN,
            originId = id,
            note = null,
        )

    private fun run(day: Long = TEST_EPOCH_DAY, metres: Int) =
        typed(day = day).copy(kind = "RUN", title = "Running", distanceM = metres)

    private fun beat(
        record: String,
        index: Int,
        bpm: Double,
        at: Long = 1_000,
        day: Long = TEST_EPOCH_DAY,
    ) = HealthReadingEntity(
        kind = "HEART_RATE",
        startMillis = at,
        endMillis = null,
        value = bpm,
        unit = "bpm",
        origin = ORIGIN,
        recordId = record,
        sampleIndex = index,
        epochDay = day,
    )

    private fun night(record: String) = SleepSessionEntity(
        epochDay = TEST_EPOCH_DAY,
        startMillis = 1_000,
        endMillis = 3_000,
        origin = ORIGIN,
        recordId = record,
        title = null,
    )

    private companion object {
        const val ORIGIN = "com.example.band"
    }
}
