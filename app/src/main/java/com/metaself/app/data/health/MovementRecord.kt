package com.metaself.app.data.health

import com.metaself.app.domain.health.HeartRateZones
import com.metaself.app.domain.movement.CountedWorkouts
import com.metaself.app.domain.movement.Effort
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * What the Movement screen reads from the health record (D73–D75), observed, so a copy that lands
 * while the screen is open shows at once. Nothing here reads Health Connect.
 */
interface MovementRecord {
    /** The daily summaries of [from]..[to], inclusive. */
    fun observeDays(from: Long, to: Long): Flow<List<HealthDay>>

    /**
     * The workouts of [from]..[to], hidden ones and walks that do not count (D81) included, each
     * marked; the week leaves those out. Observed together with the choice, so switching an app
     * off shows at once.
     */
    fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>>

    /**
     * The first day the record holds anything for — a daily summary or a workout, hidden or not —
     * or null when it holds nothing. How far back the screen can step (D83).
     */
    fun observeEarliestDay(): Flow<Long?>
}

/**
 * Over the DAOs' reads. The earliest workout is the one query of its own (D83), added without a
 * schema change; the rest already existed.
 */
class RoomMovementRecord @Inject constructor(
    private val days: HealthDayDao,
    private val workouts: WorkoutDao,
    private val walks: WalkChoices,
) : MovementRecord {

    override fun observeDays(from: Long, to: Long): Flow<List<HealthDay>> =
        days.observeBetween(from, to).map { rows -> rows.map { it.toHealthDay() } }

    override fun observeWorkouts(from: Long, to: Long): Flow<List<Workout>> =
        combine(workouts.observeBetween(from, to), walks.uncounted) { rows, uncounted ->
            rows.map { it.toWorkout(uncounted) }
        }

    override fun observeEarliestDay(): Flow<Long?> =
        combine(days.observeEarliest(), workouts.observeEarliest()) { summary, workout ->
            listOfNotNull(summary, workout).minOrNull()
        }
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
 * the side that cannot be deleted by hand; an unknown figure source reads as Health Connect's (null).
 * [uncountedWalkApps] marks a walk from a switched-off app
 * as not counted (D81).
 */
fun WorkoutEntity.toWorkout(uncountedWalkApps: Set<String> = emptySet()): Workout = Workout(
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
    counted = CountedWorkouts.counts(WorkoutKind.parse(kind), origin, uncountedWalkApps),
    distanceSource = WorkoutFigureSource.parse(distanceSource),
    steps = steps,
    stepsSource = WorkoutFigureSource.parse(stepsSource),
    maxHeartRate = maxHeartRate,
    zoneSeconds = HeartRateZones.zonesFromText(zoneSeconds),
    zoneMaxSource = zoneMaxSource,
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
    distanceSource = distanceSource?.name,
    steps = steps,
    stepsSource = stepsSource?.name,
)
