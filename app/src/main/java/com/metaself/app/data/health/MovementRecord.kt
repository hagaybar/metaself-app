package com.metaself.app.data.health

import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What the Movement screen reads from the health record (D73–D75), observed, so a copy that lands
 * while the screen is open shows at once. Nothing here reads Health Connect.
 */
interface MovementRecord {
    /** The daily summaries of [from]..[to], inclusive. */
    fun observeDays(from: Long, to: Long): Flow<List<HealthDay>>

    /** The workouts of [from]..[to], hidden ones included; the week leaves those out. */
    fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>>
}

/** Over the two DAO reads that already exist; no new query (the plan's red line). */
class RoomMovementRecord @Inject constructor(
    private val days: HealthDayDao,
    private val workouts: WorkoutDao,
) : MovementRecord {

    override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
        days.observeBetween(from, to).map { rows -> rows.map { it.toHealthDay() } }

    override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> =
        workouts.observeBetween(from, to).map { rows -> rows.map { it.toWorkout() } }
}

fun HealthDayEntity.toHealthDay(): HealthDay = HealthDay(
    epochDay = epochDay,
    steps = steps,
    stepsSource = FigureSource.parse(stepsSource),
    distanceM = distanceM,
    activeKcal = activeKcal,
    activeKcalSource = FigureSource.parse(activeKcalSource),
    restingHeartRate = restingHeartRate,
    hrvMs = hrvMs,
    oxygenPct = oxygenPct,
    respiratoryRate = respiratoryRate,
    sleepMinutes = sleepMinutes,
    deepMinutes = deepMinutes,
    remMinutes = remMinutes,
    lightMinutes = lightMinutes,
)

/**
 * A stored workout as the rest of the app reasons about it. A name this version does not know never
 * breaks the read: an unknown kind is UNRECOGNISED, an unknown effort none. An unknown energy source
 * reads as NONE and an unknown source as SYNCED — the Movement screen shows neither, and SYNCED is
 * the side that cannot be deleted by hand.
 */
fun WorkoutEntity.toWorkout(): Workout = Workout(
    id = id,
    epochDay = epochDay,
    startedAtMillis = startedAtMillis,
    durationMinutes = durationMinutes,
    kind = WorkoutKind.parse(kind),
    title = title,
    distanceM = distanceM,
    energyKcal = energyKcal,
    energySource = EnergySource.entries.firstOrNull { it.name == energySource } ?: EnergySource.NONE,
    effort = Effort.entries.firstOrNull { it.name == effort },
    source = WorkoutSource.entries.firstOrNull { it.name == source } ?: WorkoutSource.SYNCED,
    hidden = hidden,
    note = note,
    avgHeartRate = avgHeartRate,
)

/** A typed workout as it is stored (D76): no origin, `source = TYPED`, every enum by its name. */
fun Workout.toTypedEntity(): WorkoutEntity = WorkoutEntity(
    id = id,
    epochDay = epochDay,
    startedAtMillis = startedAtMillis,
    durationMinutes = durationMinutes,
    kind = kind.name,
    title = title,
    distanceM = distanceM,
    energyKcal = energyKcal,
    energySource = energySource.name,
    effort = effort?.name,
    source = WorkoutSource.TYPED.name,
    origin = null,
    originId = null,
    hidden = hidden,
    note = note,
)
