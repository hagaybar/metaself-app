package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

/**
 * Spec §4's invented example throughout: a trend at 80 kg against a target of 72 kg, losing at
 * 0.5 kg a week. 80 - 72 = 8 kg to go.
 */
class GoalForecastTest {

    /** 8 kg at 0.5 a week = 16 weeks = 112 days. */
    @Test
    fun `the chosen projection is the distance over the chosen rate`() {
        val forecast = forecast(nowKg = 80.0, measured = null)

        assertThat(forecast.chosenKgPerWeek).isWithin(1e-9).of(0.5)
        assertThat(forecast.chosenWeeks!!).isWithin(1e-9).of(16.0)
        assertThat(forecast.chosenFinishEpochDay).isEqualTo(TODAY + 112)
    }

    /** A destination with no speed has no arrival date and must not be given one. */
    @Test
    fun `a target with no rate gets no chosen projection`() {
        val goal = Goal(direction = GoalDirection.LOSE, kgPerWeek = 0.0, targetKg = 72.0)
        val forecast = GoalForecast.of(
            goal = goal,
            progress = GoalProgress.of(goal, trendOf(82.0, 80.0))!!,
            measured = null,
            todayEpochDay = TODAY,
        )

        assertThat(forecast.chosenWeeks).isNull()
        assertThat(forecast.chosenFinishEpochDay).isNull()
    }

    /** 8 kg at a measured 0.25 a week = 32 weeks = 224 days. */
    @Test
    fun `losing weight against a LOSE goal is closing the distance`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -0.25))

        assertThat(forecast.measuredTowardGoalKgPerWeek!!).isWithin(1e-9).of(0.25)
        assertThat(forecast.measuredWeeks!!).isWithin(1e-9).of(32.0)
        assertThat(forecast.measuredFinishEpochDay).isEqualTo(TODAY + 224)
    }

    /** Going the wrong way gets no finish line, and the measurement is still carried. */
    @Test
    fun `gaining against a LOSE goal gets no measured finish line`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = 0.1))

        assertThat(forecast.measured).isNotNull()
        assertThat(forecast.measuredTowardGoalKgPerWeek!!).isWithin(1e-9).of(-0.1)
        assertThat(forecast.measuredWeeks).isNull()
        assertThat(forecast.measuredFinishEpochDay).isNull()
    }

    /**
     * The same rising trend against a GAIN goal IS progress. 75 - 63 = 12 kg to go, and the
     * expectation is written as that division so it cannot be left behind if the fixture moves.
     */
    @Test
    fun `gaining against a GAIN goal is closing the distance`() {
        val goal = Goal.gain(kgPerWeek = 0.25, targetKg = 75.0)
        val forecast = GoalForecast.of(
            goal = goal,
            progress = GoalProgress.of(goal, trendOf(62.0, 63.0))!!,
            measured = rate(kgPerWeek = 0.25),
            todayEpochDay = TODAY,
        )

        assertThat(forecast.measuredTowardGoalKgPerWeek!!).isWithin(1e-9).of(0.25)
        assertThat(forecast.measuredWeeks!!).isWithin(1e-9).of(12.0 / 0.25)
    }

    /**
     * The owner's two-year rule, at its exact boundary. At 8 kg to go, a finish line landing
     * exactly 104 weeks out needs 8/104 kg a week — about 0.077 — and the rate is written as that
     * division rather than as a decimal so the test cannot drift from the fixture it derives from.
     * (8.0 / (8.0 / 104.0) is exactly 104.0 in IEEE doubles, so `<=` holds.)
     */
    @Test
    fun `a finish line exactly two years out is still given`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -8.0 / 104.0))

        assertThat(forecast.measuredWeeks!!).isWithin(1e-6).of(104.0)
    }

    /** 8 kg at 0.07 a week is about 114 weeks: past two years, so no finish line. */
    @Test
    fun `a finish line past two years is withheld`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -0.07))

        assertThat(forecast.measured).isNotNull()
        assertThat(forecast.measuredWeeks).isNull()
        assertThat(forecast.measuredFinishEpochDay).isNull()
    }

    /** Flat is tested before direction: -0.002 is "toward" by the sign and steady by any reading. */
    @Test
    fun `a trend flat to the rounding shown gets no finish line`() {
        val forecast = forecast(nowKg = 80.0, measured = rate(kgPerWeek = -0.002))

        assertThat(forecast.measuredWeeks).isNull()
    }

    @Test
    fun `no measurement leaves every measured field null`() {
        val forecast = forecast(nowKg = 80.0, measured = null)

        assertThat(forecast.measured).isNull()
        assertThat(forecast.measuredTowardGoalKgPerWeek).isNull()
        assertThat(forecast.measuredWeeks).isNull()
        assertThat(forecast.measuredFinishEpochDay).isNull()
    }

    /**
     * Arriving has its own sentence; neither projection may follow it.
     *
     * The measurement itself is still CARRIED — the trend went on moving — which is exactly why
     * `arrived` has to travel on the forecast: without it the wording would print a measured
     * sentence underneath the arrival announcement.
     */
    @Test
    fun `an arrived goal projects nothing at all`() {
        val forecast = forecast(nowKg = 72.0, measured = rate(kgPerWeek = -0.25))

        assertThat(forecast.arrived).isTrue()
        assertThat(forecast.measured).isNotNull()
        assertThat(forecast.chosenWeeks).isNull()
        assertThat(forecast.measuredWeeks).isNull()
    }

    private companion object {
        /** 2024-08-08, spec §4's fixed "today". */
        const val TODAY = 19_943L

        fun forecast(nowKg: Double, measured: MeasuredRate?): GoalForecast {
            val goal = Goal.lose(kgPerWeek = 0.5, targetKg = 72.0)
            return GoalForecast.of(
                goal = goal,
                progress = GoalProgress.of(goal, trendOf(82.0, nowKg))!!,
                measured = measured,
                todayEpochDay = TODAY,
            )
        }

        fun rate(kgPerWeek: Double) = MeasuredRate(
            spanDays = 28,
            changeKg = kgPerWeek / 7.0 * 28.0,
            kgPerWeek = kgPerWeek,
            asOfEpochDay = TODAY,
        )

        fun trendOf(vararg trendKg: Double): List<TrendPoint> =
            trendKg.mapIndexed { index, kg ->
                TrendPoint(
                    reading = WeightReading(epochDay = TODAY - 1 + index, kg = kg),
                    trendKg = kg,
                )
            }
    }
}
