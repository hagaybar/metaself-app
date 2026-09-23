package com.metaself.app.domain.goal

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.weight.MeasuredRate
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.ui.goal.GoalWording
import org.junit.jupiter.api.Test

/**
 * Spec §4's invented example: a trend at 80 kg against a target of 72 kg, losing at 0.5 kg a week,
 * starting from a trend of 82 kg, on a fixed "today" of 8 August 2024.
 */
class GoalWordingTest {

    /** The dashed line on the weight chart is labelled with the goal, in the same figures (#15). */
    @Test
    fun `the goal line is labelled as the goal, in whole kilos when it is whole`() {
        assertThat(GoalWording.goalLine(72.0)).isEqualTo("Goal 72 kg")
        assertThat(GoalWording.goalLine(72.5)).isEqualTo("Goal 72.5 kg")
    }

    @Test
    fun `it says how far, and to what`() {
        assertThat(GoalWording.toGo(progress(80.0)))
            .isEqualTo("8 kg to go, to 72 kg")
    }

    /**
     * The rate must appear in the same sentence as the weeks. A bare "about 16 weeks" reads as a
     * promise; the whole point is that it is a division of a distance by a rate he chose.
     *
     * 8 kg / 0.5 = 16 weeks = 112 days after 2024-08-08 = 2024-11-28.
     */
    @Test
    fun `the chosen projection states the rate it assumed, and a month`() {
        assertThat(GoalWording.projection(forecast(80.0)))
            .isEqualTo("At the 0.5 kg a week you're aiming for: about 16 weeks, around November 2024.")
    }

    /**
     * The phrase that prompted all of this. It presupposes something is keeping up, which makes a
     * typed setting wear the grammar of an observation — D4's failure mode reached through tone.
     */
    @Test
    fun `nothing says if it keeps up any more`() {
        val sentences = listOfNotNull(
            GoalWording.projection(forecast(80.0)),
            GoalWording.measured(forecast(80.0, rate(-0.25))),
        )

        assertThat(sentences).hasSize(2)
        sentences.forEach { assertThat(it).doesNotContain("if it keeps up") }
    }

    /** 0.5 kg / 0.5 = 1 week = 7 days after 2024-08-08 = 2024-08-15. */
    @Test
    fun `one week is not one weeks`() {
        assertThat(GoalWording.projection(forecast(72.5)))
            .isEqualTo("At the 0.5 kg a week you're aiming for: about 1 week, around August 2024.")
    }

    /** A month name for something a day or two off is absurdly coarse, so this case survives. */
    @Test
    fun `almost there does not round down to nothing`() {
        assertThat(GoalWording.projection(forecast(72.1)))
            .isEqualTo("Less than a week at 0.5 kg a week.")
    }

    @Test
    fun `a goal reached says so and promises no more weeks`() {
        val reached = forecast(72.0, rate(-0.25))

        assertThat(GoalWording.arrived(progress(72.0)))
            .isEqualTo("You are at your goal weight of 72 kg.")
        assertThat(GoalWording.toGo(progress(72.0))).isNull()
        assertThat(GoalWording.projection(reached)).isNull()
        assertThat(GoalWording.measured(reached)).isNull()
    }

    // --- the measured line: one test per row of the spec's §4 table ---

    /** Row 1. */
    @Test
    fun `no measurement says nothing at all`() {
        assertThat(GoalWording.measured(forecast(80.0, measured = null))).isNull()
    }

