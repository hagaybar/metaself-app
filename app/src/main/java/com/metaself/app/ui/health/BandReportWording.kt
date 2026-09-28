package com.metaself.app.ui.health

import com.metaself.app.domain.health.BandReport
import com.metaself.app.domain.health.DayFigure
import com.metaself.app.domain.health.HealthKind
import com.metaself.app.domain.health.KindArrivals
import com.metaself.app.domain.health.WorkoutApp
import com.metaself.app.domain.health.WorkoutArrivals
import com.metaself.app.ui.movement.MovementWeekWording
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every sentence of "What the band sends" (D80), and the text Copy as text puts on the clipboard:
 * counts, dates and app names only, never a reading's value.
 *
 * [labels] maps a package name to the phone's name for that app; a package missing from it is shown
 * as itself.
 */
object BandReportWording {

    /**
     * What Health Connect cannot carry. Checked against the pinned client (connect-client 1.1.0): it has
     * no record type for stress, training load or recovery time, and it does have `Vo2MaxRecord` —
     * which is not one of the thirteen kinds this app copies (D66).
     */
    const val NOT_SHARED = "Stress, training load and recovery time are not shared through Health Connect; " +
        "they stay in the band's app. VO₂ max has a Health Connect record, but this app does not copy it."

    /**
     * D81: said of an app when it wrote no distance reading of its own during any of its workouts in
     * the window — the reason a workout of its has none, as far as the record can tell. A workout's
     * own distance is Health Connect's total over its time from every app, so it can still have one.
     */
    const val DISTANCE_NOT_SHARED = "distance not shared for workouts by this app"

    /** [Locale.US]: Java 17's UK data writes "Sept" (see [MovementWeekWording]). */
    private val DAY = DateTimeFormatter.ofPattern("d MMM", Locale.US)

    private const val SEP = " · "

    /** "The last 30 days, 5 Aug to today." */
    fun window(report: BandReport, today: Long): String =
        "The last ${report.windowDays} days, ${day(report.fromDay, today)} to ${day(report.toDay, today)}."

    /** "1,200 readings · 30 days · from com.example.band"; "nothing arrived"; "not allowed". */
    fun kindLine(kind: KindArrivals, notAllowed: Set<HealthKind>, labels: Map<String, String>): String {
        if (kind.count == 0) return if (kind.kind in notAllowed) "not allowed" else "nothing arrived"
        return count(kind.count, unitOf(kind.kind)) + SEP + count(kind.days, "day", "days") + SEP +
            "from " + names(kind.origins.map { labels[it] ?: it })
    }

    /** "first 5 Aug · last today", or null when nothing arrived. */
    fun kindDates(kind: KindArrivals, today: Long): String? {
        val first = kind.firstDay ?: return null
        val last = kind.lastDay ?: return null
        return "first ${day(first, today)}" + SEP + "last ${day(last, today)}"
    }

    /** The workouts section, one line each: count, kinds, sources, the copied ones' details. */
    fun workoutLines(w: WorkoutArrivals): List<String> {
        if (w.total == 0) return listOf("none in these 30 days")
        val byName = linkedMapOf<String, Int>()
        w.byKind.forEach { (kind, n) ->
            val name = MovementWeekWording.kindName(kind)
            byName[name] = (byName[name] ?: 0) + n
        }
        return buildList {
            add(count(w.total, "workout", "workouts"))
            add(byName.entries.joinToString(SEP) { (name, n) -> "$name $n" })
            add("copied ${w.copied}" + SEP + "typed ${w.typed}")
            if (w.notCounted > 0) add(count(w.notCounted, "walk", "walks") + " not counted")
            if (w.copied > 0) {
                add(
                    listOf(
                        "distance" to w.copiedWithDistance,
                        "calories" to w.copiedWithCalories,
                        "heart rate (worked out here)" to w.copiedWithHeartRate,
                        "title" to w.copiedWithTitle,
                    ).joinToString(SEP) { (detail, n) -> "$detail $n of ${w.copied}" },
                )
            }
        }
    }

