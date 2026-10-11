package com.metaself.app.domain.movement

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.Meal
import kotlin.math.roundToInt

/**
 * Where one figure of a day's health summary came from (D69), read back from its stored name —
 * never an ordinal, for the reason [WorkoutKind] gives.
 */
enum class FigureSource {
    TOTAL, READ, COMPUTED, CORRECTED,

    /** A name this version does not know. Shown with no source at all rather than a guessed one. */
    UNRECOGNISED;

    companion object {
        fun parse(stored: String?): FigureSource? =
            if (stored == null) null else entries.firstOrNull { it.name == stored } ?: UNRECOGNISED
    }
}

/**
 * One day of the stored health record (D65, D69), as the Movement screen reads it.
 *
 * Pure: the Room row maps to this in `data/health/MovementRecord.kt`. Every figure is nullable,
 * because no data is not zero.
 */
data class HealthDay(
    val epochDay: Long,
    val steps: Int? = null,
    val stepsSource: FigureSource? = null,
    val distanceM: Int? = null,
    val activeKcal: Int? = null,
    val activeKcalSource: FigureSource? = null,
    val restingHeartRate: Int? = null,
    val hrvMs: Double? = null,
    val oxygenPct: Double? = null,
    val respiratoryRate: Double? = null,
    val sleepMinutes: Int? = null,
    val deepMinutes: Int? = null,
    val remMinutes: Int? = null,
    val lightMinutes: Int? = null,
)

/**
 * One row of the Movement screen (D73).
 *
 * @property health null when the record has no summary for the day.
 * @property workouts the day's visible workouts, in the order they started.
 * @property eatenKcal what the meals logged that day add up to; null when nothing was logged.
 */
data class MovementDay(
    val epochDay: Long,
    val health: HealthDay?,
    val workouts: List<Workout>,
    val eatenKcal: Int?,
)

/**
 * One week as the Movement screen shows it (D73, D74): this week, Monday to today, or an earlier
 * one, Monday to Sunday (D83).
 *
 * @property monday the Monday of the week shown.
 * @property thisMonday the Monday of the calendar's week, so the week knows whether it is the current
 *   one ([isCurrent]) and the kicker can name it.
 * @property distanceM the week's distance so far: the sum of the days' de-duplicated totals (D69);
 *   null when no day this week has one.
 * @property averageActiveKcal the mean movement calories over the days that have a figure, rounded;
 *   days without are not counted as zero. Null when none has one.
 * "Visible" below means neither hidden nor a walk that does not count (D81).
 *
 * @property today the calendar's today, whose year a date is said against (D83, amended).
 * @property sessions the week's visible sessions, walks included (D131), counted by [WeekSessions] — the
 *   Trainer's own count (D115).
 * @property minutes every one of those sessions' minutes (D131): the headline, and the verdict's figure.
 * @property days newest first: today back to Monday on this week; Sunday back to Monday on an
 *   earlier one, all seven (D83).
 * @property previousWeeksM the four weeks before the one shown, newest first; null for a week with no
 *   distance on any day.
 */
data class MovementWeek(
    val monday: Long,
    val thisMonday: Long,
    val today: Long,
    val distanceM: Int?,
    val averageActiveKcal: Int?,
    val sessions: Int,
    val minutes: Int,
    val days: List<MovementDay>,
    val previousWeeksM: List<Int?>,
) {
    /** The calendar's own week, rather than an earlier one stepped back to (D83). */
    val isCurrent: Boolean get() = monday == thisMonday

    /** D132: the four weeks before this one, the weeks with a distance summed; null when none has one. */
    val fourWeeksM: Int? get() = fourWeeks(previousWeeksM)

    companion object {

        const val PREVIOUS_WEEKS = 4

        /**
         * The Monday-based week number `WorkoutDao.observeWeeklyRunning` uses: epoch day 0 was a
         * Thursday, so `(epochDay + 3) / 7` changes on Mondays. Floored, so it stays right before 1970.
         */
        fun weekOf(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

        fun mondayOf(epochDay: Long): Long = weekOf(epochDay) * 7 - 3

        /**
         * @param days the health record's days from four weeks before [monday] to the week's last day
         *   shown; any others are ignored.
         * @param workouts the week's workouts, hidden ones and uncounted walks (D81) included — both
         *   are left out here.
         * @param mealsByDay the week's meals, by day.
         * @param monday the week to show: this week's Monday unless an earlier one is asked for (D83).
         *   Never a later week — nothing after this week is shown — and always a Monday.
         */
        fun of(
            today: Long,
            days: List<HealthDay>,
            workouts: List<Workout>,
            mealsByDay: Map<Long, List<Meal>>,
            monday: Long = mondayOf(today),
        ): MovementWeek {
            val thisMonday = mondayOf(today)
            require(mondayOf(monday) == monday) { "not a Monday: $monday" }
            require(monday <= thisMonday) { "a week after this one: $monday" }
            // This week stops at today; an earlier week is all seven days.
            val last = if (monday == thisMonday) today else monday + 6
            val byDay = days.associateBy { it.epochDay }
            val thisWeek = (monday..last).mapNotNull { byDay[it] }
            val visible = WeekSessions.of(workouts, monday, today)
            val active = thisWeek.mapNotNull { it.activeKcal }

            return MovementWeek(
                monday = monday,
                thisMonday = thisMonday,
                today = today,
                distanceM = distanceOf(days, monday, last),
                averageActiveKcal = active.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                sessions = visible.size,
                minutes = WeekSessions.minutes(visible),
                days = (last downTo monday).map { day ->
                    MovementDay(
                        epochDay = day,
                        health = byDay[day],
                        workouts = visible.filter { it.epochDay == day },
                        eatenKcal = eaten(mealsByDay[day].orEmpty()),
                    )
                },
                previousWeeksM = previousWeeksOf(days, monday),
            )
        }

        /**
         * The distance from [from] to [to]: the sum of the days' de-duplicated totals (D69) that have one; null
         * when none has. The headline's figure (D74), and the Trainer's summary's (D132).
         */
        fun distanceOf(days: List<HealthDay>, from: Long, to: Long): Int? =
            days.filter { it.epochDay in from..to }.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum()

        /** The [PREVIOUS_WEEKS] weeks before [monday]'s, newest first, each [distanceOf] its seven days. */
        fun previousWeeksOf(days: List<HealthDay>, monday: Long): List<Int?> =
            (1..PREVIOUS_WEEKS).map { back -> distanceOf(days, monday - 7L * back, monday - 7L * back + 6) }

        /** D132: [previous]' weeks that have a distance, summed; null when none has. */
        fun fourWeeks(previous: List<Int?>): Int? = previous.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

        /**
         * The day screen's own sum (`DayTotals.of`), so the two screens cannot disagree. Null with no
         * meal logged; a logged meal always holds at least one item ([Meal]'s own rule).
         */
        private fun eaten(meals: List<Meal>): Int? =
            if (meals.isEmpty()) null else DayTotals.of(meals).kcal
    }
}
