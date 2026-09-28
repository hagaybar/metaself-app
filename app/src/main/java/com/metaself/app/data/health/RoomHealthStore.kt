package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.HeartRateZones
import com.metaself.app.domain.movement.CountedWorkouts
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The health record's writes (D65). One transaction per batch, so a record and its rows — a night and
 * its stages, a series and its samples — are never half-stored.
 *
 * A record read again replaces its earlier rows: rows are found by Health Connect's record id, deleted,
 * and inserted afresh. A synced workout is the exception: it is updated in place, keeping the owner's
 * `hidden` and `note`, whether it comes in a batch of changes or in a window re-read.
 *
 * The archive months (D71) of the days whose rows a write changed are marked out of date by that
 * write, stamped with [now]; summarising marks nothing. It is also the archive's [ArchiveRecord]: which
 * months are out of date, what is in one, marking one written, and adding back the rows Drive has
 * that the phone lacks. One instance serves both.
 */
@Singleton
class RoomHealthStore @Inject constructor(
    private val database: MetaSelfDatabase,
    private val transaction: DatabaseTransaction,
    private val rows: HealthRows,
    private val profiles: ProfileRepository,
    private val today: Today,
    private val now: Now,
    private val walks: WalkChoices,
) : HealthStore, ArchiveRecord {

    private val readingDao get() = database.healthReadingDao()
    private val sleepDao get() = database.sleepDao()
    private val workoutDao get() = database.workoutDao()
    private val dayDao get() = database.healthDayDao()
    private val correctionDao get() = database.movementCorrectionDao()
    private val bookkeepingDao get() = database.healthBookkeepingDao()

    override suspend fun bookmark(kind: HealthKind): HealthSyncEntity? = bookkeepingDao.sync(kind.name)

    override suspend fun saveBookmark(bookmark: HealthSyncEntity) = bookkeepingDao.putSync(bookmark)

    override suspend fun historyActedOn(): Boolean = bookkeepingDao.sync(HealthStore.HISTORY_MARKER) != null

    override suspend fun markHistoryActedOn() = bookkeepingDao.putSync(HealthSyncEntity(kind = HealthStore.HISTORY_MARKER))

    override suspend fun recentRecheckDue(): Boolean = bookkeepingDao.sync(HealthStore.RECHECK_MARKER)?.catchUpDone != true

    override suspend fun recheckFromDay(): Long? =
        bookkeepingDao.sync(HealthStore.RECHECK_MARKER)?.takeUnless { it.catchUpDone }?.catchUpCursorMillis

    override suspend fun setRecentRecheckDue(due: Boolean, fromDay: Long?) = bookkeepingDao.putSync(
        HealthSyncEntity(
            kind = HealthStore.RECHECK_MARKER,
            catchUpCursorMillis = fromDay.takeIf { due },
            catchUpDone = !due,
        ),
    )

    override suspend fun apply(records: List<ReadRecord>, deletedIds: List<String>): Set<Long> {
        val touched = mutableSetOf<Long>()
        transaction.run {
            // Deletions first: the page's order between its two lists is not kept, so an id in both
            // (not expected, as Health Connect does not reuse ids) ends stored rather than lost.
            deletedIds.forEach { id ->
                touched += readingDao.daysOf(id)
                touched += sleepDao.daysOf(id)
                touched += workoutDao.daysOfSynced(id)
                readingDao.deleteByRecordId(id)
                sleepDao.deleteByRecordId(id)
                workoutDao.deleteSynced(id)
            }
            records.forEach { record -> touched += store(record) }
            markMonths(touched)
        }
        return touched
    }

    override suspend fun replaceWindow(
        kind: HealthKind,
        fromMillis: Long,
        toMillis: Long,
        records: List<ReadRecord>,
    ): Set<Long> {
        val touched = mutableSetOf<Long>()
        transaction.run {
            when (kind) {
                HealthKind.SLEEP -> {
                    touched += sleepDao.daysBetween(fromMillis, toMillis)
                    sleepDao.deleteBetween(fromMillis, toMillis)
                    records.forEach { record -> touched += store(record) }
                }
                HealthKind.EXERCISE -> {
                    // Stored first, updated in place; then the visible synced sessions the read no
                    // longer returned are dropped. A hidden one stays, so the next read cannot revive it.
                    records.forEach { record -> touched += store(record) }
                    val read = records.map { it.recordId }.toSet()
                    workoutDao.visibleSyncedBetween(fromMillis, toMillis)
                        .filter { it.originId !in read }
                        .forEach { gone ->
                            touched += gone.epochDay
                            workoutDao.deleteSyncedRow(gone.id)
                        }
                }
                else -> {
                    // Whole records with any sample in the window, so none is left half-deleted.
                    val (fromDay, toDay) = daysAround(fromMillis, toMillis)
                    readingDao.recordsOfKindBetween(kind.name, fromDay, toDay, fromMillis, toMillis).forEach { key ->
                        touched += readingDao.daysOf(key.origin, key.recordId)
                        readingDao.deleteRecord(key.origin, key.recordId)
                    }
                    records.forEach { record -> touched += store(record) }
                }
            }
            markMonths(touched)
        }
        return touched
    }

    override suspend fun sessionGaps(days: Set<Long>): List<SessionGap> {
        if (days.isEmpty()) return emptyList()
        val uncounted = walks.uncounted.first()
        return workoutDao.missingTotalsOn(days.toList())
            .filter { CountedWorkouts.counts(WorkoutKind.parse(it.kind), it.origin, uncounted) }
            .map { row ->
                SessionGap(
                    id = row.id,
                    epochDay = row.epochDay,
                    startMillis = row.startedAtMillis,
                    endMillis = endOf(row),
                    needsDistance = row.distanceM == null,
                    needsEnergy = row.energyKcal == null && row.energySource == "NONE",
                )
            }
    }

    /**
     * Read and written in one transaction, so a re-read of the session landing between the two cannot
     * be overwritten with the older row. Marks no archive month: workouts are not in the archive.
     */
    override suspend fun fillSessionTotals(id: Long, totals: SessionTotals) {
        transaction.run {
            val row = workoutDao.byId(id) ?: return@run
            if (row.source != WorkoutSource.SYNCED.name) return@run
            val energy = totals.energyKcal?.takeIf { row.energyKcal == null && row.energySource == "NONE" }
            val filled = row.copy(
                distanceM = row.distanceM ?: totals.distanceM,
                energyKcal = energy ?: row.energyKcal,
                energySource = if (energy != null) "BAND" else row.energySource,
            )
            if (filled != row) workoutDao.update(filled)
        }
    }

    /** One record, replacing its earlier rows. Inside a transaction the caller holds. */
    private suspend fun store(record: ReadRecord): Set<Long> = when (record) {
        is ReadRecord.Reading -> {
            val before = readingDao.daysOf(record.origin, record.recordId)
            readingDao.deleteRecord(record.origin, record.recordId)
            val fresh = rows.readings(record)
            readingDao.insertAll(fresh)
            before.toSet() + fresh.map { it.epochDay }
        }
        is ReadRecord.Night -> {
            val before = sleepDao.daysOf(record.recordId)
            sleepDao.deleteRecord(record.origin, record.recordId)
            val (night, stages) = rows.night(record)
            val id = sleepDao.insertSession(night)
            sleepDao.insertStages(stages.map { it.copy(sessionId = id) })
            before.toSet() + night.epochDay
        }
        is ReadRecord.Session -> {
            val fresh = rows.workout(record)
            val existing = workoutDao.synced(record.origin, record.recordId)
            if (existing == null) {
                workoutDao.insert(fresh)
                setOf(fresh.epochDay)
            } else {
                // D82: a workout file's figures are kept — its steps always, its distance and
                // calories while Health Connect still gives none; once it does, its own is taken.
                val fileDistance = fresh.distanceM == null && existing.distanceSource == FILE_SOURCE
                val fileEnergy = fresh.energyKcal == null && existing.energySource == FILE_SOURCE
                workoutDao.update(
                    fresh.copy(
                        id = existing.id,
                        hidden = existing.hidden,
                        note = existing.note,
                        avgHeartRate = existing.avgHeartRate,
                        maxHeartRate = existing.maxHeartRate,
                        zoneSeconds = existing.zoneSeconds,
                        zoneMaxSource = existing.zoneMaxSource,
                        distanceM = if (fileDistance) existing.distanceM else fresh.distanceM,
                        distanceSource = if (fileDistance) existing.distanceSource else fresh.distanceSource,
                        energyKcal = if (fileEnergy) existing.energyKcal else fresh.energyKcal,
                        energySource = if (fileEnergy) existing.energySource else fresh.energySource,
                        steps = existing.steps,
                        stepsSource = existing.stepsSource,
                    ),
                )
                setOf(existing.epochDay, fresh.epochDay)
            }
        }
    }

    /**
     * Each day's summary, and the heart-rate figures of its workouts. A workout filed on the day before
     * that runs past midnight has samples filed on this day, so its figures are worked out again too;
     * the day before is not re-summarised, as its summary counts that workout's minutes, not its
     * heart rate.
     *
     * Which apps' walks do not count (D81), and the owner's session splits (D92), are read once for the
     * whole call.
     */
    override suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long) {
        val maxHeartRate = profiles.profile.first()
            ?.let { HeartRateZones.estimatedMax(it.birthYear, today().year) }
        val uncounted = walks.uncounted.first()
        val splits = database.sessionSplitDao().all().mapNotNull { it.toSplit() }
        days.sorted().forEach { epochDay ->
            transaction.run {
                if (epochDay - 1 !in days) {
                    workoutDao.onDay(epochDay - 1)
                        .filter { rows.dayOf(endOf(it) - 1) >= epochDay }
                        .forEach { withHeartRate(it, maxHeartRate, uncounted) }
                }
                val dayWorkouts = workoutDao.onDay(epochDay).map { withHeartRate(it, maxHeartRate, uncounted) }
                val summary = DaySummary.of(
                    epochDay = epochDay,
                    totals = keepingStored(totals.byDay[epochDay] ?: DayTotals(), totals.failed, dayDao.day(epochDay)),
                    readings = readingDao.onDay(epochDay),
                    nights = sleepDao.nightsOn(epochDay),
                    workouts = dayWorkouts,
                    correction = correctionDao.day(epochDay),
                    nowMillis = nowMillis,
                    uncountedWalkApps = uncounted,
                    splits = splits,
                )
                if (summary == null) dayDao.delete(epochDay) else dayDao.put(summary)
            }
        }
    }

    /**
     * A total whose call failed this time keeps the one already stored, when that one was Health
     * Connect's: a failure never zeroes a total. A total whose call succeeded is taken as given, null
     * included — no data now means the old figure is stale.
     */
    private fun keepingStored(given: DayTotals, failed: Set<TotalMetric>, stored: HealthDayEntity?): DayTotals {
        fun <T> kept(metric: TotalMetric, now: T?, before: T?, beforeSource: String?): T? =
            if (metric in failed) before?.takeIf { beforeSource == "TOTAL" } else now
        return DayTotals(
            steps = kept(TotalMetric.STEPS, given.steps, stored?.steps, stored?.stepsSource),
            distanceM = kept(TotalMetric.DISTANCE, given.distanceM, stored?.distanceM, stored?.distanceSource),
            activeKcal = kept(TotalMetric.ACTIVE_KCAL, given.activeKcal, stored?.activeKcal, stored?.activeKcalSource),
            totalKcal = kept(TotalMetric.TOTAL_KCAL, given.totalKcal, stored?.totalKcal, stored?.totalKcalSource),
        )
    }

    /**
     * A workout's heart-rate figures from the readings inside it (D70); all four null when none fall
     * inside, so figures from readings since deleted do not stand. Unchanged when there is no profile
     * to estimate a maximum from.
     *
     * A typed workout (D4) is never given figures this way: nothing recorded it, so a reading that
     * happens to fall inside its typed window is not its heart rate. All four fields stay, or are
     * made, null. A walk from an app whose walks do not count (D81) is treated the same, as it is
     * counted nowhere; its readings stay, so switching the app back on works the figures out again.
     */
    private suspend fun withHeartRate(workout: WorkoutEntity, maxHeartRate: Int?, uncounted: Set<String>): WorkoutEntity {
        if (workout.source == WorkoutSource.TYPED.name || !DaySummary.counts(workout, uncounted)) {
            if (workout.avgHeartRate == null && workout.maxHeartRate == null &&
                workout.zoneSeconds == null && workout.zoneMaxSource == null
            ) {
                return workout
            }
            val cleared = workout.copy(avgHeartRate = null, maxHeartRate = null, zoneSeconds = null, zoneMaxSource = null)
            workoutDao.update(cleared)
            return cleared
        }
        if (maxHeartRate == null) return workout
        val end = endOf(workout)
        val (fromDay, toDay) = daysAround(workout.startedAtMillis, end)
        val samples = readingDao.ofKindBetween(HealthKind.HEART_RATE.name, fromDay, toDay, workout.startedAtMillis, end)
            .map { it.startMillis to it.value }
        val figures = HeartRateZones.of(samples, end, maxHeartRate)
        val updated = workout.copy(
            avgHeartRate = figures?.average,
            maxHeartRate = figures?.maximum,
            zoneSeconds = figures?.zonesAsText(),
            zoneMaxSource = figures?.let { "ESTIMATED" },
        )
        if (updated != workout) workoutDao.update(updated)
        return updated
    }

    /**
     * Where a workout ends as far as this store knows: its start plus its whole minutes. There is no
     * end column, so up to 59 seconds at the end are not seen. Accepted.
     */
    private fun endOf(workout: WorkoutEntity): Long = workout.startedAtMillis + workout.durationMinutes * 60_000L

    /**
     * The local days covering [fromMillis, toMillis), widened by one day each side: rows are filed in
     * the zone the phone was in when they were stored, which may not be today's.
     */
    private fun daysAround(fromMillis: Long, toMillis: Long): Pair<Long, Long> =
        rows.dayOf(fromMillis) - 1 to rows.dayOf(toMillis) + 1

    override suspend fun monthsOutOfDate(): List<String> = bookkeepingDao.monthsOutOfDate().map { it.month }

    /**
     * Kind by kind, so the `(kind, epochDay, startMillis)` index serves every query. The month's
     * changedAt is read in the SAME transaction as the rows, so it reflects exactly what [rows]
     * holds — a later write can mark the month written as of this value and no other.
     */
    override suspend fun readingsIn(month: String): MonthRows {
        val yearMonth = YearMonth.parse(month)
        val first = yearMonth.atDay(1).toEpochDay()
        val last = yearMonth.atEndOfMonth().toEpochDay()
        lateinit var result: MonthRows
        transaction.run {
            val rows = HealthKind.entries.filter { it.isReading }.flatMap { kind ->
                readingDao.ofKindInDays(kind.name, first, last)
            }
            val changedAtMillis = bookkeepingDao.month(month)?.changedAtMillis ?: 0L
            result = MonthRows(rows, changedAtMillis)
        }
        return result
    }

    override suspend fun everWritten(month: String): Boolean =
        bookkeepingDao.month(month)?.writtenAtMillis != null

    /**
     * A conditional update, never a read-then-replace: it takes only when the month's changedAt is
     * still [changedAtSeen], so a change stamped after the read that produced it — and before this
     * call — leaves the month out of date rather than marked for a version it never wrote.
     */
    override suspend fun markWritten(month: String, changedAtSeen: Long) {
        bookkeepingDao.markWritten(month, changedAtSeen)
    }

    override suspend fun insertMissing(rows: List<HealthReadingEntity>): Inserted {
        if (rows.isEmpty()) return Inserted.NONE
        val added = mutableSetOf<Long>()
        var count = 0
        transaction.run {
            val ids = readingDao.insertMissing(rows.map { it.copy(id = 0) })
            rows.zip(ids).forEach { (row, id) -> if (id != -1L) { added += row.epochDay; count++ } }
            markMonths(added)
        }
        return Inserted(added, count)
    }

    /** The Drive month files of these days are out of date (D71); when each was last written is kept. */
    private suspend fun markMonths(days: Set<Long>) {
        if (days.isEmpty()) return
        val stamp = now()
        days.map { LocalDate.ofEpochDay(it).toString().substring(0, 7) }.toSet().forEach { month ->
            val known = bookkeepingDao.month(month)
            bookkeepingDao.putMonth(
                ArchiveMonthEntity(month = month, changedAtMillis = stamp, writtenAtMillis = known?.writtenAtMillis),
            )
        }
    }
}

/** A workout figure's source when a workout file gave it (D82), as stored. */
private const val FILE_SOURCE = "FILE"