    /** Row 2. 8 kg / 0.25 = 32 weeks = 224 days after 2024-08-08 = 2025-03-20. */
    @Test
    fun `a measured rate that reaches the goal gives a finish line`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.25))))
            .isEqualTo(
                "Over the last 28 days you've averaged 0.25 kg a week: " +
                    "about 32 weeks, around March 2025.",
            )
    }

    /** Row 3 — the owner's two-year rule, from the reporting end. 8 / 0.05 = 160 weeks. */
    @Test
    fun `a measured rate that would take over two years states the rate and stops`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.05))))
            .isEqualTo("Over the last 28 days you've averaged 0.05 kg a week.")
    }

    /** Row 4. */
    @Test
    fun `a flat trend says it held steady`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.002))))
            .isEqualTo("Over the last 28 days your trend has held steady.")
    }

    /** Row 5 — a fact about a line, never about him. */
    @Test
    fun `a trend going the wrong way says so of the trend`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(0.1))))
            .isEqualTo("Over the last 28 days your trend is up 0.1 kg a week.")
    }

    /** The span stated is the one observed, never the constant 28. */
    @Test
    fun `the window stated is the span actually measured`() {
        val shortSpan = MeasuredRate(
            spanDays = 21,
            changeKg = -0.75,
            kgPerWeek = -0.25,
            asOfEpochDay = TODAY,
        )

        assertThat(GoalWording.measured(forecast(80.0, shortSpan)))
            .startsWith("Over the last 21 days")
    }

    /**
     * The flat threshold is exactly the value that would print as "0", so that rounding and meaning
     * are one test and no sentence can ever state a rate of zero. 0.0051 prints as 0.01, and 8 kg at
     * that rate is far past two years, so the sentence stops at the rate.
     */
    @Test
    fun `the flat boundary holds on both sides`() {
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.0049))))
            .isEqualTo("Over the last 28 days your trend has held steady.")
        assertThat(GoalWording.measured(forecast(80.0, rate(-0.0051))))
            .isEqualTo("Over the last 28 days you've averaged 0.01 kg a week.")
    }

    /** Nearly there and still moving: a month would be coarser than the answer. 0.1 / 0.25 = 0.4. */
    @Test
    fun `a measured finish under a week says so`() {
        assertThat(GoalWording.measured(forecast(72.1, rate(-0.25))))
            .isEqualTo(
                "Over the last 28 days you've averaged 0.25 kg a week: less than a week to go.",
            )
    }

    /**
     * The register that keeps a factual readout from becoming a reprimand. D22 forbids the app
     * commenting on going the wrong way; D47 states the rate anyway, and this is what protects it.
     */
    @Test
    fun `no measured sentence scolds`() {
        val banned = listOf("only", "just", "still", "behind", "should", "failed")
        // Row 2, row 3, row 4, row 5, and a steeper row 5.
        val rates = listOf(-0.25, -0.05, -0.002, 0.1, 0.5)

        rates.forEach { kgPerWeek ->
            val text = GoalWording.measured(forecast(80.0, rate(kgPerWeek)))!!.lowercase()
            banned.forEach { word -> assertThat(text).doesNotContain(word) }
        }
    }

    /** 82 - 80 = 2 kg since the first reading. */
    @Test
    fun `progress so far is measured from the first reading`() {
        assertThat(GoalWording.done(progress(80.0)))
            .isEqualTo("2 kg down since you started tracking.")
    }

    /** Nothing is said about going the wrong way (D22). */
    @Test
    fun `a weight that went up says nothing about how far it has come`() {
        assertThat(GoalWording.done(progress(83.0))).isNull()
    }

    @Test
    fun `nothing at all without a goal`() {
        assertThat(GoalWording.toGo(null)).isNull()
        assertThat(GoalWording.projection(null)).isNull()
        assertThat(GoalWording.measured(null)).isNull()
        assertThat(GoalWording.done(null)).isNull()
        assertThat(GoalWording.arrived(null)).isNull()
    }

    @Test
    fun `the celebration says what the daily target has become`() {
        val text = GoalWording.celebration(targetKg = 72.0, newDailyKcal = 2450)

        assertThat(text).contains("reached 72 kg")
        assertThat(text).contains("2450 kcal")
    }

    private companion object {
        /** 2024-08-08, so that the month names in the assertions above are stable. */
        const val TODAY = 19_943L

        val GOAL = Goal.lose(kgPerWeek = 0.5, targetKg = 72.0)

        fun progress(nowKg: Double) = GoalProgress.of(
            goal = GOAL,
            trend = listOf(82.0, nowKg).mapIndexed { index, kg ->
                TrendPoint(WeightReading(epochDay = TODAY - 1 + index, kg = kg), trendKg = kg)
            },
        )

        fun forecast(nowKg: Double, measured: MeasuredRate? = null): GoalForecast =
            GoalForecast.of(
                goal = GOAL,
                progress = progress(nowKg)!!,
                measured = measured,
                todayEpochDay = TODAY,
            )

        fun rate(kgPerWeek: Double) = MeasuredRate(
            spanDays = 28,
            changeKg = kgPerWeek / 7.0 * 28.0,
            kgPerWeek = kgPerWeek,
            asOfEpochDay = TODAY,
        )
    }
}
