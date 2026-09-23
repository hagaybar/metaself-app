package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

class GoalProgressTest {

    @Test
    fun `how far there is to go, and how long at the chosen rate`() {
        val progress = GoalProgress.of(
            goal = Goal.lose(kgPerWeek = 0.5, targetKg = 70.0),
            trend = trendOf(82.0, 77.2),
        )!!

        // Nothing here is a measurement: 82 -> 70 is an invented ladder. 77.2 - 70 = 7.2 to go, at
        // 0.5 a week = 14.4 weeks, and 82 - 77.2 = 4.8 done.
        assertThat(progress.toGoKg).isWithin(1e-9).of(7.2)
        assertThat(progress.weeksToGo).isWithin(1e-9).of(14.4)
        assertThat(progress.doneKg).isWithin(1e-9).of(4.8)
        assertThat(progress.arrived).isFalse()
    }

    @Test
    fun `reaching the target leaves nothing to go and no projection`() {
        val progress = GoalProgress.of(
            goal = Goal.lose(kgPerWeek = 0.5, targetKg = 70.0),
            trend = trendOf(82.0, 70.0),
        )!!

        assertThat(progress.arrived).isTrue()
        assertThat(progress.toGoKg).isEqualTo(0.0)
        assertThat(progress.weeksToGo).isNull()
    }

    /** Overshooting is arriving, not a negative distance. */
    @Test
    fun `going past the target is still arriving`() {
        val progress = GoalProgress.of(
            goal = Goal.lose(kgPerWeek = 0.5, targetKg = 70.0),
            trend = trendOf(82.0, 68.2),
        )!!

        assertThat(progress.arrived).isTrue()
        assertThat(progress.toGoKg).isEqualTo(0.0)
    }

    @Test
    fun `a gain counts upwards`() {
        val progress = GoalProgress.of(
            goal = Goal.gain(kgPerWeek = 0.25, targetKg = 75.0),
            trend = trendOf(62.0, 63.0),
        )!!

        // 75 - 63 = 12 to go, at 0.25 a week = 48 weeks. A worked number that is not recomputed
        // with the value it derives from is the defect this project has shipped twice.
        assertThat(progress.toGoKg).isWithin(1e-9).of(12.0)
        assertThat(progress.doneKg).isWithin(1e-9).of(1.0)
        assertThat(progress.weeksToGo).isWithin(1e-9).of(48.0)
    }

    /** Going the wrong way is not negative progress. The app has no opinion about it (D22). */
    @Test
    fun `moving away from the goal is nothing done, not something undone`() {
        val progress = GoalProgress.of(
            goal = Goal.lose(kgPerWeek = 0.5, targetKg = 70.0),
            trend = trendOf(80.0, 81.0),
        )!!

        assertThat(progress.doneKg).isEqualTo(0.0)
        assertThat(progress.toGoKg).isWithin(1e-9).of(11.0)
    }

    @Test
    fun `nothing to say without a destination`() {
        assertThat(GoalProgress.of(Goal.lose(0.5), trendOf(82.0, 80.0))).isNull()
        assertThat(GoalProgress.of(Goal.hold(), trendOf(82.0, 80.0))).isNull()
    }

    @Test
    fun `nothing to say with no readings at all`() {
        assertThat(GoalProgress.of(Goal.lose(0.5, 70.0), emptyList())).isNull()
    }

    /** A destination with no speed is a place, not a plan. It gets a distance and no date. */
    @Test
    fun `a target with no rate gets no projection`() {
        val progress = GoalProgress.of(
            goal = Goal(
                direction = com.metaself.app.domain.profile.GoalDirection.LOSE,
                kgPerWeek = 0.0,
                targetKg = 70.0,
            ),
            trend = trendOf(82.0, 80.0),
        )!!

        assertThat(progress.toGoKg).isWithin(1e-9).of(10.0)
        assertThat(progress.weeksToGo).isNull()
    }

    private fun trendOf(vararg trendKg: Double): List<TrendPoint> =
        trendKg.mapIndexed { index, kg ->
            TrendPoint(
                reading = WeightReading(epochDay = 20_000L + index, kg = kg),
                trendKg = kg,
            )
        }
}
