package com.metaself.app.domain.movement

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Meal
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.day.aMeal
import com.metaself.app.domain.day.anItem
import org.junit.jupiter.api.Test

/**
 * The Movement screen's week (D73, D74), as numbers. Every figure here is invented, and round so the
 * arithmetic can be checked by eye.
 *
 * TEST_EPOCH_DAY is Thursday 3 September 2026, so its week began on Monday 31 August, epoch day
 * 20,696; the four weeks before it began on 24, 17, 10 and 3 August (20,689 / 20,682 / 20,675 /
 * 20,668).
 */
class MovementWeekTest {

    private val today = TEST_EPOCH_DAY
    private val monday = 20_696L

    @Test
    fun `the week runs from Monday to today, today first`() {
        val week = MovementWeek.of(today, days = emptyList(), workouts = emptyList(), mealsByDay = emptyMap())

        assertThat(week.monday).isEqualTo(monday)
        assertThat(week.days.map { it.epochDay })
            .containsExactly(20_699L, 20_698L, 20_697L, 20_696L).inOrder()
    }

    @Test
    fun `on a Monday the week is that one day`() {
        val week = MovementWeek.of(monday, emptyList(), emptyList(), emptyMap())

        assertThat(week.days.map { it.epochDay }).containsExactly(monday)
    }

    @Test
    fun `Sunday closes the week and Monday starts the next`() {
        assertThat(MovementWeek.mondayOf(20_702L)).isEqualTo(monday) // Sunday 6 September
        assertThat(MovementWeek.mondayOf(20_703L)).isEqualTo(20_703L) // Monday 7 September
    }

