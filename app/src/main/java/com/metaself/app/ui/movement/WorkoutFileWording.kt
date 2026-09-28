package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.AddedFigures
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutFileRefusal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the Movement screen says after a workout file (D82): one line per outcome, and one row per
 * workout to choose from. A file's distance is said to two decimals (it is metre-true); a distance
 * already stored is said as the rest of the screen says it.
 */
object WorkoutFileWording {

    /** [Locale.US]: Java 17's UK data writes "Sept" (see [MovementWeekWording]). */
    private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private const val SEP = " · "

    fun line(outcome: ImportOutcome, zone: ZoneId): String = when (outcome) {
        is ImportOutcome.Refused -> reason(outcome.reason)
        is ImportOutcome.Filled -> filled(outcome, zone)
        is ImportOutcome.Unchanged -> outcome.workout.distanceM
            ?.let { "This session already had ${distance(outcome.workout)}; nothing changed." }
            ?: "This session already had everything in the file; nothing changed."
        is ImportOutcome.NoMatch -> "No workout matches this file (${outcome.file.writtenAt.format(WHEN)})."
        is ImportOutcome.Several -> "Several workouts match this file; choose one."
        is ImportOutcome.AddedWorkout ->
            "Added ${MovementWeekWording.name(outcome.workout)}, ${start(outcome.workout, zone)}, from the file."
        ImportOutcome.Failed -> "The file could not be added; Recent problems says why."
    }

    /** "Walking · 10:00 · 40 min". */
    fun choice(workout: Workout, zone: ZoneId): String =
        MovementWeekWording.name(workout) + SEP +
            Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).format(TIME) + SEP +
            MovementWeekWording.duration(workout.durationMinutes)

    fun reason(refusal: WorkoutFileRefusal): String = when (refusal) {
        WorkoutFileRefusal.NOT_XML -> "This is not a workout file MetaSelf can read."
        WorkoutFileRefusal.NO_WORKOUT -> "The file holds no workout."
        WorkoutFileRefusal.NO_START -> "The file does not say when the workout started."
        WorkoutFileRefusal.NO_DURATION -> "The file does not say how long the workout lasted."
        WorkoutFileRefusal.NOTHING_TO_ADD -> "The file has no distance, steps or calories to add."
        WorkoutFileRefusal.TOO_LARGE -> "The file is too large to be a workout summary."
        WorkoutFileRefusal.UNREADABLE -> "The file could not be opened; Recent problems says why."
    }

    /** "3.25 km": a figure from a file, to the metre's second decimal. */
    fun fileKm(metres: Int): String = String.format(Locale.US, "%,.2f km", metres / 1000.0)

    private fun filled(outcome: ImportOutcome.Filled, zone: ZoneId): String {
        val workout = outcome.workout
        val first = "Added ${parts(outcome.added)} to ${MovementWeekWording.name(workout)}, ${start(workout, zone)}."
        val fileMetres = outcome.fileDistanceM
        val kept = workout.distanceM
        return if (outcome.added.distanceM == null && fileMetres != null && kept != null) {
            "$first It already had ${distance(workout)}; the file says ${fileKm(fileMetres)}."
        } else {
            first
        }
    }

    private fun parts(added: AddedFigures): String = listOfNotNull(
        added.distanceM?.let(::fileKm),
        added.steps?.let { String.format(Locale.US, "%,d steps", it) },
        added.kcal?.let { String.format(Locale.US, "%,d kcal", it) },
    ).let { list -> if (list.size <= 1) list.joinToString() else list.dropLast(1).joinToString(", ") + " and " + list.last() }

    /** A stored distance as the screen says it: two decimals when a file gave it. */
    private fun distance(workout: Workout): String {
        val metres = workout.distanceM ?: return ""
        return if (workout.distanceSource == WorkoutFigureSource.FILE) fileKm(metres) else MovementWeekWording.km(metres)
    }

    private fun start(workout: Workout, zone: ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(workout.startedAtMillis), zone).format(WHEN)
}
