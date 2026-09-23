package com.metaself.app.data.movement

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.movement.DayMovement
import com.metaself.app.domain.movement.ExerciseSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Steps, out of Android's shared health store.
 *
 * Deliberately thin: open the store, ask for a range, add up each day. Everything that decides what
 * those steps are worth lives in the domain, where it can be tested without a phone.
 *
 * **Nothing here may throw upwards.** Health Connect can be absent, out of date, unpermitted, or
 * simply have nothing; all four are ordinary and none of them may stop the owner logging his lunch
 * (D8). Every one of them comes back as no data.
 */
@Singleton
class HealthConnectSteps @Inject constructor(
    @ApplicationContext private val context: Context,
    private val problems: ProblemLog,
) : StepSource {

    private val client: HealthConnectClient?
        get() = runCatching {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        }.getOrNull()

    override suspend fun access(): StepAccess = withContext(Dispatchers.IO) {
        val connect = client ?: return@withContext StepAccess.UNAVAILABLE
        val granted = runCatching {
            connect.permissionController.getGrantedPermissions()
        }.getOrNull() ?: return@withContext StepAccess.UNAVAILABLE

        // Steps alone are enough to be useful; the other two only add to what can be said. Holding
        // out for all three would turn a partly-granted permission into no feature at all.
        if (READ_STEPS in granted) StepAccess.GRANTED else StepAccess.NOT_PERMITTED
    }

    /**
     * A day's total, asked of Health Connect as a total.
     *
     * The first version of this read raw records and added them up, which was wrong in a way that
     * looked exactly like having no history: **`readRecords` returns ONE PAGE**, a thousand records
     * by default, and a phone writes a step record every few minutes. A month of walking is tens of
     * thousands of records, so a single page covered about four days and the app concluded it was
     * still learning. Following the page tokens would fix it; asking the aggregation API for daily
     * totals avoids the whole problem, is one call instead of thirty, and correctly attributes a
     * record that straddles midnight.
     *
     * Buckets with no data are dropped rather than reported as zero. That distinction is what lets
     * the normal-day rule tell a week with a flat phone apart from a week of sitting still.
     */
    /**
     * A day's totals, asked for in SEPARATE calls on purpose.
     *
     * The first version asked for steps and active calories in one aggregation, which was a quiet
     * disaster: an aggregation is all-or-nothing, so a phone that had not granted the active-calorie
     * permission — or a band that never publishes one — failed the whole call and **the step count
     * vanished with it.** One missing permission silently deleted a working feature.
     *
     * Two calls cost one extra round trip and mean that whatever can be read, is.
     *
     * **Both go through the AGGREGATION api, and that is a correctness requirement rather than a
     * convenience (D12c).** Health Connect deduplicates Activity data by the user's app-priority
     * list when aggregating, and does not when handing over raw records. A phone commonly has the
     * system's own step counter and a fitness-band app both writing steps; summing records would
     * count the same walk once per app, silently and for ever. Do not go back to readRecords here.
     */
    override suspend fun history(from: LocalDate, to: LocalDate): List<DayMovement> =
        withContext(Dispatchers.IO) {
            val steps = totalsFor(StepsRecord.COUNT_TOTAL, from, to, "steps") { it.toInt() }
            val active = totalsFor(
                ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                from,
                to,
                "active calories",
            ) { it.inKilocalories.roundToInt() }

            (steps.keys + active.keys).sorted().map { epochDay ->
                DayMovement(
                    epochDay = epochDay,
                    steps = steps[epochDay] ?: 0,
                    activeKcal = active[epochDay],
                )
            }.let { withSessions(it, from, to) }
        }

    /**
     * One metric, one call, and an empty answer for anything that cannot be read.
     *
     * A metric this phone will not give up is not an error the owner can act on: it is simply a
     * number the app will have to do without, and everything above here already copes with that.
     */
    private suspend fun <T : Any> totalsFor(
        metric: androidx.health.connect.client.aggregate.AggregateMetric<T>,
        from: LocalDate,
        to: LocalDate,
        name: String,
        asInt: (T) -> Int,
    ): Map<Long, Int> {
        val connect = client ?: return emptyMap()

        return runCatching {
            connect.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(metric),
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay(),
                    ),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).mapNotNull { bucket ->
                bucket.result[metric]?.let { value ->
                    bucket.startTime.toLocalDate().toEpochDay() to asInt(value)
                }
            }.toMap()
        }.onFailure { error ->
            problems.record(
                kind = "steps",
                detail = "could not read $name: " +
                    "${error::class.java.simpleName} ${error.message}",
            )
        }.getOrDefault(emptyMap())
    }

    private suspend fun withSessions(
        days: List<DayMovement>,
        from: LocalDate,
        to: LocalDate,
    ): List<DayMovement> {
        val connect = client ?: return days
        val zone = ZoneId.systemDefault()

        val byDay = runCatching {
            connect.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        from.atStartOfDay(zone).toInstant(),
                        to.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                ),
            ).records.groupBy { it.startTime.atZone(zone).toLocalDate().toEpochDay() }
        }.getOrNull() ?: return days

        return days.map { day ->
            // Two apps recording the same walk write it twice, and unlike steps and calories the
            // sessions are NOT deduplicated by Health Connect. Same name, same length, same day is
            // one walk however many apps noticed it. Cosmetic — sessions are shown, never counted —
            // but a day listing "Walking · 45 min" twice is still wrong.
            val sessions = byDay[day.epochDay].orEmpty().map { record ->
                ExerciseSession(
                    name = ExerciseNames.of(record.exerciseType, record.title),
                    minutes = Duration.between(record.startTime, record.endTime)
                        .toMinutes()
                        .toInt(),
                )
            }.distinct()
            if (sessions.isEmpty()) day else day.copy(sessions = sessions)
        }
    }

    companion object {
        /**
         * Everything this app asks Health Connect for, and nothing more.
         *
         * Steps and active calories are what the arithmetic needs; exercise sessions are what the
         * screen needs in order to say "Swimming" rather than "movement". No heart rate, no sleep,
         * no weight — asking for what cannot be used is how permission screens become things people
         * dismiss without reading.
         */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

        val READ_STEPS: String = HealthPermission.getReadPermission(StepsRecord::class)
    }
}