    @Test
    fun `the distance is the sum of the days that have one`() {
        val days = listOf(day(20_699, distanceM = 5_000), day(20_698), day(20_696, distanceM = 7_500))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).distanceM).isEqualTo(12_500)
    }

    @Test
    fun `with no distance on any day there is no distance, not zero`() {
        val days = listOf(day(20_699, activeKcal = 300))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).distanceM).isNull()
    }

    @Test
    fun `a day before Monday does not count towards this week`() {
        val days = listOf(
            day(20_695, distanceM = 9_000, activeKcal = 900),
            day(20_699, distanceM = 1_000, activeKcal = 100),
        )

        val week = MovementWeek.of(today, days, emptyList(), emptyMap())

        assertThat(week.distanceM).isEqualTo(1_000)
        assertThat(week.averageActiveKcal).isEqualTo(100)
        assertThat(week.days.map { it.epochDay }).doesNotContain(20_695L)
    }

    /** D74: days without a figure are not counted as zero. */
    @Test
    fun `the average movement counts only the days that have a figure`() {
        val days = listOf(day(20_699, activeKcal = 300), day(20_698), day(20_697, activeKcal = 500))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).averageActiveKcal).isEqualTo(400)
    }

    @Test
    fun `with no movement figure on any day there is no average`() {
        val days = listOf(day(20_699, distanceM = 2_000))

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).averageActiveKcal).isNull()
    }

    @Test
    fun `hidden workouts are left out of the count, the time and the day`() {
        val workouts = listOf(
            workout(20_699, minutes = 30),
            workout(20_699, minutes = 45, hidden = true),
            workout(20_697, minutes = 60),
        )

        val week = MovementWeek.of(today, emptyList(), workouts, emptyMap())

        assertThat(week.workoutCount).isEqualTo(2)
        assertThat(week.workoutMinutes).isEqualTo(90)
        assertThat(week.days.first().workouts.map { it.durationMinutes }).containsExactly(30)
    }

    @Test
    fun `a workout before Monday is not this week's`() {
        val week = MovementWeek.of(today, emptyList(), listOf(workout(20_695, minutes = 60)), emptyMap())

        assertThat(week.workoutCount).isEqualTo(0)
        assertThat(week.workoutMinutes).isEqualTo(0)
    }

    /** D78: walks are counted apart from workouts; the time is every session's. Invented minutes. */
    @Test
    fun `walks are counted apart from workouts, and the time is every session's`() {
        val workouts = listOf(
            workout(20_699, minutes = 30),
            workout(20_698, minutes = 45),
            workout(20_697, minutes = 60, kind = WorkoutKind.WALK),
            workout(20_697, minutes = 20, kind = WorkoutKind.WALK, hidden = true),
        )

        val week = MovementWeek.of(today, emptyList(), workouts, emptyMap())

        assertThat(week.workoutCount).isEqualTo(2)
        assertThat(week.walkCount).isEqualTo(1)
        assertThat(week.workoutMinutes).isEqualTo(135)
    }

    @Test
    fun `a day's workouts are in the order they started`() {
        val workouts = listOf(
            workout(20_699, minutes = 20, startedAtMillis = 2_000),
            workout(20_699, minutes = 40, startedAtMillis = 1_000),
        )

        val week = MovementWeek.of(today, emptyList(), workouts, emptyMap())

        assertThat(week.days.first().workouts.map { it.durationMinutes }).containsExactly(40, 20).inOrder()
    }

    @Test
    fun `eaten is the day's logged total`() {
        val meals = mapOf(20_699L to listOf(aMeal(items = listOf(anItem(kcal = 600), anItem(kcal = 400)))))

        val week = MovementWeek.of(today, emptyList(), emptyList(), meals)

        assertThat(week.days.first().eatenKcal).isEqualTo(1_000)
        assertThat(week.days[1].eatenKcal).isNull()
    }

    @Test
    fun `a day with no meals logged has no eaten figure, not zero`() {
        val meals = mapOf(20_699L to emptyList<Meal>())

        assertThat(MovementWeek.of(today, emptyList(), emptyList(), meals).days.first().eatenKcal).isNull()
    }

    @Test
    fun `a day with no health row still has its row, with nothing in it`() {
        val week = MovementWeek.of(today, listOf(day(20_699, activeKcal = 300)), emptyList(), emptyMap())

        assertThat(week.days.first().health?.activeKcal).isEqualTo(300)
        assertThat(week.days[1].health).isNull()
        assertThat(week.days[1].workouts).isEmpty()
    }

    @Test
    fun `the last four weeks are newest first, with no figure for a week with no distance`() {
        val days = listOf(
            day(20_690, distanceM = 10_000), // the week of 24 August
            day(20_676, distanceM = 4_000), // the week of 10 August …
            day(20_677, distanceM = 6_000), // … twice
            day(20_668, distanceM = 2_000), // the week of 3 August
            day(20_661, distanceM = 99_000), // five weeks back: not on the line
            day(20_699, distanceM = 1_000), // this week: the headline, not the line
        )

        assertThat(MovementWeek.of(today, days, emptyList(), emptyMap()).previousWeeksM)
            .containsExactly(10_000, null, 10_000, 2_000).inOrder()
    }

    @Test
    fun `a source is read by name, and one this version does not know says so`() {
        assertThat(FigureSource.parse("CORRECTED")).isEqualTo(FigureSource.CORRECTED)
        assertThat(FigureSource.parse("GUESSED")).isEqualTo(FigureSource.UNRECOGNISED)
        assertThat(FigureSource.parse(null)).isNull()
    }

    private fun day(epochDay: Long, distanceM: Int? = null, activeKcal: Int? = null) = HealthDay(
        epochDay = epochDay,
        distanceM = distanceM,
        activeKcal = activeKcal,
        activeKcalSource = activeKcal?.let { FigureSource.TOTAL },
    )

    private fun workout(
        epochDay: Long,
        minutes: Int,
        hidden: Boolean = false,
        startedAtMillis: Long = 0,
        kind: WorkoutKind = WorkoutKind.RUN,
    ) = Workout(
        id = 0, epochDay = epochDay, startedAtMillis = startedAtMillis, durationMinutes = minutes,
        kind = kind, title = "Running", distanceM = null, energyKcal = null,
        energySource = EnergySource.NONE, effort = null, source = WorkoutSource.SYNCED,
        hidden = hidden, note = null,
    )
}
