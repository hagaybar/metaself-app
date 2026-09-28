package com.metaself.app.domain.movement

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Which stored workout a workout file belongs to, and what it adds (D82). Pure.
 *
 * **The file's zone is not trusted.** A band's app may write local wall-clock time with a `Z`, so the
 * start is tried two ways: the wall clock as written, taken in the phone's zone; and, when the file
 * gave a marker, that marker taken as true. A workout matches when either reading is within
 * [START_MINUTES] of its start and falls on its stored local day, and the file's duration is within
 * [DURATION_SHARE] of its stored minutes (never less than a minute, as those are rounded).
 */
object WorkoutFileMatch {

    /** A choice, not a measurement: how far apart a file's start and a session's may be. */
    const val START_MINUTES = 10L

    /** A choice, not a measurement: how far apart a file's duration and a session's may be, as a share. */
    const val DURATION_SHARE = 0.10

    /** A fill's result: the workout as it now is, and what the file gave it. */
    data class Filling(val workout: Workout, val added: AddedFigures)

    /** The local days either reading of the file's start falls on. */
    fun days(file: FileWorkout, zone: ZoneId): Set<Long> =
        starts(file, zone).mapTo(mutableSetOf()) { it.atZone(zone).toLocalDate().toEpochDay() }

    /**
     * What a file may fill in: a visible synced session, or a typed workout a file made (so the same
     * file imported again finds it). A hidden session was hidden on purpose; a workout the owner typed
     * is his own.
     */
    fun candidates(stored: List<Workout>): List<Workout> =
        stored.filter { !it.hidden && (it.source == WorkoutSource.SYNCED || it.fromFile) }

    /**
     * D91: when [file] matches nothing, the sessions it might still be — those it may fill
     * ([candidates]) of its kind ([kindOf] its sport, as "Add it as a workout" reads it) on a day either
     * reading of its start falls on ([days]) — by start.
     */
    fun sameKind(file: FileWorkout, stored: List<Workout>, zone: ZoneId): List<Workout> {
        val days = days(file, zone)
        val kind = kindOf(file.sport)
        return candidates(stored).filter { it.kind == kind && it.epochDay in days }.sortedBy { it.startedAtMillis }
    }

    fun matches(file: FileWorkout, stored: List<Workout>, zone: ZoneId): List<Workout> {
        val starts = starts(file, zone)
        return candidates(stored).filter { workout ->
            durationFits(file.seconds, workout.durationMinutes) && starts.any { start ->
                abs(workout.startedAtMillis - start.toEpochMilli()) <= START_MINUTES * MINUTE &&
                    start.atZone(zone).toLocalDate().toEpochDay() == workout.epochDay
            }
        }
    }

    /**
     * Only what [workout] lacks, each with its source FILE (D4): a distance when it has none, steps
     * when it has none, calories when it has none at all (energy source NONE). A figure already there
     * — a band's, the owner's, an estimate — is never replaced.
     */
    fun fill(workout: Workout, file: FileWorkout): Filling {
        val metres = file.metres?.takeIf { workout.distanceM == null }
        val steps = file.steps?.takeIf { workout.steps == null }
        val kcal = file.kcal?.takeIf { workout.energyKcal == null && workout.energySource == EnergySource.NONE }
        val filled = workout.copy(
            distanceM = workout.distanceM ?: metres,
            distanceSource = if (metres != null) WorkoutFigureSource.FILE else workout.distanceSource,
            steps = workout.steps ?: steps,
            stepsSource = if (steps != null) WorkoutFigureSource.FILE else workout.stepsSource,
            energyKcal = workout.energyKcal ?: kcal,
            energySource = if (kcal != null) EnergySource.FILE else workout.energySource,
        )
        return Filling(filled, AddedFigures(distanceM = metres, steps = steps, kcal = kcal))
    }

    /**
     * "Add it as a workout": a typed workout at the file's wall clock in the phone's zone, its minutes
     * rounded (at least one), its kind from the file's sport, and every figure the file has, source
     * FILE. No effort: nothing was felt, so none is claimed.
     */
    fun asWorkout(file: FileWorkout, zone: ZoneId): Workout {
        val start = file.writtenAt.atZone(zone)
        return Workout(
            id = 0,
            epochDay = start.toLocalDate().toEpochDay(),
            startedAtMillis = start.toInstant().toEpochMilli(),
            durationMinutes = max(1, (file.seconds / 60.0).roundToInt()),
            kind = kindOf(file.sport),
            title = null,
            distanceM = file.metres,
            energyKcal = file.kcal,
            energySource = if (file.kcal != null) EnergySource.FILE else EnergySource.NONE,
            effort = null,
            source = WorkoutSource.TYPED,
            hidden = false,
            note = null,
            distanceSource = file.metres?.let { WorkoutFigureSource.FILE },
            steps = file.steps,
            stepsSource = file.steps?.let { WorkoutFigureSource.FILE },
        )
    }

    /** TCX names Running, Biking and Other; a band's app may write others. */
    fun kindOf(sport: String?): WorkoutKind = when (sport?.trim()?.lowercase()) {
        "running", "run" -> WorkoutKind.RUN
        "biking", "cycling", "bike" -> WorkoutKind.CYCLE
        "walking", "walk" -> WorkoutKind.WALK
        "swimming", "swim" -> WorkoutKind.SWIM
        else -> WorkoutKind.OTHER
    }

    private fun starts(file: FileWorkout, zone: ZoneId): List<Instant> =
        listOfNotNull(file.writtenAt.atZone(zone).toInstant(), file.instant).distinct()

    private fun durationFits(seconds: Int, minutes: Int): Boolean =
        abs(seconds / 60.0 - minutes) <= max(DURATION_SHARE * minutes, 1.0)

    private const val MINUTE = 60_000L
}
