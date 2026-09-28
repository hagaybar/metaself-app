package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.AddedFigures
import com.metaself.app.domain.movement.ImportOutcome
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutFileRefusal
import java.time.Instant
import java.time.LocalDate
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
    private val WHEN_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
    private val DAY_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
    private const val SEP = " · "

    fun line(outcome: ImportOutcome, zone: ZoneId): String = when (outcome) {
        is ImportOutcome.Refused -> reason(outcome.reason)
        is ImportOutcome.Filled -> filled(outcome, zone)
        is ImportOutcome.Unchanged -> outcome.workout.distanceM
            ?.let { "This session already had ${distance(outcome.workout)}; nothing changed." }
            ?: "This session already had everything in the file; nothing changed."
        is ImportOutcome.NoMatch -> if (outcome.sameKind.isEmpty()) {
            "No workout matches this file (${outcome.file.writtenAt.format(WHEN)})."
        } else {
            "No session matches this file exactly (${outcome.file.writtenAt.format(WHEN)}). Is it one of these?"
        }
        is ImportOutcome.Several -> "Several workouts match this file; choose one."
        is ImportOutcome.AddedWorkout ->
            "Added ${MovementWeekWording.name(outcome.workout)}, ${start(outcome.workout, zone)}, from the file."
        ImportOutcome.Failed -> "The file could not be added; Recent problems says why."
    }

    /**
     * The problem log's line for [outcome] (kind "import", D82 as amended): one short line saying what
     * happened — a success in the words of a success. Which figures a fill added, never their values;
     * a stored workout by its name and day, and a file by the wall-clock time it gives, as the screen
     * says it. [today] decides whether a date needs its year, by [MovementWeekWording]'s rule — this
     * line outlives the screen (it sits in Recent problems), so a date read much later still says which
     * year it was.
     *
     * **Amended:** [WorkoutFileRefusal.UNREADABLE] no longer repeats the screen's "Recent problems says
     * why" — this line IS that entry, so pointing at itself said nothing; the failure it followed is
     * its own, separate log line (kind "workout file"), so this one is self-contained instead.
     */
    fun logLine(outcome: ImportOutcome, today: Long): String = when (outcome) {
        is ImportOutcome.Refused -> if (outcome.reason == WorkoutFileRefusal.UNREADABLE) {
            "refused: the file could not be opened"
        } else {
            "refused: " + reason(outcome.reason).removeSuffix(".").replaceFirstChar { it.lowercaseChar() }
        }
        is ImportOutcome.Filled -> "added ${figureNames(outcome.added)} to ${onDay(outcome.workout, today)}"
        is ImportOutcome.Unchanged -> "nothing new for ${onDay(outcome.workout, today)}"
        is ImportOutcome.NoMatch -> "no workout matches a file from ${whenOf(outcome.file.writtenAt, today)}"
        is ImportOutcome.Several -> "${outcome.choices.size} workouts match a file from ${whenOf(outcome.file.writtenAt, today)}"
        is ImportOutcome.AddedWorkout -> "added ${onDay(outcome.workout, today)} from a file"
        ImportOutcome.Failed -> "not added: a write failed"
    }

    private fun onDay(workout: Workout, today: Long): String =
        "${MovementWeekWording.name(workout)} on ${dayOf(workout.epochDay, today)}"

    private fun dayOf(epochDay: Long, today: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.format(if (date.year == LocalDate.ofEpochDay(today).year) DAY else DAY_WITH_YEAR)
    }

    private fun whenOf(writtenAt: LocalDateTime, today: Long): String =
        writtenAt.format(if (writtenAt.year == LocalDate.ofEpochDay(today).year) WHEN else WHEN_WITH_YEAR)

    private fun figureNames(added: AddedFigures): String = andList(
        listOfNotNull(
            added.distanceM?.let { "distance" },
            added.steps?.let { "steps" },
            added.kcal?.let { "calories" },
        ),
    )

    private fun andList(list: List<String>): String =
        if (list.size <= 1) list.joinToString() else list.dropLast(1).joinToString(", ") + " and " + list.last()

    /** D91: above "Add it as a workout", when sessions of the file's kind were offered first. */
    const val OR_ADD = "Or, if it is a session the record does not have:"

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
    ).let(::andList)

    /** A stored distance as the screen says it: two decimals when a file gave it. */
    private fun distance(workout: Workout): String {
        val metres = workout.distanceM ?: return ""
        return if (workout.distanceSource == WorkoutFigureSource.FILE) fileKm(metres) else MovementWeekWording.km(metres)
    }

    private fun start(workout: Workout, zone: ZoneId): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(workout.startedAtMillis), zone).format(WHEN)
}
