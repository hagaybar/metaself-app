package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.goal.GoalWording
import org.junit.jupiter.api.Test

class GoalWordingTest {

    @Test
    fun `it says how far, and to what`() {
        assertThat(GoalWording.toGo(progress(77.2, target = 70.0)))
            .isEqualTo("7.2 kg to go, to 70 kg")
    }

    /**
     * The rate must appear in the same sentence as the number of weeks. A bare "about 14 weeks"
     * reads as a promise; the whole point is that it is a division of a distance by a rate he chose.
     */
    @Test
    fun `the projection states the rate it assumed`() {
        val text = GoalWording.projection(progress(77.2, target = 70.0))!!

        assertThat(text).contains("About 14 weeks")
        assertThat(text).contains("0.5 kg a week")
    }

    @Test
    fun `one week is not one weeks`() {
        assertThat(GoalWording.projection(progress(70.4, target = 70.0)))
            .isEqualTo("About 1 week at 0.5 kg a week, if it keeps up.")
    }

    @Test
    fun `almost there does not round down to nothing`() {
        assertThat(GoalWording.projection(progress(70.1, target = 70.0)))
            .isEqualTo("Less than a week at 0.5 kg a week.")
    }

    @Test
    fun `a goal reached says so and promises no more weeks`() {
        val reached = progress(70.0, target = 70.0)

        assertThat(GoalWording.arrived(reached)).isEqualTo("You are at your goal weight of 70 kg.")
        assertThat(GoalWording.toGo(reached)).isNull()
        assertThat(GoalWording.projection(reached)).isNull()
    }

    @Test
    fun `progress so far is measured from the first reading`() {
        assertThat(GoalWording.done(progress(77.2, target = 70.0)))
            .isEqualTo("4.8 kg down since you started tracking.")
    }

    /** Nothing is said about going the wrong way (D22). */
    @Test
    fun `a weight that went up says nothing about how far it has come`() {
        assertThat(GoalWording.done(progress(83.0, target = 70.0))).isNull()
    }

    @Test
    fun `nothing at all without a goal`() {
        assertThat(GoalWording.toGo(null)).isNull()
        assertThat(GoalWording.projection(null)).isNull()
        assertThat(GoalWording.done(null)).isNull()
        assertThat(GoalWording.arrived(null)).isNull()
    }

    @Test
    fun `the celebration says what the daily target has become`() {
        val text = GoalWording.celebration(targetKg = 70.0, newDailyKcal = 2450)

        assertThat(text).contains("reached 70 kg")
        assertThat(text).contains("2450 kcal")
    }

    private fun progress(nowKg: Double, target: Double) = GoalProgress.of(
        goal = Goal.lose(kgPerWeek = 0.5, targetKg = target),
        trend = listOf(82.0, nowKg).mapIndexed { index, kg ->
            TrendPoint(WeightReading(epochDay = 20_000L + index, kg = kg), trendKg = kg)
        },
    )
}