    /**
     * One app's workouts (D81), e.g. "6 workouts · 4 walks" (", not counted" when its walks are
     * switched off) and "distance on 2 of 6" (both invented), then [DISTANCE_NOT_SHARED] when it wrote
     * no distance during any of them. That line is left out when distance is not allowed
     * ([distanceAllowed] false: no readings are stored, so the record cannot tell), and when every one
     * of its workouts carries a distance (it would contradict the line above it). [windowDays] is the
     * report's own window.
     */
    fun appLines(app: WorkoutApp, windowDays: Int, distanceAllowed: Boolean): List<String> {
        if (app.workouts == 0) {
            return listOf("no workouts in these $windowDays days" + if (app.walksCounted) "" else SEP + "walks not counted")
        }
        val walks = if (app.walks == 0) "no walks" else count(app.walks, "walk", "walks")
        val off = if (!app.walksCounted && app.walks > 0) ", not counted" else ""
        return buildList {
            add(count(app.workouts, "workout", "workouts") + SEP + walks + off)
            add("distance on ${app.withDistance} of ${app.workouts}")
            if (distanceAllowed && app.withOwnDistance == 0 && app.withDistance < app.workouts) add(DISTANCE_NOT_SHARED)
        }
    }

    /** "steps 30 of 30 days", for each figure of the daily summary, in the spec's order. */
    fun dayLines(report: BandReport): List<String> = DayFigure.entries.map { figure ->
        "${figureName(figure)} ${report.daysWith[figure] ?: 0} of ${report.windowDays} days"
    }

    /** The whole page as plain text, for pasting into a conversation. */
    fun asText(report: BandReport, notAllowed: Set<HealthKind>, labels: Map<String, String>, today: Long): String =
        buildList {
            add("What the band sends — the last ${report.windowDays} days, " +
                "${day(report.fromDay, today)} to ${day(report.toDay, today)}")
            add("")
            add("Kinds of reading")
            report.kinds.forEach { kind ->
                val dates = kindDates(kind, today)?.let { SEP + it }.orEmpty()
                add("${kind.kind.displayName}: ${kindLine(kind, notAllowed, labels)}$dates")
            }
            add("")
            val workouts = workoutLines(report.workouts)
            add("Workouts: " + workouts.first())
            addAll(workouts.drop(1))
            report.workouts.apps.forEach { app ->
                val lines = appLines(app, report.windowDays, distanceAllowed = HealthKind.DISTANCE !in notAllowed)
                add("${labels[app.origin] ?: app.origin}: " + lines.joinToString(SEP))
            }
            add("")
            add("Daily summary")
            addAll(dayLines(report))
            add("")
            add(NOT_SHARED)
        }.joinToString("\n")

    private fun unitOf(kind: HealthKind): Pair<String, String> = when (kind) {
        HealthKind.SLEEP -> "night" to "nights"
        HealthKind.EXERCISE -> "workout" to "workouts"
        else -> "reading" to "readings"
    }

    private fun count(n: Int, unit: Pair<String, String>): String = count(n, unit.first, unit.second)

    private fun count(n: Int, one: String, many: String): String =
        String.format(Locale.US, "%,d", n) + " " + if (n == 1) one else many

    private fun names(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names.single()
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private fun day(epochDay: Long, today: Long): String =
        if (epochDay == today) "today" else LocalDate.ofEpochDay(epochDay).format(DAY)

    private fun figureName(figure: DayFigure): String = when (figure) {
        DayFigure.STEPS -> "steps"
        DayFigure.DISTANCE -> "distance"
        DayFigure.ACTIVE_KCAL -> "movement calories"
        DayFigure.TOTAL_KCAL -> "total calories"
        DayFigure.RESTING_HEART_RATE -> "resting heart rate"
        DayFigure.HRV -> "heart-rate variability"
        DayFigure.OXYGEN -> "blood oxygen"
        DayFigure.BREATHING -> "breathing rate"
        DayFigure.SLEEP -> "sleep"
        DayFigure.WORKOUTS -> "workouts"
    }
}
