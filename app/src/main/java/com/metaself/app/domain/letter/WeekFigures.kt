package com.metaself.app.domain.letter

import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.MonthlyLines
import com.metaself.app.domain.trainer.SessionReviews
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.weight.TrendPoint
import kotlin.math.roundToInt

/** A week's food (D100): averages of daily totals over the [daysLogged] days that hold something. */
data class FoodWeek(val daysLogged: Int, val kcal: Int?, val proteinG: Int?, val carbsG: Int?, val fatG: Int?)

/** A week's movement (D100). A null is a figure not recorded on any day of the week. */
data class MovementFigures(
    val sessions: Int,
    val minutes: Int,
    val distanceM: Int?,
    val easy: Int,
    val right: Int,
    val hard: Int,
    val activeKcalADay: Int?,
    val stepsADay: Int?,
)

/**
 * The weekly plan's week (D100): its title, and this week's planned and done sessions. [done] is
 * [com.metaself.app.domain.trainer.WeekProgress.done] — ticked sessions only, full or confirmed (D105),
 * never a candidate or an attempt.
 */
data class PlanWeekFigures(val title: String, val planned: Int, val done: Int, val ended: Boolean)

/** One Monday-to-Sunday week, counted on the phone (D100). */
data class WeekFigures(
    val monday: Long,
    val food: FoodWeek,
    val weightChangeKg: Double?,
    val weighedIn: Boolean,
    val movement: MovementFigures,
    val plan: PlanWeekFigures?,
) {
    /** D99, design question 15: nothing to write about. */
    val quiet: Boolean get() = food.daysLogged == 0 && !weighedIn && movement.sessions == 0

    companion object {
        /**
         * @param food each day's totals, for days holding at least one item; days outside the week are ignored.
         * @param trend the whole smoothed trend; its change across the week is [MonthlyLines.weightChange]'s.
         * @param workouts the record's sessions as read (combined, D92); only the week's visible, counted ones count.
         * @param reviews any; each counts toward its session's felt, never its words.
         * @param days the health record's days; only the week's are read.
         */
        fun of(
            monday: Long,
            food: Map<Long, DayTotals>,
            trend: List<TrendPoint>,
            workouts: List<Workout>,
            reviews: List<TrainerReview>,
            days: List<HealthDay>,
            plan: PlanWeekFigures?,
        ): WeekFigures {
            val sunday = monday + 6
            val week = monday..sunday
            val eaten = food.filterKeys { it in week }.values
            val sessions = workouts.filter { !it.hidden && it.counted && it.epochDay in week }
            val byWorkout = SessionReviews.bySession(sessions, reviews)
            val felt = sessions.mapNotNull { byWorkout[it.id]?.felt }
            val weekDays = days.filter { it.epochDay in week }
            return WeekFigures(
                monday = monday,
                food = FoodWeek(
                    daysLogged = eaten.size,
                    kcal = eaten.averageOf { it.kcal },
                    proteinG = eaten.averageOf { it.proteinG },
                    carbsG = eaten.averageOf { it.carbsG },
                    fatG = eaten.averageOf { it.fatG },
                ),
                weightChangeKg = MonthlyLines.weightChange(monday - 1, sunday, trend),
                weighedIn = trend.any { it.reading.epochDay in week },
                movement = MovementFigures(
                    sessions = sessions.size,
                    minutes = sessions.sumOf { it.durationMinutes },
                    distanceM = sessions.mapNotNull { it.distanceM }.takeIf { it.isNotEmpty() }?.sum(),
                    easy = felt.count { it == Felt.EASY },
                    right = felt.count { it == Felt.RIGHT },
                    hard = felt.count { it == Felt.HARD },
                    activeKcalADay = weekDays.mapNotNull { it.activeKcal }.averageOrNull(),
                    stepsADay = weekDays.mapNotNull { it.steps }.averageOrNull(),
                ),
                plan = plan,
            )
        }

        private fun Collection<DayTotals>.averageOf(pick: (DayTotals) -> Int): Int? =
            takeIf { it.isNotEmpty() }?.map(pick)?.average()?.roundToInt()

        private fun List<Int>.averageOrNull(): Int? = takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    }
}

/** The mean of the earlier weeks' figures, each over the weeks that have it (design question 14). */
data class FourWeekAverage(
    val daysLogged: Double,
    val kcal: Int?,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?,
    val weightChangeKg: Double?,
    val sessions: Double,
    val distanceM: Int?,
    val stepsADay: Int?,
)

/**
 * This week and the four before it, oldest last (D100), with today's calorie target (design question 1).
 * Exactly four earlier weeks, always: a week with nothing in it is still a week, and counts in the
 * averages that take whole weeks.
 */
data class LetterFigures(val week: WeekFigures, val earlier: List<WeekFigures>, val targetKcal: Int?) {
    init {
        require(earlier.size == WEEKS_BEFORE) { "exactly $WEEKS_BEFORE earlier weeks" }
    }

    val average: FourWeekAverage by lazy {
        fun ints(pick: (WeekFigures) -> Int?) = earlier.mapNotNull(pick).takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        FourWeekAverage(
            daysLogged = earlier.map { it.food.daysLogged }.averageOrZero(),
            kcal = ints { it.food.kcal },
            proteinG = ints { it.food.proteinG },
            carbsG = ints { it.food.carbsG },
            fatG = ints { it.food.fatG },
            weightChangeKg = earlier.mapNotNull { it.weightChangeKg }.takeIf { it.isNotEmpty() }?.average(),
            sessions = earlier.map { it.movement.sessions }.averageOrZero(),
            distanceM = ints { it.movement.distanceM },
            stepsADay = ints { it.movement.stepsADay },
        )
    }

    companion object {
        const val WEEKS_BEFORE = 4

        private fun List<Int>.averageOrZero(): Double = if (isEmpty()) 0.0 else average()
    }
}
