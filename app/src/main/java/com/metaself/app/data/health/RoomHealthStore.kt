package com.metaself.app.data.health

import com.metaself.app.data.day.DatabaseTransaction
import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.data.profile.ProfileRepository
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
 * `hidden` and `note`.
 */
class RoomHealthStore @Inject constructor(
    private val database: MetaSelfDatabase,
    private val transaction: DatabaseTransaction,
    private val rows: HealthRows,
    private val profiles: ProfileRepository,
    private val today: Today,
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
            deletedIds.forEach { id ->
                touched += readingDao.daysOf(id)
                touched += sleepDao.daysOf(id)
                touched += workoutDao.daysOfSynced(id)
                readingDao.deleteByRecordId(id)
                sleepDao.deleteByRecordId(id)
                workoutDao.deleteSynced(id)
            }
            records.forEach { record -> touched += store(record) }
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
                }
                HealthKind.EXERCISE -> {
                    touched += workoutDao.daysOfSyncedBetween(fromMillis, toMillis)
                    workoutDao.deleteSyncedBetween(fromMillis, toMillis)
                }
                else -> {
                    touched += readingDao.daysOfKindBetween(kind.name, fromMillis, toMillis)
                    readingDao.deleteKindBetween(kind.name, fromMillis, toMillis)
                }
            }
            records.forEach { record -> touched += store(record) }
        }
        return touched
    }

    /** One record, replacing its earlier rows. Inside a transaction the caller holds. */
    private suspend fun store(record: ReadRecord): Set<Long> = when (record) {
        is ReadRecord.Reading -> {
            val before = readingDao.daysOf(record.recordId)
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

    override suspend fun summarise(days: Set<Long>, totals: Map<Long, DayTotals>, nowMillis: Long) {
        val maxHeartRate = profiles.profile.first()
            ?.let { HeartRateZones.estimatedMax(it.birthYear, today().year) }
        days.sorted().forEach { epochDay ->
            transaction.run {
                val dayWorkouts = workoutDao.onDay(epochDay).map { withHeartRate(it, maxHeartRate) }
                val summary = DaySummary.of(
                    epochDay = epochDay,
                    totals = keepingStored(totals[epochDay] ?: DayTotals(), dayDao.day(epochDay)),
                    readings = readingDao.onDay(epochDay),
                    nights = sleepDao.nightsOn(epochDay),
                    workouts = dayWorkouts,
                    correction = correctionDao.day(epochDay),
                    nowMillis = nowMillis,
                )
                if (summary == null) dayDao.delete(epochDay) else dayDao.put(summary)
                markMonth(epochDay, nowMillis)
            }
        }
    }

    /**
     * A total Health Connect did not give this time — a failed call, a withdrawn permission — keeps
     * the one already stored, when that one was Health Connect's. A total is never zeroed by a miss.
     */
    private fun keepingStored(given: DayTotals, stored: HealthDayEntity?): DayTotals = DayTotals(
        steps = given.steps ?: stored?.steps?.takeIf { stored.stepsSource == "TOTAL" },
        distanceM = given.distanceM ?: stored?.distanceM?.takeIf { stored.distanceSource == "TOTAL" },
        activeKcal = given.activeKcal ?: stored?.activeKcal?.takeIf { stored.activeKcalSource == "TOTAL" },
        totalKcal = given.totalKcal ?: stored?.totalKcal?.takeIf { stored.totalKcalSource == "TOTAL" },
    )

    /** A workout's heart-rate figures from the readings inside it (D70); unchanged when none. */
    private suspend fun withHeartRate(workout: WorkoutEntity, maxHeartRate: Int?): WorkoutEntity {
        if (maxHeartRate == null) return workout
        val end = workout.startedAtMillis + workout.durationMinutes * 60_000L
        val samples = readingDao.ofKindBetween(HealthKind.HEART_RATE.name, workout.startedAtMillis, end)
            .map { it.startMillis to it.value }
        val figures = HeartRateZones.of(samples, end, maxHeartRate) ?: return workout
        val updated = workout.copy(
            avgHeartRate = figures.average,
            maxHeartRate = figures.maximum,
            zoneSeconds = figures.zonesAsText(),
            zoneMaxSource = "ESTIMATED",
        )
        if (updated != workout) workoutDao.update(updated)
        return updated
    }

    /** The day's Drive month file is out of date (D71); when it was last written is kept. */
    private suspend fun markMonth(epochDay: Long, nowMillis: Long) {
        val month = LocalDate.ofEpochDay(epochDay).toString().substring(0, 7)
        val known = bookkeepingDao.month(month)
        bookkeepingDao.putMonth(
            ArchiveMonthEntity(month = month, changedAtMillis = nowMillis, writtenAtMillis = known?.writtenAtMillis),
        )
    }
}
