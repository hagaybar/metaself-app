package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.weight.aReading
import org.junit.jupiter.api.Test

/**
 * The quiet grid's arithmetic (public issue #15), with no canvas anywhere near it.
 *
 * TEST_EPOCH_DAY is Thursday 2026-09-03; every date literal below is counted from it by hand and
 * written out, so that a formatter which changed would fail here rather than agree with itself.
 */
class WeightChartLayoutTest {

    // ---- The gridline step ----

    @Test
    fun `the minimum span is gridded every kilo`() {
        assertThat(ChartLayout.gridStepKg(ChartGeometry.MIN_SPAN_KG)).isEqualTo(1)
    }

    @Test
    fun `the step widens only when a finer one would leave more than six gaps`() {
        // Six gaps of 1 kg is the most a 1 kg grid may carry; a hair more moves to 2 kg.
        assertThat(ChartLayout.gridStepKg(6.0)).isEqualTo(1)
        assertThat(ChartLayout.gridStepKg(6.5)).isEqualTo(2)
        assertThat(ChartLayout.gridStepKg(12.0)).isEqualTo(2)
        assertThat(ChartLayout.gridStepKg(12.5)).isEqualTo(5)
        assertThat(ChartLayout.gridStepKg(30.0)).isEqualTo(5)
        assertThat(ChartLayout.gridStepKg(30.5)).isEqualTo(10)
    }

    @Test
    fun `the lines sit on whole multiples of the step, heaviest first`() {
        val lines = ChartLayout.gridLines(lightestKg = 78.4, heaviestKg = 80.4)

        assertThat(lines.map { it.kg }).containsExactly(80, 79).inOrder()
    }

    @Test
    fun `the top line carries the unit and the others are bare figures`() {
        val lines = ChartLayout.gridLines(lightestKg = 78.4, heaviestKg = 80.4)

        assertThat(lines.map { it.label }).containsExactly("80 kg", "79").inOrder()
    }

    @Test
    fun `a wider span is gridded on the wider step, edges included`() {
        // 13 kg is more than six gaps of 2 kg, so the step is 5; 70 is ON the bottom edge and kept.
        val lines = ChartLayout.gridLines(lightestKg = 70.0, heaviestKg = 83.0)

        assertThat(lines.map { it.kg }).containsExactly(80, 75, 70).inOrder()
    }

    @Test
    fun `every axis the chart can draw has at least two gridlines`() {
        // The axis is never narrower than two kilograms, and any two-kilogram stretch holds at least
        // two whole kilos — so a chart never shows a single unlabelled line or none at all.
        listOf(78.0, 78.01, 78.5, 78.99).forEach { lightest ->
            val lines = ChartLayout.gridLines(
                lightestKg = lightest,
                heaviestKg = lightest + ChartGeometry.MIN_SPAN_KG,
            )
            assertThat(lines.size).isAtLeast(2)
        }
    }

    // ---- How much the dots fade ----

    @Test
    fun `dots that do not touch are drawn solid`() {
        assertThat(ChartLayout.dotAlpha(readingsPerDp = 0.1f)).isEqualTo(1f)
        // Exactly touching: one dot every diameter.
        assertThat(ChartLayout.dotAlpha(readingsPerDp = 1f / ChartLayout.DOT_DIAMETER_DP))
            .isEqualTo(1f)
    }

    @Test
    fun `dots fade in proportion as they crowd`() {
        // Twice as many as would touch: each dot is half as strong, so two stacked read as one.
        val touching = 1f / ChartLayout.DOT_DIAMETER_DP

        assertThat(ChartLayout.dotAlpha(readingsPerDp = touching * 2)).isWithin(0.001f).of(0.5f)
        assertThat(ChartLayout.dotAlpha(readingsPerDp = touching * 4)).isWithin(0.001f).of(0.25f)
    }

    @Test
    fun `however crowded, a dot never vanishes`() {
        assertThat(ChartLayout.dotAlpha(readingsPerDp = 50f)).isEqualTo(ChartLayout.MIN_DOT_ALPHA)
    }

    // ---- The dates along the bottom ----

    @Test
    fun `a month start between the ends is labelled`() {
        val ticks = ticks(firstDay = TEST_EPOCH_DAY - 13, lastDay = TEST_EPOCH_DAY, widthPx = 1000f)

        assertThat(ticks.map { it.label }).containsExactly("21 Aug", "Sep", "3 Sep").inOrder()
    }

