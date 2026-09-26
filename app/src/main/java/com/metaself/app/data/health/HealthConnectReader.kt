package com.metaself.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
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

    override suspend fun grantedKinds(): Set<HealthKind> = withContext(Dispatchers.IO) {
        val granted = runCatching { client?.permissionController?.getGrantedPermissions() }
            .getOrNull() ?: return@withContext emptySet()
        HealthKind.entries.filter { HealthPermissions.of(it) in granted }.toSet()
    }

    override suspend fun changesToken(kind: HealthKind): String = withContext(Dispatchers.IO) {
        connect().getChangesToken(ChangesTokenRequest(setOf(HealthPermissions.recordType(kind))))
    }

    override suspend fun changes(kind: HealthKind, token: String): ChangesPage = withContext(Dispatchers.IO) {
        val response = connect().getChanges(token)
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
    private suspend fun readAll(type: kotlin.reflect.KClass<out Record>, range: TimeRangeFilter): List<Record> {
        val out = mutableListOf<Record>()
        var page: String? = null
        do {
            @Suppress("UNCHECKED_CAST")
            val response = connect().readRecords(
                ReadRecordsRequest(recordType = type as kotlin.reflect.KClass<Record>, timeRangeFilter = range, pageToken = page),
            )
            out += response.records
            page = response.pageToken
        } while (page != null)
        return out
    }

    override suspend fun dayTotals(fromDay: Long, toDay: Long): Map<Long, DayTotals> = withContext(Dispatchers.IO) {
        val from = LocalDate.ofEpochDay(fromDay)
        val to = LocalDate.ofEpochDay(toDay)
        val steps = daily(StepsRecord.COUNT_TOTAL, "steps", from, to) { it.toInt() }
        val distance = daily(DistanceRecord.DISTANCE_TOTAL, "distance", from, to) { it.inMeters.roundToInt() }
        val active = daily(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, "active calories", from, to) {
            it.inKilocalories.roundToInt()
        }
        val total = daily(TotalCaloriesBurnedRecord.ENERGY_TOTAL, "total calories", from, to) {
            it.inKilocalories.roundToInt()
        }
        (steps.keys + distance.keys + active.keys + total.keys).associateWith { day ->
            DayTotals(steps[day], distance[day], active[day], total[day])
        }
    }

    private suspend fun <T : Any> daily(
        metric: AggregateMetric<T>,
        name: String,
        from: LocalDate,
        to: LocalDate,
        asInt: (T) -> Int,
    ): Map<Long, Int> = try {
        connect().aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(metric),
                timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), to.plusDays(1).atStartOfDay()),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).mapNotNull { bucket ->
            bucket.result[metric]?.let { bucket.startTime.toLocalDate().toEpochDay() to asInt(it) }
        }.toMap()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        problems.record("health", "totals of $name: ${failure::class.java.simpleName} ${failure.message}")
        emptyMap()
    }

    private suspend fun translate(records: List<Record>): List<ReadRecord> = records.mapNotNull { record ->
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
            is ExerciseSessionRecord -> session(record, origin, id)
            else -> null
        }
    }

    /**
     * Distance and energy over the session's own time, each its own call (D4: null when none). A
     * failed call leaves its figure null and is logged.
     *
     * These two aggregate calls are outside the copying's read budget: `HealthRecordSync` counts only
     * the change and window reads it makes itself. Accepted, because sessions are few.
     */
    private suspend fun session(record: ExerciseSessionRecord, origin: String, id: String): ReadRecord.Session {
        val range = TimeRangeFilter.between(record.startTime, record.endTime)
        val distance = sessionTotal(DistanceRecord.DISTANCE_TOTAL, "distance", range)
        val energy = sessionTotal(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, "active calories", range)
        return ReadRecord.Session(
            origin = origin,
            recordId = id,
            startMillis = record.startTime.toEpochMilli(),
            endMillis = record.endTime.toEpochMilli(),
            kind = WorkoutKinds.of(record.exerciseType),
            title = ExerciseNames.of(record.exerciseType, record.title),
            distanceM = distance?.inMeters?.roundToInt(),
            energyKcal = energy?.inKilocalories?.roundToInt(),
        )
    }

    private suspend fun <T : Any> sessionTotal(metric: AggregateMetric<T>, name: String, range: TimeRangeFilter): T? =
        try {
            connect().aggregate(AggregateRequest(setOf(metric), range))[metric]
        } catch (cancelled: CancellationException) {
            throw cancelled
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
