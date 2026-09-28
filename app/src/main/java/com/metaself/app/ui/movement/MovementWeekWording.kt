package com.metaself.app.ui.movement

import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.FigureSource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutFigureSource
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * One line of an open day. [workout] is set on a workout's own line, so the screen can make a typed
 * workout's line a door to change it (D76).
 */
data class DetailLine(val text: String, val workout: Workout? = null)

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
    private val DATE_WITH_YEAR = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.US)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    private const val SEP = " · "

    const val NOTHING = "nothing recorded"

    /**
     * The week shown, by its Monday (D83): "THIS WEEK · FROM MON 31 AUG", "LAST WEEK · FROM MON 24 AUG",
     * then "WEEK OF MON 17 AUG" further back. [thisMonday] is the calendar week's Monday. A Monday
     * outside [today]'s year says its year: "WEEK OF MON 16 JUN 2025". Set in capitals here: these are
     * the app's words, not the owner's.
     */
    fun kicker(monday: Long, thisMonday: Long, today: Long): String {
        val date = date(monday, today).uppercase(Locale.US)
        return when (monday) {
            thisMonday -> "THIS WEEK · FROM $date"
            thisMonday - 7 -> "LAST WEEK · FROM $date"
            else -> "WEEK OF $date"
        }
    }

    /** The one large figure (D74): "42.6 km". */
    fun distance(week: MovementWeek): String? = week.distanceM?.let(::km)

    fun averageMovement(week: MovementWeek): String? =
        week.averageActiveKcal?.let { "${number(it)} kcal of movement a day, on average" }

    /** "2 workouts · 3 walks · 4 h 30" (D78): either part left out at zero; null with neither. */
    fun workouts(week: MovementWeek): String? {
        val parts = listOfNotNull(
            week.workoutCount.takeIf { it > 0 }?.let { if (it == 1) "1 workout" else "$it workouts" },
            week.walkCount.takeIf { it > 0 }?.let { if (it == 1) "1 walk" else "$it walks" },
        )
        if (parts.isEmpty()) return null
        return (parts + duration(week.workoutMinutes)).joinToString(SEP)
    }

    /**
     * "Thu 3 Sep"; "Sun 22 Jun 2025" for a day outside [today]'s year (D83, amended), judged day by
     * day, so a week across New Year gives the year only to last year's days.
     */
    fun dayHeading(epochDay: Long, today: Long): String = date(epochDay, today)

    private fun date(epochDay: Long, today: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        return date.format(if (date.year == LocalDate.ofEpochDay(today).year) SHORT_DATE else DATE_WITH_YEAR)
    }

    /**
     * A day's one-line summary (D73), shown whether the day is open or closed: up to three parts —
     * movement calories, workouts, sleep. With none of them, the first line the open day would show,
     * so a day with steps is never called empty; "nothing recorded" only when there is nothing at all.
     */
    fun summaryLine(day: MovementDay): String {
        val health = day.health
        val parts = listOfNotNull(
            health?.activeKcal?.let { "${number(it)} kcal" },
            sessions(day.workouts),
            health?.sleepMinutes?.let { "slept ${duration(it)}" },
        )
        // With no part there is no session, so no start time is said and the zone does not matter.
        return if (parts.isNotEmpty()) parts.joinToString(SEP) else partLines(day, ZoneOffset.UTC).firstOrNull()?.text ?: NOTHING
    }

    /**
     * What an open day shows beneath its summary (D73): one line per part, each only when recorded.
     * A line the summary already says is not repeated, so a day with nothing but steps, or nothing at
     * all, has nothing beneath.
     */
    fun detailRows(day: MovementDay, zone: ZoneId): List<DetailLine> {
        val summary = summaryLine(day)
        return partLines(day, zone).filterNot { it.text == summary }
    }

    /** [detailRows]' words alone. */
    fun detailLines(day: MovementDay, zone: ZoneId): List<String> = detailRows(day, zone).map { it.text }

    /** "Last four weeks: 38.1 · 45.0 · — · 44.7 km"; null when none of the four has a distance. */
    fun lastFourWeeks(week: MovementWeek): String? {
        if (week.previousWeeksM.all { it == null }) return null
        return "Last four weeks: " +
            week.previousWeeksM.joinToString(SEP) { metres -> metres?.let(::kmFigure) ?: "—" } +
            " km"
    }

    /** The recording app's name for a session, or its kind's. */
    fun name(workout: Workout): String = workout.title?.takeIf { it.isNotBlank() } ?: kindName(workout.kind)

    /** A kind's name, as `ExerciseNames` names the same kinds on the day screen. */
    fun kindName(kind: WorkoutKind): String = when (kind) {
        WorkoutKind.RUN -> "Running"
        WorkoutKind.WALK -> "Walking"
        WorkoutKind.CYCLE -> "Cycling"
        WorkoutKind.SWIM -> "Swimming"
        WorkoutKind.STRENGTH -> "Weights"
        WorkoutKind.OTHER, WorkoutKind.UNRECOGNISED -> "Exercise"
    }

    /**
     * A day's sessions in its summary (D78): same-kind sessions combined — "Walking ×3 · 2 h 30" —
     * in the order each kind first started. A kind's total distance when every one of its sessions
     * has one, its total time otherwise. Named by the title its sessions share, or by the kind when
     * the titles differ. A kind with one session reads as it always did: "Running 6.2 km".
     */
    private fun sessions(workouts: List<Workout>): String? {
        if (workouts.isEmpty()) return null
        return workouts.groupBy { it.kind }.values.joinToString(", ") { same ->
            val label = same.map(::name).distinct().singleOrNull() ?: kindName(same.first().kind)
            val distances = same.mapNotNull { it.distanceM }
            val measure = if (distances.size == same.size) km(distances.sum()) else duration(same.sumOf { it.durationMinutes })
            if (same.size == 1) "$label $measure" else "$label ×${same.size}$SEP$measure"
        }
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
    private fun partLines(day: MovementDay, zone: ZoneId): List<DetailLine> {
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
        return movement.map { DetailLine(it) } +
            day.workouts.map { DetailLine(workoutLine(it, zone), it) } +
            rest.map { DetailLine(it) }
    }

    /** "Walking · 10:20 · 2.0 km · 25 min · avg 105 bpm" (invented): its start time in [zone] (D91) after its name. */
    private fun workoutLine(workout: Workout, zone: ZoneId): String = listOfNotNull(
        name(workout),
        Instant.ofEpochMilli(workout.startedAtMillis).atZone(zone).format(TIME),
        workout.distanceM?.let { metres ->
            // D82: a file's distance says so, to the two decimals it was written with.
            if (workout.distanceSource == WorkoutFigureSource.FILE) WorkoutFileWording.fileKm(metres) + " (from file)" else km(metres)
        },
        duration(workout.durationMinutes),
        // D78: pace for runs only.
        workout.paceSecondsPerKm?.takeIf { workout.kind == WorkoutKind.RUN }?.let(::pace),
        workout.avgHeartRate?.let { "avg $it bpm" },
        typedEnergy(workout),
    ).joinToString(SEP)

    /** A typed workout's energy with where it came from (D4). A synced one's is not shown here. */
    private fun typedEnergy(workout: Workout): String? {
        if (workout.source != WorkoutSource.TYPED) return null
        val kcal = workout.energyKcal ?: return null
        return when (workout.energySource) {
            EnergySource.MET_ESTIMATE -> "about ${number(kcal)} kcal, estimated"
            EnergySource.TYPED -> "${number(kcal)} kcal, you set this"
            EnergySource.FILE -> "${number(kcal)} kcal, from the file"
            EnergySource.BAND, EnergySource.NONE -> null
        }
    }

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
