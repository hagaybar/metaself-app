package com.metaself.app.domain.movement

import java.time.Instant
import java.time.LocalDateTime

/**
 * What a workout file says (D82): the session's totals, never its second-by-second points.
 *
 * @property writtenAt the start's wall-clock time exactly as written, whatever zone marker followed
 *   it. A band's app may write local time with a `Z`, so the marker is not trusted.
 * @property instant the start as a true instant, when the file gave an offset or `Z`; null when it gave
 *   none. The matcher tries it beside [writtenAt].
 * @property seconds the laps' summed `TotalTimeSeconds`, rounded.
 * @property avgHeartRate read, and never stored: a session's heart rate is worked out from the
 *   readings (D70), and two averages would disagree.
 * @property sport the activity's `Sport` attribute as written, if any.
 */
data class FileWorkout(
    val writtenAt: LocalDateTime,
    val instant: Instant?,
    val seconds: Int,
    val distanceM: Double? = null,
    val kcal: Int? = null,
    val steps: Int? = null,
    val avgHeartRate: Int? = null,
    val sport: String? = null,
) {
    /** The file's distance in whole metres, as a workout stores it. */
    val metres: Int? get() = distanceM?.let { Math.round(it).toInt() }
}

/** Why a file was not used. Each is said in plain words on the Movement screen. */
enum class WorkoutFileRefusal {
    /** Not XML, or XML with a DOCTYPE (never read, so no entity is ever expanded). */
    NOT_XML,

    /** XML, but no TCX activity in it. */
    NO_WORKOUT,

    /** No start time that can be read. */
    NO_START,

    /** No lap time, or none above zero. */
    NO_DURATION,

    /** No distance, steps or calories: nothing a session could gain from it. */
    NOTHING_TO_ADD,

    /** Larger than a workout summary could be ([com.metaself.app.data.health.WorkoutFileSource]). */
    TOO_LARGE,

    /** The file could not be opened at all; logged (D8). */
    UNREADABLE,
}

/** What reading a file came to. */
sealed interface TcxRead {
    data class Read(val workout: FileWorkout) : TcxRead
    data class Refused(val reason: WorkoutFileRefusal) : TcxRead
}

/** The figures a fill put on a workout; null for each it did not. */
data class AddedFigures(val distanceM: Int? = null, val steps: Int? = null, val kcal: Int? = null) {
    val any: Boolean get() = distanceM != null || steps != null || kcal != null
}

/** What importing a file came to, for the Movement screen to say (D82). */
sealed interface ImportOutcome {
    data class Refused(val reason: WorkoutFileRefusal) : ImportOutcome

    /** [workout] as it now is; [added] what the file gave it; [fileDistanceM] the file's own distance. */
    data class Filled(val workout: Workout, val added: AddedFigures, val fileDistanceM: Int?) : ImportOutcome

    /** The matched workout already had everything the file could give. */
    data class Unchanged(val workout: Workout, val fileDistanceM: Int?) : ImportOutcome

    /** Nothing matches; the screen offers "Add it as a workout". */
    data class NoMatch(val file: FileWorkout) : ImportOutcome

    /** More than one workout matches; the screen lists them to choose from. */
    data class Several(val file: FileWorkout, val choices: List<Workout>) : ImportOutcome

    /** The file was added as a workout of its own. */
    data class AddedWorkout(val workout: Workout) : ImportOutcome

    /** A write failed; logged (D8). */
    data object Failed : ImportOutcome
}
