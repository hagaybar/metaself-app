package com.metaself.app.data.health

import com.metaself.app.data.day.MetaSelfDatabase
import com.metaself.app.domain.health.Arrival
import com.metaself.app.domain.health.ArrivedWorkout
import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayCoverage
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.movement.WorkoutKind
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** What "What the band sends" reads (D80): the stored record only, never Health Connect. */
fun interface BandRecord {
    suspend fun report(fromDay: Long, toDay: Long): BandReport
}

/**
 * Six reads: the readings counted by their query; the window's nights, workouts and days; which of
 * the workouts their own app wrote a distance during; and whose walks do not count (D81). Not one transaction: a copy landing between two reads can make
 * one section a moment newer than another, which a page of counts can bear.
 */
class RoomBandRecord @Inject constructor(
    private val database: MetaSelfDatabase,
    private val walks: WalkChoices,
) : BandRecord {

    override suspend fun report(fromDay: Long, toDay: Long): BandReport {
        val readings = database.healthReadingDao().countsByDay(READING_KINDS, fromDay, toDay).mapNotNull { it.toArrival() }
        val nights = database.sleepDao().sessionsBetween(fromDay, toDay).map { it.toArrival() }
        val ownDistance = database.workoutDao().idsWithOwnDistance(fromDay, toDay).toSet()
        val workouts = database.workoutDao().observeBetween(fromDay, toDay).first()
            .map { it.toArrived(ownDistance = it.id in ownDistance) }
        val days = database.healthDayDao().observeBetween(fromDay, toDay).first().map { it.toCoverage() }
        return BandReport.of(fromDay, toDay, readings + nights, workouts, days, walks.uncounted.first())
    }

    private companion object {
        val READING_KINDS = HealthKind.entries.filter { it.isReading }.map { it.name }
    }
}

/** Null for a kind this version does not know. */
fun ReadingCount.toArrival(): Arrival? = HealthKind.parse(kind)?.let { Arrival(it, origin, epochDay, count) }

fun SleepSessionEntity.toArrival(): Arrival = Arrival(HealthKind.SLEEP, origin, epochDay, 1)

/** Anything but TYPED reads as copied, as `toWorkout` reads an unknown source. */
fun WorkoutEntity.toArrived(ownDistance: Boolean = false): ArrivedWorkout = ArrivedWorkout(
    epochDay = epochDay,
    kind = WorkoutKind.parse(kind),
    typed = source == "TYPED",
    origin = origin,
    hasDistance = distanceM != null,
    hasCalories = energyKcal != null && energySource == "BAND",
    hasHeartRate = avgHeartRate != null,
    hasTitle = !title.isNullOrBlank(),
    ownDistance = ownDistance,
)

fun HealthDayEntity.toCoverage(): DayCoverage = DayCoverage(
    epochDay,
    buildSet {
        if (steps != null) add(DayFigure.STEPS)
        if (distanceM != null) add(DayFigure.DISTANCE)
        if (activeKcal != null) add(DayFigure.ACTIVE_KCAL)
        if (totalKcal != null) add(DayFigure.TOTAL_KCAL)
        if (restingHeartRate != null) add(DayFigure.RESTING_HEART_RATE)
        if (hrvMs != null) add(DayFigure.HRV)
        if (oxygenPct != null) add(DayFigure.OXYGEN)
        if (respiratoryRate != null) add(DayFigure.BREATHING)
        if (sleepMinutes != null) add(DayFigure.SLEEP)
        if (workoutCount != null) add(DayFigure.WORKOUTS)
    },
)