    @Test
    fun `the ends sit inside the plot, the first from the left and the last from the right`() {
        val ticks = ticks(firstDay = TEST_EPOCH_DAY - 13, lastDay = TEST_EPOCH_DAY, widthPx = 1000f)

        // Labels are ten pixels a character here: "21 Aug" starts at 0, "3 Sep" ends at 1000.
        assertThat(ticks.first().leftPx).isWithin(0.01f).of(0f)
        assertThat(ticks.last().leftPx).isWithin(0.01f).of(1000f - 50f)
    }

    @Test
    fun `a month start that would touch an end label is left out, and the ends are not`() {
        // On a 100 px plot, 1 Sep lands at 84.6 px: "Sep" would run 69.6 to 99.6 and overlap
        // "3 Sep", which runs 50 to 100.
        val ticks = ticks(firstDay = TEST_EPOCH_DAY - 13, lastDay = TEST_EPOCH_DAY, widthPx = 100f)

        assertThat(ticks.map { it.label }).containsExactly("21 Aug", "3 Sep").inOrder()
    }

    @Test
    fun `a first day that is itself a month start is not labelled twice`() {
        // 2026-09-01 is TEST_EPOCH_DAY - 2.
        val ticks = ticks(firstDay = TEST_EPOCH_DAY - 2, lastDay = TEST_EPOCH_DAY, widthPx = 1000f)

        assertThat(ticks.map { it.label }).containsExactly("1 Sep", "3 Sep").inOrder()
    }

    @Test
    fun `every month start that fits is labelled`() {
        // 2026-06-05 (today - 90) to 2026-09-03 crosses July, August and September. 1 Sep is day 88
        // of 90, at 977.8 px: "Sep" would run 962.8 to 992.8, inside "3 Sep" (950 to 1000), so it
        // is the one left out.
        val ticks = ticks(firstDay = TEST_EPOCH_DAY - 90, lastDay = TEST_EPOCH_DAY, widthPx = 1000f)

        assertThat(ticks.map { it.label })
            .containsExactly("5 Jun", "Jul", "Aug", "3 Sep").inOrder()
    }

    @Test
    fun `over a year or more a month start says its year`() {
        // 2025-08-15 (today - 384) to 2026-09-03, on a plot wide enough that only 1 Sep 2026, beside
        // the last date, is crowded out.
        val ticks = ticks(firstDay = TEST_EPOCH_DAY - 384, lastDay = TEST_EPOCH_DAY, widthPx = 10_000f)

        assertThat(ticks.first().label).isEqualTo("Aug 2025")
        assertThat(ticks[1].label).isEqualTo("Sep 2025")
        assertThat(ticks.last().label).isEqualTo("Sep 2026")
    }

    @Test
    fun `one day is one date, in the middle`() {
        val ticks = ticks(firstDay = TEST_EPOCH_DAY, lastDay = TEST_EPOCH_DAY, widthPx = 1000f)

        assertThat(ticks.map { it.label }).containsExactly("3 Sep")
        // "3 Sep" is 50 px wide here, centred on 500.
        assertThat(ticks.single().leftPx).isWithin(0.01f).of(475f)
    }

    // ---- Where the finger is ----

