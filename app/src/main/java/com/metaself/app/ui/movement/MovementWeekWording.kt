package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the Movement screen says (D73, D74).
 *
 * Every part is said only when it was recorded: a missing figure is left out, never written as zero
 * (D4, D69). Movement calories are shown as themselves — never a total burn, never eaten minus
 * burned (D63).
 */
object MovementWeekWording {

    /**
     * [Locale.US], not the UK locale [com.metaself.app.ui.day.DayWording] uses: Java 17's CLDR data
     * abbreviates September as "Sept" for en-GB, and the design writes "Sep".
     */
    private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    private const val SEP = " · "

    const val NOTHING = "nothing recorded"

    /** "THIS WEEK · FROM MON 31 AUG". Set in capitals here: these are the app's words, not the owner's. */
    fun kicker(monday: Long): String =
        "THIS WEEK · FROM " + LocalDate.ofEpochDay(monday).format(SHORT_DATE).uppercase(Locale.US)

    /** The one large figure (D74): "42.6 km". */
    fun distance(week: MovementWeek): String? = week.distanceM?.let(::km)

    fun averageMovement(week: MovementWeek): String? =
        week.averageActiveKcal?.let { "${number(it)} kcal of movement a day, on average" }

    /** "4 workouts · 2 h 21"; null with none. */
    fun workouts(week: MovementWeek): String? {
        if (week.workoutCount == 0) return null
        val count = if (week.workoutCount == 1) "1 workout" else "${week.workoutCount} workouts"
        return count + SEP + duration(week.workoutMinutes)
    }

    /** "Thu 3 Sep". No year: the screen only ever shows this week. */
    fun dayHeading(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(SHORT_DATE)

    /**
     * A day's one-line summary (D73), shown whether the day is open or closed: up to three parts —
     * movement calories, workouts, sleep. With none of them, the first line the open day would show,
     * so a day with steps is never called empty; "nothing recorded" only when there is nothing at all.
     */
    fun summaryLine(day: MovementDay): String {
        val health = day.health
        val parts = listOfNotNull(
            health?.activeKcal?.let { "${number(it)} kcal" },
            day.workouts.takeIf { it.isNotEmpty() }?.joinToString(", ") { workout ->
                name(workout) + " " + (workout.distanceM?.let(::km) ?: duration(workout.durationMinutes))
            },
            health?.sleepMinutes?.let { "slept ${duration(it)}" },
        )
        return if (parts.isNotEmpty()) parts.joinToString(SEP) else partLines(day).firstOrNull() ?: NOTHING
    }

    /**
     * What an open day shows beneath its summary (D73): one line per part, each only when recorded.
     * A line the summary already says is not repeated, so a day with nothing but steps, or nothing at
     * all, has nothing beneath.
     */
    fun detailLines(day: MovementDay): List<String> {
        val summary = summaryLine(day)
        return partLines(day).filterNot { it == summary }
    }

    /** "Last four weeks: 38.1 · 45.0 · — · 44.7 km"; null when none of the four has a distance. */
    fun lastFourWeeks(week: MovementWeek): String? {
        if (week.previousWeeksM.all { it == null }) return null
        return "Last four weeks: " +
            week.previousWeeksM.joinToString(SEP) { metres -> metres?.let(::kmFigure) ?: "—" } +
            " km"
    }

    /** The recording app's name for a session, or the kind's, as `ExerciseNames` names them. */
    fun name(workout: Workout): String = workout.title?.takeIf { it.isNotBlank() } ?: when (workout.kind) {
        WorkoutKind.RUN -> "Running"
        WorkoutKind.WALK -> "Walking"
        WorkoutKind.CYCLE -> "Cycling"
        WorkoutKind.SWIM -> "Swimming"
        WorkoutKind.STRENGTH -> "Weights"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "Exercise"
    }

    fun km(metres: Int): String = kmFigure(metres) + " km"

    /** "45 min", "1 h", "7 h 10". */
    fun duration(minutes: Int): String {
        if (minutes < 60) return "$minutes min"
        val rest = minutes % 60
        return if (rest == 0) "${minutes / 60} h" else String.format(Locale.US, "%d h %02d", minutes / 60, rest)
    }

    /** "5:10 /km". */
    fun pace(secondsPerKm: Int): String =
        String.format(Locale.US, "%d:%02d /km", secondsPerKm / 60, secondsPerKm % 60)

    /** One line per recorded part of the day, in the order an open day shows them. */
    private fun partLines(day: MovementDay): List<String> {
        val health = day.health
        val movement = if (health == null) {
            emptyList()
        } else {
            listOfNotNull(
                health.activeKcal?.let { withSource("${number(it)} kcal of movement", health.activeKcalSource) },
                health.steps?.let { withSource("${number(it)} steps", health.stepsSource) },
            )
        }
        val rest = listOfNotNull(
            health?.let(::sleepLine),
            health?.let(::bodyLine),
            day.eatenKcal?.let { "${number(it)} kcal eaten" },
        )
        return movement + day.workouts.map(::workoutLine) + rest
    }

    private fun workoutLine(workout: Workout): String = listOfNotNull(
        name(workout),
        workout.distanceM?.let(::km),
        duration(workout.durationMinutes),
        workout.paceSecondsPerKm?.let(::pace),
        workout.avgHeartRate?.let { "avg $it bpm" },
    ).joinToString(SEP)

    private fun sleepLine(health: HealthDay): String? {
        val total = health.sleepMinutes ?: return null
        val stages = listOfNotNull(
            health.deepMinutes?.let { "deep ${duration(it)}" },
            health.remMinutes?.let { "REM ${duration(it)}" },
            health.lightMinutes?.let { "light ${duration(it)}" },
        )
        val slept = "Slept ${duration(total)}"
        return if (stages.isEmpty()) slept else slept + " — " + stages.joinToString(SEP)
    }

    private fun bodyLine(health: HealthDay): String? = listOfNotNull(
        health.restingHeartRate?.let { "Resting $it" },
        health.hrvMs?.let { "HRV ${it.roundToInt()} ms" },
        health.oxygenPct?.let { "oxygen ${it.roundToInt()}%" },
        health.respiratoryRate?.let { "breathing ${it.roundToInt()}/min" },
    ).takeIf { it.isNotEmpty() }?.joinToString(SEP)

    /** D4: a figure says where it came from. TOTAL and CORRECTED are the two a summary figure can be. */
    private fun withSource(text: String, source: FigureSource?): String = when (source) {
        FigureSource.TOTAL -> text + SEP + "phone and band"
        FigureSource.CORRECTED -> text + SEP + "you set this"
        else -> text
    }

    private fun kmFigure(metres: Int): String = String.format(Locale.US, "%,.1f", metres / 1000.0)

    private fun number(value: Int): String = String.format(Locale.US, "%,d", value)
}
