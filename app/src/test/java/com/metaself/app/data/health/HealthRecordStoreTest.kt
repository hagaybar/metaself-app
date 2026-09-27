package com.metaself.app.data.health

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.assumeSqliteRuntime
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.day.RoomDatabaseTransaction
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.aTypedWorkout
import com.metaself.app.domain.profile.aProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The health record's store over a real database: batches applied, windows replaced, days summarised.
 *
 * JUnit 4 by necessity — Robolectric's runner is JUnit 4. Skipped on aarch64, run in CI. Days are in
 * UTC around [TEST_EPOCH_DAY] (2026-09-03). Every figure is invented and round.
 */
@RunWith(RobolectricTestRunner::class)
class HealthRecordStoreTest {

    private lateinit var db: MetaSelfDatabase
    private lateinit var store: RoomHealthStore

    private val day = TEST_EPOCH_DAY

    @Before
    fun setUp() {
        assumeSqliteRuntime()
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MetaSelfDatabase::class.java,
        ).build()
        store = RoomHealthStore(
            db,
            RoomDatabaseTransaction(db),
            HealthRows(ZoneOffset.UTC),
            FakeProfileRepository(aProfile()),
            Today { LocalDate.of(2026, 9, 3) },
            Now { STAMP },
        )
    }

    @After
    fun tearDown() {
        if (this::db.isInitialized) db.close()
    }

    /** D72: the older-history marker is a row of its own, beside the kinds' bookmarks, and leaves them be. */
    @Test
    fun `the older-history marker is kept beside the bookmarks`() = runTest {
        val steps = HealthSyncEntity(kind = "STEPS", changesToken = "t", tokenAtMillis = STAMP, catchUpCursorMillis = 0)
        store.saveBookmark(steps)
        assertThat(store.historyActedOn()).isFalse()

        store.markHistoryActedOn()

        assertThat(store.historyActedOn()).isTrue()
        assertThat(store.bookmark(HealthKind.STEPS)).isEqualTo(steps)
        assertThat(db.healthBookkeepingDao().observeSync().first().map { it.kind })
            .containsExactly("STEPS", HealthStore.HISTORY_MARKER)
    }

    @Test
    fun `a record read twice is stored once, as its latest version`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0, 62.0))), emptyList())
        store.apply(listOf(heart("hr-1", listOf(70.0))), emptyList())

        assertThat(db.healthReadingDao().onDay(day).map { it.value }).containsExactly(70.0)
    }

    @Test
    fun `a deletion removes a record wherever it is, and says which day it touched`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0)), night("s-1"), session("w-1")), emptyList())

        val touched = store.apply(emptyList(), listOf("hr-1", "s-1", "w-1"))

        assertThat(touched).containsExactly(day)
        assertThat(db.healthReadingDao().onDay(day)).isEmpty()
        assertThat(db.sleepDao().allSessions()).isEmpty()
        assertThat(db.workoutDao().all()).isEmpty()
    }

    /** The owner's hiding survives a re-read (activity spec §3.4 step 3). */
    @Test
    fun `a session read again keeps its hidden flag and note`() = runTest {
        store.apply(listOf(session("w-1")), emptyList())
        val row = db.workoutDao().all().single()
        db.workoutDao().update(row.copy(hidden = true, note = "not a run"))

        store.apply(listOf(session("w-1", distanceM = 6_000)), emptyList())

        val again = db.workoutDao().all().single()
        assertThat(again.distanceM).isEqualTo(6_000)
        assertThat(again.hidden).isTrue()
        assertThat(again.note).isEqualTo("not a run")
    }

    @Test
    fun `a night read again replaces its stages`() = runTest {
        store.apply(listOf(night("s-1", stages = 2)), emptyList())
        store.apply(listOf(night("s-1", stages = 1)), emptyList())

        assertThat(db.sleepDao().allStages()).hasSize(1)
    }

    @Test
    fun `replacing a window drops what is no longer there, and nothing outside it`() = runTest {
        store.apply(
            listOf(heart("hr-in", listOf(60.0)), heart("hr-out", listOf(60.0), at = (day - 40) * DAY)),
            emptyList(),
        )

        store.replaceWindow(HealthKind.HEART_RATE, (day - 29) * DAY, (day + 1) * DAY, emptyList())

        assertThat(allHeartRecordIds()).containsExactly("hr-out")
    }

    /** A series that began inside the window goes whole, samples past its edge included (M5). */
    @Test
    fun `replacing a window drops a whole record that began inside it`() = runTest {
        val edge = (day + 1) * DAY
        store.apply(listOf(heart("hr-edge", listOf(60.0, 62.0), at = edge - MINUTE)), emptyList())

        val touched = store.replaceWindow(HealthKind.HEART_RATE, edge - 7 * DAY, edge, emptyList())

        assertThat(allHeartRecordIds()).isEmpty()
        assertThat(touched).containsExactly(day, day + 1)
    }

    /** I1: a window re-read updates a workout in place, so the owner's note stays with its row. */
    @Test
    fun `a window re-read keeps a synced session's id and note, and drops one no longer read`() = runTest {
        store.apply(listOf(session("w-1"), session("w-2", at = day * DAY + 2 * HOUR)), emptyList())
        val kept = db.workoutDao().all().first { it.originId == "w-1" }
        db.workoutDao().update(kept.copy(note = "an invented note"))

        val touched = store.replaceWindow(HealthKind.EXERCISE, (day - 29) * DAY, (day + 1) * DAY, listOf(session("w-1")))

        val after = db.workoutDao().all().single()
        assertThat(after.id).isEqualTo(kept.id)
        assertThat(after.note).isEqualTo("an invented note")
        assertThat(touched).containsExactly(day)
    }

    @Test
    fun `a window re-read keeps a hidden synced session it no longer reads`() = runTest {
        store.apply(listOf(session("w-1")), emptyList())
        db.workoutDao().setHidden(db.workoutDao().all().single().id, true)

        store.replaceWindow(HealthKind.EXERCISE, (day - 29) * DAY, (day + 1) * DAY, emptyList())

        assertThat(db.workoutDao().all().single().hidden).isTrue()
    }

    @Test
    fun `a reading read again on another day returns both days`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())

        val touched = store.apply(listOf(heart("hr-1", listOf(60.0), at = (day + 1) * DAY)), emptyList())

        assertThat(touched).containsExactly(day, day + 1)
    }

    @Test
    fun `a session read again on another day returns both days`() = runTest {
        store.apply(listOf(session("w-1")), emptyList())

        val touched = store.apply(listOf(session("w-1", at = (day + 1) * DAY)), emptyList())

        assertThat(touched).containsExactly(day, day + 1)
    }

    @Test
    fun `a summarised day is written from its readings and its totals`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0, 80.0))), emptyList())
        store.summarise(setOf(day), totals(DayTotals(steps = 9_000)), nowMillis = 1_000)

        val summary = db.healthDayDao().day(day)!!
        assertThat(summary.steps).isEqualTo(9_000)
        assertThat(summary.avgHeartRate).isEqualTo(70)
    }

    @Test
    fun `the owner's correction wins over the total in the stored summary`() = runTest {
        db.movementCorrectionDao().insertAll(
            listOf(MovementCorrectionEntity(epochDay = day, steps = 10_000, activeKcal = null, setAtMillis = 0, note = null)),
        )

        store.summarise(setOf(day), totals(DayTotals(steps = 9_000)), nowMillis = 1_000)

        val summary = db.healthDayDao().day(day)!!
        assertThat(summary.steps).isEqualTo(10_000)
        assertThat(summary.stepsSource).isEqualTo("CORRECTED")
    }

    @Test
    fun `a day with nothing left is removed`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())
        store.summarise(setOf(day), TotalsResult(), nowMillis = 1_000)

        store.apply(emptyList(), listOf("hr-1"))
        store.summarise(setOf(day), TotalsResult(), nowMillis = 2_000)

        assertThat(db.healthDayDao().day(day)).isNull()
    }

    /** I2: a totals call that failed must not wipe a day's steps: what was stored as TOTAL stands. */
    @Test
    fun `a failed total keeps the one stored`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())
        store.summarise(setOf(day), totals(DayTotals(steps = 9_000)), nowMillis = 1_000)

        store.summarise(setOf(day), TotalsResult(failed = setOf(TotalMetric.STEPS)), nowMillis = 2_000)

        assertThat(db.healthDayDao().day(day)!!.steps).isEqualTo(9_000)
    }

    /** D77: summarising again after a typed workout counts it and keeps the stored totals. */
    @Test
    fun `a typed workout is counted when its day is summarised again, and the stored totals stay`() = runTest {
        store.summarise(setOf(day), TotalsResult(byDay = mapOf(day to DayTotals(steps = 9_000))), STAMP)
        db.workoutDao().insert(
            WorkoutEntity(
                epochDay = day, startedAtMillis = day * 86_400_000L, durationMinutes = 45, kind = "STRENGTH",
                title = null, distanceM = null, energyKcal = 150, energySource = "MET_ESTIMATE",
                effort = "MODERATE", source = "TYPED", origin = null, originId = null, note = null,
            ),
        )

        store.summarise(setOf(day), TotalsResult.ALL_FAILED, STAMP)

        val summary = db.healthDayDao().day(day)!!
        assertThat(summary.steps).isEqualTo(9_000)
        assertThat(summary.workoutCount).isEqualTo(1)
        assertThat(summary.workoutMinutes).isEqualTo(45)
    }

    /**
     * D76/D77 through the real typed store and the real DAO: a typed workout logged, changed and
     * deleted by its id, each write summarising its day again; a re-log carrying an id already used
     * is a new row; the synced session on the same day is never touched.
     */
    @Test
    fun `a typed workout is logged, changed and deleted by id, and the synced one beside it stays`() = runTest {
        val typed = RoomTypedWorkouts(db.workoutDao(), RoomDatabaseTransaction(db), store, Now { STAMP })
        store.summarise(setOf(day), TotalsResult(byDay = mapOf(day to DayTotals(steps = 9_000))), STAMP)
        val syncedId = db.workoutDao().insert(
            WorkoutEntity(
                epochDay = day, startedAtMillis = day * DAY, durationMinutes = 30, kind = "RUN",
                title = "Running", distanceM = 5_000, energyKcal = null, energySource = "NONE",
                effort = null, source = "SYNCED", origin = "com.example.band", originId = "s-1", note = null,
            ),
        )

        val id = typed.log(aTypedWorkout(id = 0, epochDay = day, minutes = 45))
        // Taken after the first summary, which may work out the synced session's heart-rate figures.
        val synced = db.workoutDao().byId(syncedId)!!
        assertThat(db.healthDayDao().day(day)!!.workoutCount).isEqualTo(2)
        assertThat(db.healthDayDao().day(day)!!.workoutMinutes).isEqualTo(75)

        assertThat(typed.change(aTypedWorkout(id = id, epochDay = day - 2, minutes = 60))).isTrue()
        assertThat(db.workoutDao().byId(id)!!.durationMinutes).isEqualTo(60)
        assertThat(db.workoutDao().byId(id)!!.epochDay).isEqualTo(day)
        assertThat(db.healthDayDao().day(day)!!.workoutMinutes).isEqualTo(90)

        assertThat(typed.change(aTypedWorkout(id = syncedId, epochDay = day, minutes = 60))).isFalse()
        assertThat(typed.delete(aTypedWorkout(id = syncedId, epochDay = day))).isFalse()
        assertThat(db.workoutDao().byId(syncedId)).isEqualTo(synced)

        assertThat(typed.delete(aTypedWorkout(id = id, epochDay = day - 2))).isTrue()
        assertThat(db.workoutDao().byId(id)).isNull()
        assertThat(db.healthDayDao().day(day)!!.workoutCount).isEqualTo(1)
        assertThat(db.healthDayDao().day(day)!!.steps).isEqualTo(9_000)
        assertThat(typed.delete(aTypedWorkout(id = id, epochDay = day))).isFalse()

        val again = typed.log(aTypedWorkout(id = syncedId, epochDay = day, minutes = 45))
        assertThat(again).isNotEqualTo(syncedId)
        assertThat(db.workoutDao().all().map { it.id }).containsExactly(syncedId, again)
        assertThat(db.workoutDao().byId(syncedId)).isEqualTo(synced)
        assertThat(db.healthDayDao().day(day)!!.workoutCount).isEqualTo(2)
    }

    /** I2: a call that worked and found nothing means nothing — the old figure is stale, not kept. */
    @Test
    fun `a total that came back empty clears the stored one, and an empty day goes`() = runTest {
        store.summarise(setOf(day), totals(DayTotals(steps = 9_000)), nowMillis = 1_000)
        assertThat(db.healthDayDao().day(day)!!.steps).isEqualTo(9_000)

        store.summarise(setOf(day), TotalsResult(), nowMillis = 2_000)

        assertThat(db.healthDayDao().day(day)).isNull()
    }

    /** D70, on the standard body: born 1980, in 2026, so an estimated maximum of 174. */
    @Test
    fun `a workout gets its heart-rate figures from the readings inside it`() = runTest {
        store.apply(
            listOf(session("w-1"), heart("hr-1", listOf(120.0, 140.0), at = day * DAY + 60_000)),
            emptyList(),
        )

        store.summarise(setOf(day), TotalsResult(), nowMillis = 1_000)

        val workout = db.workoutDao().all().single()
        assertThat(workout.avgHeartRate).isEqualTo(130)
        assertThat(workout.maxHeartRate).isEqualTo(140)
        assertThat(workout.zoneMaxSource).isEqualTo("ESTIMATED")
        assertThat(workout.zoneSeconds).isNotNull()
    }

    /** D4: nothing recorded a typed workout, so a reading inside its typed window is never its heart rate. */
    @Test
    fun `a typed workout keeps no heart-rate figures, even with readings inside its window`() = runTest {
        db.workoutDao().insert(
            WorkoutEntity(
                epochDay = day, startedAtMillis = day * DAY, durationMinutes = 45, kind = "RUN",
                title = null, distanceM = null, energyKcal = 300, energySource = "TYPED",
                effort = "MODERATE", source = "TYPED", origin = null, originId = null, note = null,
            ),
        )
        store.apply(listOf(heart("hr-1", listOf(120.0, 140.0), at = day * DAY + 60_000)), emptyList())

        store.summarise(setOf(day), TotalsResult(), nowMillis = 1_000)

        val workout = db.workoutDao().all().single()
        assertThat(workout.avgHeartRate).isNull()
        assertThat(workout.maxHeartRate).isNull()
        assertThat(workout.zoneSeconds).isNull()
        assertThat(workout.zoneMaxSource).isNull()
    }

    /** M1: figures from readings since deleted are cleared, not left standing. */
    @Test
    fun `a workout whose readings are gone loses its heart-rate figures`() = runTest {
        store.apply(
            listOf(session("w-1"), heart("hr-1", listOf(120.0, 140.0), at = day * DAY + 60_000)),
            emptyList(),
        )
        store.summarise(setOf(day), TotalsResult(), nowMillis = 1_000)

        store.apply(emptyList(), listOf("hr-1"))
        store.summarise(setOf(day), TotalsResult(), nowMillis = 2_000)

        val workout = db.workoutDao().all().single()
        assertThat(workout.avgHeartRate).isNull()
        assertThat(workout.maxHeartRate).isNull()
        assertThat(workout.zoneSeconds).isNull()
        assertThat(workout.zoneMaxSource).isNull()
    }

    /** M3: a workout across midnight gets the samples filed on the next day, which alone was touched. */
    @Test
    fun `a workout across midnight is refigured when the next day is summarised`() = runTest {
        store.apply(listOf(session("w-1", at = day * DAY - 10 * MINUTE)), emptyList())
        store.apply(listOf(heart("hr-1", listOf(120.0, 140.0), at = day * DAY + 5 * MINUTE)), emptyList())

        store.summarise(setOf(day), TotalsResult(), nowMillis = 1_000)

        val workout = db.workoutDao().all().single()
        assertThat(workout.epochDay).isEqualTo(day - 1)
        assertThat(workout.avgHeartRate).isEqualTo(130)
        assertThat(db.healthDayDao().day(day - 1)).isNull()
    }

    /** M4: the archive month is marked when rows change, stamped with the store's own clock. */
    @Test
    fun `applying a record marks its month as out of date`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())

        val months = db.healthBookkeepingDao().monthsOutOfDate()
        assertThat(months.map { it.month }).containsExactly("2026-09")
        assertThat(months.single().changedAtMillis).isEqualTo(STAMP)
    }

    @Test
    fun `summarising alone marks no month`() = runTest {
        store.summarise(setOf(day), totals(DayTotals(steps = 9_000)), nowMillis = 1_000)

        assertThat(db.healthBookkeepingDao().monthsOutOfDate()).isEmpty()
    }

    /** D71: the Drive month file holds every simple reading filed on the month's days, and only those. */
    @Test
    fun `a month's readings are every kind's rows on its days, and no other month's`() = runTest {
        val lastOfAugust = LocalDate.of(2026, 8, 31).toEpochDay()
        val firstOfSeptember = LocalDate.of(2026, 9, 1).toEpochDay()
        val lastOfSeptember = LocalDate.of(2026, 9, 30).toEpochDay()
        val firstOfOctober = LocalDate.of(2026, 10, 1).toEpochDay()
        store.apply(
            listOf(
                heart("hr-aug", listOf(60.0), at = lastOfAugust * DAY + HOUR),
                steps("st-aug", at = lastOfAugust * DAY + HOUR),
                heart("hr-sep-1", listOf(60.0, 62.0), at = firstOfSeptember * DAY + HOUR),
                steps("st-sep-1", at = firstOfSeptember * DAY + HOUR),
                heart("hr-sep-30", listOf(64.0), at = lastOfSeptember * DAY + HOUR),
                steps("st-sep-30", at = lastOfSeptember * DAY + HOUR),
                heart("hr-oct", listOf(60.0), at = firstOfOctober * DAY + HOUR),
            ),
            emptyList(),
        )

        val september = store.readingsIn("2026-09")

        assertThat(september.rows.map { it.recordId }.toSet())
            .containsExactly("hr-sep-1", "st-sep-1", "hr-sep-30", "st-sep-30")
        assertThat(september.rows).hasSize(5)
        assertThat(september.rows.map { it.epochDay }.toSet()).containsExactly(firstOfSeptember, lastOfSeptember)
    }

    /**
     * D71: a month stays out of date until it is written as of the changedAt its own read saw; a mark
     * built from an older read no longer takes once the month has changed again.
     */
    @Test
    fun `a month written as of its own read is no longer out of date, until it changes again`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())
        assertThat(store.monthsOutOfDate()).containsExactly("2026-09")

        val seenAtFirstChange = store.readingsIn("2026-09").changedAtMillis
        store.markWritten("2026-09", seenAtFirstChange)
        assertThat(store.monthsOutOfDate()).isEmpty()

        // A change stamped by a later clock moves changedAt on; the old mark no longer covers it.
        val later = RoomHealthStore(
            db, RoomDatabaseTransaction(db), HealthRows(ZoneOffset.UTC), FakeProfileRepository(aProfile()),
            Today { LocalDate.of(2026, 9, 3) }, Now { STAMP + 1 },
        )
        later.apply(listOf(heart("hr-2", listOf(62.0))), emptyList())
        assertThat(store.monthsOutOfDate()).containsExactly("2026-09")

        // Marking with the stale, first changedAt no longer takes: the month stays out of date.
        store.markWritten("2026-09", seenAtFirstChange)
        assertThat(store.monthsOutOfDate()).containsExactly("2026-09")
    }

    /** The archive reads Drive's copy first for a month this phone has never written (D71). */
    @Test
    fun `a month is written by this phone only once it has been marked, and stays so when changed`() = runTest {
        assertThat(store.everWritten("2026-09")).isFalse()
        store.apply(listOf(heart("hr-1", listOf(60.0))), emptyList())
        assertThat(store.everWritten("2026-09")).isFalse()

        store.markWritten("2026-09", store.readingsIn("2026-09").changedAtMillis)
        assertThat(store.everWritten("2026-09")).isTrue()

        store.apply(listOf(heart("hr-2", listOf(62.0))), emptyList())
        assertThat(store.everWritten("2026-09")).isTrue()
    }

    /**
     * Bringing rows back from Drive (D71) only adds: the phone's own row is kept on a clash, a missing
     * sample goes in with its own index and day, and only the days actually added are returned.
     */
    @Test
    fun `adding missing rows keeps the phone's own and adds the rest as they were`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(70.0))), emptyList())
        store.markWritten("2026-09", store.readingsIn("2026-09").changedAtMillis)
        val later = RoomHealthStore(
            db, RoomDatabaseTransaction(db), HealthRows(ZoneOffset.UTC), FakeProfileRepository(aProfile()),
            Today { LocalDate.of(2026, 9, 3) }, Now { STAMP + 1 },
        )

        val added = later.insertMissing(
            listOf(
                row("hr-1", index = 0, bpm = 50.0, day = day),
                row("hr-1", index = 1, bpm = 50.0, day = day + 1),
            ),
        )

        assertThat(added.days).containsExactly(day + 1)
        assertThat(added.rows).isEqualTo(1)
        val rows = db.healthReadingDao().ofKindInDays("HEART_RATE", day, day + 1)
        assertThat(rows.map { Triple(it.sampleIndex, it.value, it.epochDay) })
            .containsExactly(Triple(0, 70.0, day), Triple(1, 50.0, day + 1))
        // The month gained a row, so it is out of date again, stamped with the store's clock.
        assertThat(db.healthBookkeepingDao().month("2026-09")!!.changedAtMillis).isEqualTo(STAMP + 1)
        assertThat(store.monthsOutOfDate()).containsExactly("2026-09")
    }

    @Test
    fun `adding rows the phone already holds adds nothing and marks nothing`() = runTest {
        store.apply(listOf(heart("hr-1", listOf(70.0))), emptyList())
        store.markWritten("2026-09", store.readingsIn("2026-09").changedAtMillis)

        assertThat(store.insertMissing(listOf(row("hr-1", index = 0, bpm = 50.0, day = day))))
            .isEqualTo(Inserted.NONE)
        assertThat(store.monthsOutOfDate()).isEmpty()
    }

    /**
     * A series over midnight at a month's end is in two month files. Brought back from the two, in
     * either order, it keeps every sample: nothing in one batch removes what the other added.
     */
    @Test
    fun `a series split across two months keeps every sample from two batches`() = runTest {
        val lastOfAugust = LocalDate.of(2026, 8, 31).toEpochDay()
        val firstOfSeptember = LocalDate.of(2026, 9, 1).toEpochDay()

        store.insertMissing(listOf(row("hr-x", index = 1, bpm = 62.0, day = firstOfSeptember)))
        store.insertMissing(listOf(row("hr-x", index = 0, bpm = 60.0, day = lastOfAugust)))

        val rows = db.healthReadingDao().ofKindInDays("HEART_RATE", lastOfAugust, firstOfSeptember)
        assertThat(rows.map { it.sampleIndex to it.epochDay })
            .containsExactly(0 to lastOfAugust, 1 to firstOfSeptember)
        assertThat(store.monthsOutOfDate()).containsExactly("2026-08", "2026-09").inOrder()
        assertThat(store.readingsIn("2026-08").rows.map { it.sampleIndex }).containsExactly(0)
        assertThat(store.readingsIn("2026-09").rows.map { it.sampleIndex }).containsExactly(1)
    }

    // --- Helpers -----------------------------------------------------------------------------------

    /** One heart-rate row as a month file holds it: sample [index], filed on [day], a minute in. */
    private fun row(id: String, index: Int, bpm: Double, day: Long) = HealthReadingEntity(
        kind = "HEART_RATE", startMillis = day * DAY + index * MINUTE, endMillis = null, value = bpm,
        unit = "bpm", origin = ORIGIN, recordId = id, sampleIndex = index, epochDay = day,
    )

    private fun totals(onDay: DayTotals) = TotalsResult(byDay = mapOf(day to onDay))

    private suspend fun allHeartRecordIds() =
        db.healthReadingDao().ofKindBetween("HEART_RATE", Long.MIN_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE)
            .map { it.recordId }

    /** A heart-rate series, its samples one minute apart from [at]. */
    private fun heart(id: String, bpm: List<Double>, at: Long = day * DAY) = ReadRecord.Reading(
        kind = HealthKind.HEART_RATE,
        origin = ORIGIN,
        recordId = id,
        samples = bpm.mapIndexed { index, value -> Sample(at + index * MINUTE, null, value) },
    )

    /** One step count over the minute from [at]. */
    private fun steps(id: String, at: Long) = ReadRecord.Reading(
        kind = HealthKind.STEPS,
        origin = ORIGIN,
        recordId = id,
        samples = listOf(Sample(at, at + MINUTE, 100.0)),
    )

    /** A night from an hour before [day] began to an hour after, so it belongs to [day]. */
    private fun night(id: String, stages: Int = 2) = ReadRecord.Night(
        origin = ORIGIN,
        recordId = id,
        startMillis = day * DAY - HOUR,
        endMillis = day * DAY + HOUR,
        title = null,
        stages = List(stages) { index ->
            val start = day * DAY - HOUR + index * 10 * MINUTE
            StageSpan("LIGHT", start, start + 10 * MINUTE)
        },
    )

    /** A half-hour run from [at], the start of [day] unless said. */
    private fun session(id: String, distanceM: Int? = 5_000, at: Long = day * DAY) = ReadRecord.Session(
        origin = ORIGIN,
        recordId = id,
        startMillis = at,
        endMillis = at + 30 * MINUTE,
        kind = "RUN",
        title = "Running",
        distanceM = distanceM,
        energyKcal = null,
    )

    private companion object {
        const val ORIGIN = "com.example.band"
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val MINUTE = 60_000L
        const val STAMP = 5_000L
    }
}
