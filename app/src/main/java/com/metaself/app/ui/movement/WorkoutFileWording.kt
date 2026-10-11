package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.AddedFigures
import com.metaself.app.domain.movement.FileWorkout
import com.metaself.app.domain.movement.FilledLater
import com.metaself.app.domain.movement.WorkoutKind
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

    /**
     * A file's time is its wall clock as written, which is the phone's-zone reading "Add it as a
     * workout" stores; a session's is its start in the phone's zone ([choice]). So after no match
     * (D91) the header's time and the offered sessions' times compare directly.
     */
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
        is ImportOutcome.Waiting -> WAITING
        is ImportOutcome.AddedWorkout ->
            "Added ${MovementWeekWording.name(outcome.workout)}, ${start(outcome.workout, zone)}, from the file."
        ImportOutcome.Failed -> "The file could not be added; Recent problems says why."
    }

    /**
     * The problem log's line for [outcome] (kind "import"), or null when it is not a problem (D139): a file filled,
     * nothing new, no match, several, added as a workout, or waiting is not written — Recent problems holds problems.
     * A refused file and a failed write are. A file that waited out its days is written by [neverFound].
     *
     * [WorkoutFileRefusal.UNREADABLE] does not repeat the screen's "Recent problems says why" — this line IS that
     * entry; the failure it followed is its own, separate line (kind "workout file").
     */
    fun logLine(outcome: ImportOutcome): String? = when (outcome) {
        is ImportOutcome.Refused -> if (outcome.reason == WorkoutFileRefusal.UNREADABLE) {
            "refused: the file could not be opened"
        } else {
            "refused: " + reason(outcome.reason).removeSuffix(".").replaceFirstChar { it.lowercaseChar() }
        }
        ImportOutcome.Failed -> "not added: a write failed"
        is ImportOutcome.Filled, is ImportOutcome.Unchanged, is ImportOutcome.NoMatch, is ImportOutcome.Several,
        is ImportOutcome.AddedWorkout, is ImportOutcome.Waiting -> null
    }

    /** D138: the problem log's line for a file that waited out its days. */
    fun neverFound(file: FileWorkout, today: Long): String = "a file from ${whenOf(file.writtenAt, today)} never found its session"

    private fun dayOf(epochDay: Long, today: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.format(if (date.year == LocalDate.ofEpochDay(today).year) DAY else DAY_WITH_YEAR)
    }

    private fun whenOf(writtenAt: LocalDateTime, today: Long): String =
        writtenAt.format(if (writtenAt.year == LocalDate.ofEpochDay(today).year) WHEN else WHEN_WITH_YEAR)

    private fun andList(list: List<String>): String =
        if (list.size <= 1) list.joinToString() else list.dropLast(1).joinToString(", ") + " and " + list.last()

    /** D138: the outcome's line when the file waits for its session. */
    const val WAITING = "The band's session for this file hasn't reached the phone yet. It will be filled in when it does."

    /** D138: a waiting file's line on Your movement. */
    fun waiting(file: FileWorkout, today: Long): String = "A file from ${whenOf(file.writtenAt, today)} is waiting for the band's session"

    /** D138: under the first three waiting files, the rest. */
    fun andMore(count: Int): String = "and $count more"

    /** D138: said once, the next time Your movement opens, for a file that filled its session in the background. */
    fun filledLater(filled: FilledLater, today: Long): String =
        "Filled in your ${kindNoun(filled.kind)} of ${dayOf(filled.epochDay, today)} from the file that was waiting."

    /** A session of [kind], as "your …" names it. */
    private fun kindNoun(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.WALK -> "walk"
        WorkoutKind.RUN -> "run"
        WorkoutKind.CYCLE -> "ride"
        WorkoutKind.SWIM -> "swim"
        WorkoutKind.STRENGTH -> "weights session"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "workout"
    }

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
