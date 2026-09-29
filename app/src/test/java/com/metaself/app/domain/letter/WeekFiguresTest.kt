package com.metaself.app.domain.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.EnergySource
import com.metaself.app.domain.movement.HealthDay
import com.metaself.app.domain.movement.MovementWeek
import com.metaself.app.domain.movement.Workout
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.movement.WorkoutSource
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.TrainerReview
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** D100. Every figure is invented and round; the week is the one holding [TEST_EPOCH_DAY]. */
class WeekFiguresTest {

    private val monday = MovementWeek.mondayOf(TEST_EPOCH_DAY)

    private fun walk(id: Long, day: Long, minutes: Int = 30, distanceM: Int? = 3_000) = Workout(
        id = id, epochDay = day, startedAtMillis = day * DAY + 36_000_000, durationMinutes = minutes,
        kind = WorkoutKind.WALK, title = null, distanceM = distanceM, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = false, note = null,
    )

    @Test
    fun `food is averaged over the days that hold something, and days logged counts them`() {
        val food = mapOf(
            monday to DayTotals(2_000, 100, 200, 80),
            monday + 1 to DayTotals(2_200, 120, 220, 60),
            monday + 7 to DayTotals(9_000, 900, 900, 900),
        )
        val week = WeekFigures.of(monday, food, emptyList(), emptyList(), emptyList(), emptyList(), plan = null)

        assertThat(week.food).isEqualTo(FoodWeek(daysLogged = 2, kcal = 2_100, proteinG = 110, carbsG = 210, fatG = 70))
    }

    @Test
    fun `a food average half-way between two whole numbers rounds up`() {
        val food = mapOf(
            monday to DayTotals(2_000, 100, 200, 60),
            monday + 1 to DayTotals(2_201, 101, 201, 61),
        )
        val week = WeekFigures.of(monday, food, emptyList(), emptyList(), emptyList(), emptyList(), plan = null)

        assertThat(week.food).isEqualTo(FoodWeek(daysLogged = 2, kcal = 2_101, proteinG = 101, carbsG = 201, fatG = 61))
    }

    @Test
    fun `movement counts visible, counted sessions, and energy and steps are a day's average where recorded`() {
        val hidden = walk(3, monday + 2).copy(hidden = true)
        val uncounted = walk(4, monday + 3).copy(counted = false)
        val days = listOf(HealthDay(monday, steps = 8_000, activeKcal = 300), HealthDay(monday + 1, steps = 10_000))
        val reviews = listOf(TrainerReview(workoutId = 1, planId = null, felt = Felt.RIGHT, words = "ignored"))

        val week = WeekFigures.of(
            monday, emptyMap(), emptyList(),
            listOf(walk(1, monday), walk(2, monday + 1, 60), hidden, uncounted), reviews, days, null,
        )

        assertThat(week.movement).isEqualTo(
            MovementFigures(sessions = 2, minutes = 90, distanceM = 6_000, easy = 0, right = 1, hard = 0, activeKcalADay = 300, stepsADay = 9_000),
        )
    }

    @Test
    fun `the weight trend's change across the week rests on fresh weigh-ins, else there is none`() {
        val trend = WeightTrend.of(listOf(WeightReading(monday - 3, 80.0), WeightReading(monday + 6, 79.0)))

        assertThat(WeekFigures.of(monday, emptyMap(), trend, emptyList(), emptyList(), emptyList(), null).weightChangeKg!!)
            .isWithin(0.001).of(trend.last().trendKg - trend.first().trendKg)
        val stale = WeightTrend.of(listOf(WeightReading(monday - 30, 80.0), WeightReading(monday + 6, 79.0)))
        assertThat(WeekFigures.of(monday, emptyMap(), stale, emptyList(), emptyList(), emptyList(), null).weightChangeKg).isNull()
    }

    @Test
    fun `a week with no food, no weigh-in and no counted session is quiet`() {
        val quiet = WeekFigures.of(monday, emptyMap(), emptyList(), listOf(walk(1, monday).copy(hidden = true)), emptyList(), emptyList(), null)
        val weighed = WeekFigures.of(monday, emptyMap(), WeightTrend.of(listOf(WeightReading(monday + 2, 80.0))), emptyList(), emptyList(), emptyList(), null)
        val walked = WeekFigures.of(monday, emptyMap(), emptyList(), listOf(walk(1, monday)), emptyList(), emptyList(), null)
        val ate = WeekFigures.of(monday, mapOf(monday to DayTotals(2_000, 100, 200, 80)), emptyList(), emptyList(), emptyList(), emptyList(), null)

        assertThat(quiet.quiet).isTrue()
        assertThat(weighed.quiet).isFalse()
        assertThat(walked.quiet).isFalse()
        assertThat(ate.quiet).isFalse()
    }

    @Test
    fun `the 4-week average is over the earlier weeks that have a figure, and none when no week has`() {
        fun food(kcal: Int, carbs: Int, fat: Int) = mapOf(monday to DayTotals(kcal, 100, carbs, fat))
        val earlier = listOf(Triple(2_000, 200, 60), Triple(2_200, 240, 80)).mapIndexed { i, (kcal, carbs, fat) ->
            WeekFigures.of(monday - 7L * (i + 1), food(kcal, carbs, fat).mapKeys { it.key - 7L * (i + 1) }, emptyList(), emptyList(), emptyList(), emptyList(), null)
        } + List(2) { i -> WeekFigures.of(monday - 7L * (i + 3), emptyMap(), emptyList(), emptyList(), emptyList(), emptyList(), null) }
        val figures = LetterFigures(WeekFigures.of(monday, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList(), null), earlier, targetKcal = 2_100)

        assertThat(figures.average.kcal).isEqualTo(2_100)
        assertThat(figures.average.proteinG).isEqualTo(100)
        assertThat(figures.average.carbsG).isEqualTo(220)
        assertThat(figures.average.fatG).isEqualTo(70)
        assertThat(figures.average.daysLogged).isWithin(0.001).of(0.5)
        assertThat(figures.average.weightChangeKg).isNull()
    }

    @Test
    fun `the figures always hold exactly four earlier weeks`() {
        fun week(monday: Long) = WeekFigures.of(monday, emptyMap(), emptyList(), emptyList(), emptyList(), emptyList(), null)
        val thisWeek = week(monday)

        listOf(3, 5).forEach { n ->
            val earlier = List(n) { week(monday - 7L * (it + 1)) }
            assertThrows<IllegalArgumentException> { LetterFigures(thisWeek, earlier, targetKcal = null) }
        }
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
