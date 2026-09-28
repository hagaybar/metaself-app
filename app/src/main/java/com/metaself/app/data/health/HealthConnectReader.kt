package com.metaself.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.movement.ExerciseNames
import com.metaself.app.domain.health.HealthKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Health Connect, translated into [ReadRecord]s (D65–D67). Thin on purpose: every decision is in
 * `HealthRecordSync`, where it is tested.
 *
 * Totals go through the aggregation API, one metric per call, for the two reasons `HealthConnectSteps`
 * records: aggregation is what de-duplicates across apps (D12c), and one missing permission must not
 * take the other totals with it.
 */
@Singleton
class HealthConnectReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val problems: ProblemLog,
) : HealthSource {

    private val client: HealthConnectClient?
        get() = runCatching {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        }.getOrNull()

    private fun connect(): HealthConnectClient =
        client ?: throw IllegalStateException("Health Connect is not available")

    /**
     * One call to Health Connect, with its refusal of a read from the background turned into a
     * [BackgroundReadRefused]: not a failure, so no caller here logs it, and `HealthRecordSync` stops
     * its pass on it. Every other exception is left as it was.
     */
    private inline fun <T> refusable(call: () -> T): T = try {
        call()
    } catch (refused: SecurityException) {
        throw BackgroundReadRefused.from(refused) ?: refused
    }

    override suspend fun grantedKinds(): Set<HealthKind> = withContext(Dispatchers.IO) {
        val granted = runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull() ?: return@withContext emptySet()
        HealthKind.entries.filter { HealthPermissions.of(it) in granted }.toSet()
    }

    /** A Health Connect too old to know the feature answers unavailable rather than failing. */
    override suspend fun historyAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            client?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            false
        }
    }

    override suspend fun historyGranted(available: Boolean): Boolean = withContext(Dispatchers.IO) {
        available && try {
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in
                (client?.permissionController?.getGrantedPermissions() ?: emptySet())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            false
        }
    }

    override suspend fun changesToken(kind: HealthKind): String = withContext(Dispatchers.IO) {
        refusable { connect().getChangesToken(ChangesTokenRequest(setOf(HealthPermissions.recordType(kind)))) }
    }

    override suspend fun changes(kind: HealthKind, token: String): ChangesPage = withContext(Dispatchers.IO) {
        val response = refusable { connect().getChanges(token) }
        val upserted = response.changes.filterIsInstance<UpsertionChange>().map { it.record }
        ChangesPage(
            upserts = translate(upserted),
            deletedIds = response.changes.filterIsInstance<DeletionChange>().map { it.recordId },
            nextToken = response.nextChangesToken,
            hasMore = response.hasMore,
            expired = response.changesTokenExpired,
        )
    }

    override suspend fun readWindow(kind: HealthKind, fromMillis: Long, toMillis: Long): List<ReadRecord> =
        withContext(Dispatchers.IO) {
            val range = TimeRangeFilter.between(Instant.ofEpochMilli(fromMillis), Instant.ofEpochMilli(toMillis))
            translate(readAll(HealthPermissions.recordType(kind), range))
        }

    /** Every page. One page is a thousand records, a few days of a busy series (the first steps bug). */
    private suspend fun <T : Record> readAll(type: kotlin.reflect.KClass<T>, range: TimeRangeFilter): List<T> {
        val out = mutableListOf<T>()
        var page: String? = null
        do {
            val response = refusable {
                connect().readRecords(ReadRecordsRequest(recordType = type, timeRangeFilter = range, pageToken = page))
            }
            out += response.records
            page = response.pageToken
        } while (page != null)
        return out
    }

    /**
     * Which of the four totals the owner has allowed, checked once per call. A metric not granted is
     * neither called nor logged (D8, the review of D66): a call Health Connect would refuse is not a
     * failure, and refusing it here keeps the figure already stored instead of clearing it.
     */
    override suspend fun dayTotals(fromDay: Long, toDay: Long): TotalsResult = withContext(Dispatchers.IO) {
        val from = LocalDate.ofEpochDay(fromDay)
        val to = LocalDate.ofEpochDay(toDay)
        val granted = grantedKinds()
        val steps = if (HealthKind.STEPS in granted) {
            daily(StepsRecord.COUNT_TOTAL, "steps", from, to) { it.toInt() }
        } else null
        val distance = if (HealthKind.DISTANCE in granted) {
            daily(DistanceRecord.DISTANCE_TOTAL, "distance", from, to) { it.inMeters.roundToInt() }
        } else null
        val active = if (HealthKind.ACTIVE_KCAL in granted) {
            daily(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, "active calories", from, to) {
                it.inKilocalories.roundToInt()
            }
        } else null
        val total = if (HealthKind.TOTAL_KCAL in granted) {
            daily(TotalCaloriesBurnedRecord.ENERGY_TOTAL, "total calories", from, to) {
                it.inKilocalories.roundToInt()
            }
        } else null
        val byMetric = mapOf(
            TotalMetric.STEPS to steps,
            TotalMetric.DISTANCE to distance,
            TotalMetric.ACTIVE_KCAL to active,
            TotalMetric.TOTAL_KCAL to total,
        )
        val days = byMetric.values.filterNotNull().flatMap { it.keys }.toSet()
        TotalsResult(
            byDay = days.associateWith { day ->
                DayTotals(steps?.get(day), distance?.get(day), active?.get(day), total?.get(day))
            },
            // Null either because the metric was not granted (never called, never logged) or because
            // its call failed (logged in `daily`); either way the stored figure stands.
            failed = byMetric.filterValues { it == null }.keys,
        )
    }

    /**
     * One metric's daily figures; empty when there was no data, null when the call failed (logged). A
     * refusal for the background is thrown, unlogged, so the copying stops rather than summarising a
     * day as if the metric had failed.
     */
    private suspend fun <T : Any> daily(
        metric: AggregateMetric<T>,
        name: String,
        from: LocalDate,
        to: LocalDate,
        asInt: (T) -> Int,
    ): Map<Long, Int>? = try {
        refusable {
            connect().aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(metric),
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
        }.mapNotNull { bucket ->
            bucket.result[metric]?.let { bucket.startTime.toLocalDate().toEpochDay() to asInt(it) }
        }.toMap()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (refused: BackgroundReadRefused) {
        throw refused
    } catch (failure: Exception) {
        problems.record("health", "totals of $name: ${failure::class.java.simpleName} ${failure.message}")
        null
    }

    /**
     * Granted permissions are checked once for the whole batch, not once per session (D8): a workout
     * list of any length costs one extra call, not one per row.
     */
    private suspend fun translate(records: List<Record>): List<ReadRecord> {
        val granted = if (records.any { it is ExerciseSessionRecord }) grantedKinds() else emptySet()
        return records.mapNotNull { record ->
            val origin = record.metadata.dataOrigin.packageName
            val id = record.metadata.id
            fun one(kind: HealthKind, at: Instant, value: Double) =
                ReadRecord.Reading(kind, origin, id, listOf(Sample(at.toEpochMilli(), null, value)))
            fun span(kind: HealthKind, start: Instant, end: Instant, value: Double) =
                ReadRecord.Reading(kind, origin, id, listOf(Sample(start.toEpochMilli(), end.toEpochMilli(), value)))
            when (record) {
                is StepsRecord -> span(HealthKind.STEPS, record.startTime, record.endTime, record.count.toDouble())
                is DistanceRecord -> span(HealthKind.DISTANCE, record.startTime, record.endTime, record.distance.inMeters)
                is ActiveCaloriesBurnedRecord ->
                    span(HealthKind.ACTIVE_KCAL, record.startTime, record.endTime, record.energy.inKilocalories)
                is TotalCaloriesBurnedRecord ->
                    span(HealthKind.TOTAL_KCAL, record.startTime, record.endTime, record.energy.inKilocalories)
                is HeartRateRecord -> ReadRecord.Reading(
                    HealthKind.HEART_RATE, origin, id,
                    record.samples.map { Sample(it.time.toEpochMilli(), null, it.beatsPerMinute.toDouble()) },
                )
                is RestingHeartRateRecord ->
                    one(HealthKind.RESTING_HEART_RATE, record.time, record.beatsPerMinute.toDouble())
                is HeartRateVariabilityRmssdRecord ->
                    one(HealthKind.HRV_RMSSD, record.time, record.heartRateVariabilityMillis)
                is OxygenSaturationRecord -> one(HealthKind.OXYGEN_SATURATION, record.time, record.percentage.value)
                is RespiratoryRateRecord -> one(HealthKind.RESPIRATORY_RATE, record.time, record.rate)
                is WeightRecord -> one(HealthKind.WEIGHT, record.time, record.weight.inKilograms)
                is BodyFatRecord -> one(HealthKind.BODY_FAT, record.time, record.percentage.value)
                is SleepSessionRecord -> ReadRecord.Night(
                    origin, id, record.startTime.toEpochMilli(), record.endTime.toEpochMilli(), record.title,
                    record.stages.map { StageSpan(stageName(it.stage), it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) },
                )
                is ExerciseSessionRecord -> session(record, origin, id, granted)
                else -> null
            }
        }
    }

    /**
     * Distance and energy over the session's own time, each its own call (D4: null when none), skipped
     * entirely — no call, no log — when that reading is not granted (D8). A failed call leaves its
     * figure null and is logged; a call refused for the background throws [BackgroundReadRefused], so the
 * session is not stored at all on that read rather than stored without the figure.
     *
     * An `ExerciseSessionRecord` carries no distance of its own in connect-client 1.1.0 (checked on the
     * pinned jar: only segments, laps with an optional length, and a route behind its own permission),
     * so these are the `DistanceRecord`s and `ActiveCaloriesBurnedRecord`s of every app over its time,
     * de-duplicated — unfiltered by origin, as a filter could only find the same or less. They are
     * whatever Health Connect holds at the moment of reading: a figure its app writes later is asked
     * for again by `HealthRecordSync` (D81's investigation).
     *
     * These aggregate calls are outside the copying's read budget: `HealthRecordSync` counts only the
     * change and window reads it makes itself. What bounds them: a session is read only when it changes
     * or when its week is read (a catch-up week, or the window re-read after an expired token), so these
     * are two calls per session so read — as many as that stretch holds, not capped here. The later
     * re-asks for a missing figure are capped separately (`HealthRecordSync.SESSION_ASKS_PER_OPEN`).
     */
    private suspend fun session(
        record: ExerciseSessionRecord,
        origin: String,
        id: String,
        granted: Set<HealthKind>,
    ): ReadRecord.Session {
        val totals = totalsOver(
            TimeRangeFilter.between(record.startTime, record.endTime),
            distance = HealthKind.DISTANCE in granted,
            energy = HealthKind.ACTIVE_KCAL in granted,
        )
        return ReadRecord.Session(
            origin = origin,
            recordId = id,
            startMillis = record.startTime.toEpochMilli(),
            endMillis = record.endTime.toEpochMilli(),
            kind = WorkoutKinds.of(record.exerciseType),
            title = ExerciseNames.of(record.exerciseType, record.title),
            distanceM = totals.distanceM,
            energyKcal = totals.energyKcal,
        )
    }

    override suspend fun sessionTotals(startMillis: Long, endMillis: Long, distance: Boolean, energy: Boolean): SessionTotals =
        withContext(Dispatchers.IO) {
            totalsOver(
                TimeRangeFilter.between(Instant.ofEpochMilli(startMillis), Instant.ofEpochMilli(endMillis)),
                distance,
                energy,
            )
        }

    private suspend fun totalsOver(range: TimeRangeFilter, distance: Boolean, energy: Boolean): SessionTotals =
        SessionTotals(
            distanceM = if (distance) {
                sessionTotal(DistanceRecord.DISTANCE_TOTAL, "distance", range)?.inMeters?.roundToInt()
            } else null,
            energyKcal = if (energy) {
                sessionTotal(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, "active calories", range)
                    ?.inKilocalories?.roundToInt()
            } else null,
        )

    /** One figure over a workout's time; null when the call failed (logged). A background refusal is thrown, unlogged. */
    private suspend fun <T : Any> sessionTotal(metric: AggregateMetric<T>, name: String, range: TimeRangeFilter): T? =
        try {
            refusable { connect().aggregate(AggregateRequest(setOf(metric), range)) }[metric]
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: BackgroundReadRefused) {
            throw refused
        } catch (failure: Exception) {
            problems.record("health", "workout $name: ${failure::class.java.simpleName} ${failure.message}")
            null
        }

    private fun stageName(stage: Int): String = when (stage) {
        SleepSessionRecord.STAGE_TYPE_AWAKE -> "AWAKE"
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "SLEEPING"
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "OUT_OF_BED"
        SleepSessionRecord.STAGE_TYPE_LIGHT -> "LIGHT"
        SleepSessionRecord.STAGE_TYPE_DEEP -> "DEEP"
        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "AWAKE_IN_BED"
        else -> "UNKNOWN"
    }
}