    @Test
    fun `a finger maps to the nearest day, and past either end to that end`() {
        val geometry = twoReadings()

        assertThat(geometry.dayAt(0f)).isEqualTo(TEST_EPOCH_DAY - 10)
        assertThat(geometry.dayAt(1000f)).isEqualTo(TEST_EPOCH_DAY)
        assertThat(geometry.dayAt(500f)).isEqualTo(TEST_EPOCH_DAY - 5)
        // 449 px is 4.49 days along: nearer the fourth day than the fifth.
        assertThat(geometry.dayAt(449f)).isEqualTo(TEST_EPOCH_DAY - 6)
        assertThat(geometry.dayAt(-50f)).isEqualTo(TEST_EPOCH_DAY - 10)
        assertThat(geometry.dayAt(5000f)).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `a day with a reading reads the trend and the scale`() {
        val day = twoReadings().dayOn(TEST_EPOCH_DAY)

        // The trend after ten days' gap: 81 - (1 - 0.9^10) = 81 - 0.651322 = 80.348678.
        assertThat(day.trendKg).isWithin(0.0001).of(80.348678)
        assertThat(day.weighedKg).isEqualTo(80.0)
    }

    @Test
    fun `a day with no reading reads the trend off the line as drawn, and nothing weighed`() {
        val day = twoReadings().dayOn(TEST_EPOCH_DAY - 5)

        // Halfway between 81.0 and 80.348678, which is where the drawn line is on that day.
        assertThat(day.trendKg).isWithin(0.0001).of(80.674339)
        assertThat(day.weighedKg).isNull()
    }

    // ---- The tick under the finger ----

    @Test
    fun `landing on a day with a reading ticks, landing between readings does not`() {
        val days = setOf(TEST_EPOCH_DAY - 10, TEST_EPOCH_DAY)

        assertThat(ChartLayout.crossesReading(null, TEST_EPOCH_DAY, days)).isTrue()
        assertThat(ChartLayout.crossesReading(null, TEST_EPOCH_DAY - 5, days)).isFalse()
    }

    @Test
    fun `moving onto or past a reading ticks, in either direction`() {
        val days = setOf(TEST_EPOCH_DAY - 10, TEST_EPOCH_DAY)

        assertThat(ChartLayout.crossesReading(TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY, days)).isTrue()
        assertThat(ChartLayout.crossesReading(TEST_EPOCH_DAY - 5, TEST_EPOCH_DAY - 11, days)).isTrue()
    }

    @Test
    fun `moving between readings, or leaving one, or staying on one, does not tick`() {
        val days = setOf(TEST_EPOCH_DAY - 10, TEST_EPOCH_DAY)

        assertThat(ChartLayout.crossesReading(TEST_EPOCH_DAY - 6, TEST_EPOCH_DAY - 5, days)).isFalse()
        assertThat(ChartLayout.crossesReading(TEST_EPOCH_DAY, TEST_EPOCH_DAY - 3, days)).isFalse()
        assertThat(ChartLayout.crossesReading(TEST_EPOCH_DAY, TEST_EPOCH_DAY, days)).isFalse()
    }

    // ---- What is said ----

    @Test
    fun `the readout says the day, the trend and what the scale said`() {
        val text = ChartLayout.readout(
            ChartDay(epochDay = TEST_EPOCH_DAY, trendKg = 80.34, weighedKg = 80.12),
            spanDays = 13,
        )

        assertThat(text).isEqualTo("Thu 3 Sep · Trend 80.3 · Weighed 80.1")
    }

    @Test
    fun `the readout says nothing was weighed by leaving it out`() {
        val text = ChartLayout.readout(
            ChartDay(epochDay = TEST_EPOCH_DAY, trendKg = 80.34, weighedKg = null),
            spanDays = 13,
        )

        assertThat(text).isEqualTo("Thu 3 Sep · Trend 80.3")
    }

    @Test
    fun `over a year the readout names the year`() {
        val text = ChartLayout.readout(
            ChartDay(epochDay = TEST_EPOCH_DAY, trendKg = 80.34, weighedKg = null),
            spanDays = 400,
        )

        assertThat(text).isEqualTo("3 Sep 2026 · Trend 80.3")
    }

    @Test
    fun `a screen reader hears the dates and the trend at both ends`() {
        assertThat(ChartLayout.summary(twoReadings()))
            .isEqualTo("Weight chart, 24 Aug to 3 Sep. The trend went from 81.0 kg to 80.3 kg.")
    }

    @Test
    fun `a screen reader hears the goal when it is drawn`() {
        // The readings span 1 kg, so a goal 1 kg under the heavier end stays on the chart.
        val geometry = twoReadings(targetKg = 80.0)

        assertThat(ChartLayout.summary(geometry)).isEqualTo(
            "Weight chart, 24 Aug to 3 Sep. The trend went from 81.0 kg to 80.3 kg. " +
                "The dashed line is the goal weight, 80 kg.",
        )
    }

    @Test
    fun `one day is summarised as one day`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(listOf(aReading(kg = 80.0))),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(ChartLayout.summary(geometry)).isEqualTo("Weight chart, 3 Sep. The trend is 80.0 kg.")
    }

    // ---- Fixtures ----

    /** Two readings ten days apart, 81.0 then 80.0, on a 1000 px plot with no inset. */
    private fun twoReadings(targetKg: Double? = null): ChartGeometry = ChartGeometry.of(
        trend = WeightTrend.of(
            listOf(
                aReading(epochDay = TEST_EPOCH_DAY - 10, kg = 81.0),
                aReading(epochDay = TEST_EPOCH_DAY, kg = 80.0),
            ),
        ),
        widthPx = 1000f,
        heightPx = 400f,
        range = ChartRange.All,
        todayEpochDay = TEST_EPOCH_DAY,
        targetKg = targetKg,
    )!!

    /** Date ticks on a plot running linearly from 0 to [widthPx], ten pixels a character, 8 px apart. */
    private fun ticks(firstDay: Long, lastDay: Long, widthPx: Float): List<DateTick> {
        val days = (lastDay - firstDay).toFloat()
        return ChartLayout.dateTicks(
            firstDay = firstDay,
            lastDay = lastDay,
            xOf = { day -> if (days <= 0f) widthPx / 2f else (day - firstDay) / days * widthPx },
            widthOf = { label -> label.length * 10f },
            gapPx = 8f,
        )
    }
}
