package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.profile.ProfileRepository
import com.metaself.app.data.time.Now
import com.metaself.app.data.time.Today
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.HeartRateZones
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * The health record's writes (D65). One transaction per batch, so a record and its rows — a night and
 * its stages, a series and its samples — are never half-stored.
 *
 * A record read again replaces its earlier rows: rows are found by Health Connect's record id, deleted,
 * and inserted afresh. A synced workout is the exception: it is updated in place, keeping the owner's
 * `hidden` and `note`, whether it comes in a batch of changes or in a window re-read.
 *
 * The archive months (D71) of the days whose rows a write changed are marked out of date by that
 * write, stamped with [now]; summarising marks nothing.
 */
class RoomHealthStore @Inject constructor(
    private val database: MetaSelfDatabase,
    private val transaction: DatabaseTransaction,
    private val rows: HealthRows,
    private val profiles: ProfileRepository,
    private val today: Today,
    private val now: Now,
) : HealthStore {

    private val readingDao get() = database.healthReadingDao()
    private val sleepDao get() = database.sleepDao()
    private val workoutDao get() = database.workoutDao()
    private val dayDao get() = database.healthDayDao()
    private val correctionDao get() = database.movementCorrectionDao()
    private val bookkeepingDao get() = database.healthBookkeepingDao()

    override suspend fun bookmark(kind: HealthKind): HealthSyncEntity? = bookkeepingDao.sync(kind.name)

    override suspend fun saveBookmark(bookmark: HealthSyncEntity) = bookkeepingDao.putSync(bookmark)

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
                workoutDao.update(
                    fresh.copy(
                        id = existing.id,
                        hidden = existing.hidden,
                        note = existing.note,
                        avgHeartRate = existing.avgHeartRate,
                        maxHeartRate = existing.maxHeartRate,
                        zoneSeconds = existing.zoneSeconds,
                        zoneMaxSource = existing.zoneMaxSource,
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
     */
    override suspend fun summarise(days: Set<Long>, totals: TotalsResult, nowMillis: Long) {
        val maxHeartRate = profiles.profile.first()
            ?.let { HeartRateZones.estimatedMax(it.birthYear, today().year) }
        days.sorted().forEach { epochDay ->
            transaction.run {
                if (epochDay - 1 !in days) {
                    workoutDao.onDay(epochDay - 1)
                        .filter { rows.dayOf(endOf(it) - 1) >= epochDay }
                        .forEach { withHeartRate(it, maxHeartRate) }
                }
                val dayWorkouts = workoutDao.onDay(epochDay).map { withHeartRate(it, maxHeartRate) }
                val summary = DaySummary.of(
                    epochDay = epochDay,
                    totals = keepingStored(totals.byDay[epochDay] ?: DayTotals(), totals.failed, dayDao.day(epochDay)),
                    readings = readingDao.onDay(epochDay),
                    nights = sleepDao.nightsOn(epochDay),
                    workouts = dayWorkouts,
                    correction = correctionDao.day(epochDay),
                    nowMillis = nowMillis,
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
     */
    private suspend fun withHeartRate(workout: WorkoutEntity, maxHeartRate: Int?): WorkoutEntity {
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
