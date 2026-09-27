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
 * This week, Monday to today, as the Movement screen shows it (D73, D74).
 *
 * @property distanceM the week's distance so far: the sum of the days' de-duplicated totals (D69);
 *   null when no day this week has one.
 * @property averageActiveKcal the mean movement calories over the days that have a figure, rounded;
 *   days without are not counted as zero. Null when none has one.
 * @property workoutCount visible sessions this week that are not walks (D78).
 * @property walkCount visible walks this week, counted apart from workouts (D78).
 * @property workoutMinutes every visible session's time, walks included (D78).
 * @property days today first, back to Monday.
 * @property previousWeeksM the four weeks before this one, newest first; null for a week with no
 *   distance on any day.
 */
data class MovementWeek(
    val monday: Long,
    val distanceM: Int?,
    val averageActiveKcal: Int?,
    val workoutCount: Int,
    val walkCount: Int,
    val workoutMinutes: Int,
    val days: List<MovementDay>,
    val previousWeeksM: List<Int?>,
) {
    companion object {

        const val PREVIOUS_WEEKS = 4

        /**
         * The Monday-based week number `WorkoutDao.observeWeeklyRunning` uses: epoch day 0 was a
         * Thursday, so `(epochDay + 3) / 7` changes on Mondays. Floored, so it stays right before 1970.
         */
        fun weekOf(epochDay: Long): Long = Math.floorDiv(epochDay + 3, 7L)

        fun mondayOf(epochDay: Long): Long = weekOf(epochDay) * 7 - 3

        /**
         * @param days the health record's days from four weeks before this Monday to [today]; any
         *   others are ignored.
         * @param workouts this week's workouts, hidden ones included — they are left out here.
         * @param mealsByDay this week's meals, by day.
         */
        fun of(
            today: Long,
            days: List<HealthDay>,
            workouts: List<Workout>,
            mealsByDay: Map<Long, List<Meal>>,
        ): MovementWeek {
            val monday = mondayOf(today)
            val byDay = days.associateBy { it.epochDay }
            val thisWeek = (monday..today).mapNotNull { byDay[it] }
            val visible = workouts
                .filter { !it.hidden && it.epochDay in monday..today }
                .sortedBy { it.startedAtMillis }
            val distances = thisWeek.mapNotNull { it.distanceM }
            val active = thisWeek.mapNotNull { it.activeKcal }
            val week = weekOf(today)

            return MovementWeek(
                monday = monday,
                distanceM = distances.takeIf { it.isNotEmpty() }?.sum(),
                averageActiveKcal = active.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
                workoutCount = visible.count { it.kind != WorkoutKind.WALK },
                walkCount = visible.count { it.kind == WorkoutKind.WALK },
                workoutMinutes = visible.sumOf { it.durationMinutes },
                days = (today downTo monday).map { day ->
                    MovementDay(
                        epochDay = day,
                        health = byDay[day],
                        workouts = visible.filter { it.epochDay == day },
                        eatenKcal = eaten(mealsByDay[day].orEmpty()),
                    )
                },
                previousWeeksM = (1..PREVIOUS_WEEKS).map { back ->
                    days.filter { weekOf(it.epochDay) == week - back }
                        .mapNotNull { it.distanceM }
                        .takeIf { it.isNotEmpty() }
                        ?.sum()
                },
            )
        }

        /**
         * The day screen's own sum (`DayTotals.of`), so the two screens cannot disagree. Null with no
         * meal logged; a logged meal always holds at least one item ([Meal]'s own rule).
         */
        private fun eaten(meals: List<Meal>): Int? =
            if (meals.isEmpty()) null else DayTotals.of(meals).kcal
    }
}
