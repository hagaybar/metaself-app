package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.WeightReading
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.weight.aFortnight
import com.metaself.app.domain.weight.aReading
import org.junit.jupiter.api.Test

/**
 * The chart's arithmetic, with no canvas anywhere near it.
 *
 * Every call names its arguments. `of` has two parameters with defaults in the middle of its list
 * (`insetPx`, `targetKg`), so whichever end the two new ones are added at, named arguments keep
 * every call here reading the same and keep the implementation free to order them either way.
 *
 * TEST_EPOCH_DAY is 2026-09-03, which is where every date string below comes from. The dates are
 * written out as literals rather than computed, so that a formatter which quietly changed would
 * fail here instead of agreeing with itself.
 */
class WeightChartTest {

    // ---- The range filter (C1, C7) ----

    @Test
    fun `a range keeps only the readings inside it`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aYear()),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // A month is the 30 days ending today inclusive: today - 29 .. today.
        assertThat(geometry.points).hasSize(30)
        assertThat(geometry.startLabel).isEqualTo("5 Aug")
        assertThat(geometry.endLabel).isEqualTo("3 Sep")
    }

    @Test
    fun `the newest reading is still the right-hand edge of a narrowed range`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aYear()),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // Narrowing must re-scale the horizontal axis to what is left, not leave a month's worth of
        // line hanging in the last twelfth of a chart drawn for a year.
        assertThat(geometry.points.first().x).isWithin(0.01f).of(0f)
        assertThat(geometry.points.last().x).isWithin(0.01f).of(1000f)
    }

    @Test
    fun `a reading on the edge of the range is kept and the one before it is not`() {
        // The boundary is stated here and nowhere else: a month is today - 29 .. today, so the
        // reading on today - 29 is in and the one on today - 30 is out.
        val readings = listOf(
            aReading(epochDay = TEST_EPOCH_DAY - 30, kg = 80.0),
            aReading(epochDay = TEST_EPOCH_DAY - 29, kg = 79.5),
        )

        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(readings),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.points).hasSize(1)
        // 2026-08-05 is today - 29; the dropped reading was 2026-08-04.
        assertThat(geometry.startLabel).isEqualTo("5 Aug")
    }

    @Test
    fun `a range wider than the whole history shows everything`() {
        val trend = WeightTrend.of(aFortnight())

        val year = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Year,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!
        val all = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(year.points).isEqualTo(all.points)
        assertThat(year.startLabel).isEqualTo(all.startLabel)
        assertThat(year.endLabel).isEqualTo(all.endLabel)
    }

    @Test
    fun `a range containing no readings draws nothing`() {
        // The geometry says "nothing to draw" the same way an empty history does. What to SAY about
        // it is the screen's business, not this function's.
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aFortnight(startDay = TEST_EPOCH_DAY - 103)),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )

        assertThat(geometry).isNull()
    }

    @Test
    fun `an empty history plots nothing`() {
        assertThat(
            ChartGeometry.of(
                trend = emptyList(),
                widthPx = 1000f,
                heightPx = 400f,
                range = ChartRange.All,
                todayEpochDay = TEST_EPOCH_DAY,
            ),
        ).isNull()
    }

    @Test
    fun `a single reading in a range is plotted in the middle rather than dividing by zero`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(listOf(aReading(kg = 80.0))),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.points).hasSize(1)
        assertThat(geometry.points.single().y).isWithin(0.01f).of(200f)
        // One day is a span of zero days, and dividing the width by it is the crash this guards.
        assertThat(geometry.points.single().x).isWithin(0.01f).of(500f)
    }

    // ---- The date labels (C3, C7) ----

    @Test
    fun `the labels are the two ends of what is shown, and nothing between`() {
        val trend = WeightTrend.of(aRun(days = 90))

        val all = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!
        val month = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(all.startLabel).isEqualTo("6 Jun")
        assertThat(all.endLabel).isEqualTo("3 Sep")

        // Narrowing moves the left-hand label. The right-hand one does not move, and must not: the
        // newest reading is the right edge of every range that contains it.
        assertThat(month.startLabel).isEqualTo("5 Aug")
        assertThat(month.endLabel).isEqualTo("3 Sep")
        assertThat(month.startLabel).isNotEqualTo(all.startLabel)
    }

    @Test
    fun `a fortnight says the day and the month`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aFortnight()),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.startLabel).isEqualTo("21 Aug")
        assertThat(geometry.endLabel).isEqualTo("3 Sep")
    }

    @Test
    fun `several years say the month and the year`() {
        // A day number in a three-year view is noise; the month and the year are what locate it.
        val readings = listOf(
            aReading(epochDay = TEST_EPOCH_DAY - 1095, kg = 95.0),
            aReading(epochDay = TEST_EPOCH_DAY, kg = 80.0),
        )

        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(readings),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.startLabel).isEqualTo("Sep 2023")
        assertThat(geometry.endLabel).isEqualTo("Sep 2026")
    }

    @Test
    fun `a range holding one day has one label, not the same date twice`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(listOf(aReading(kg = 80.0))),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.startLabel).isEqualTo("3 Sep")
        assertThat(geometry.endLabel).isNull()
    }

    @Test
    fun `the labels follow the readings, not the chip`() {
        // A year's chip over a fortnight's data draws a fortnight, so it reads as a fortnight. Keyed
        // off the chosen range instead, this would print "Sep 2026" at both ends of two weeks.
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aFortnight()),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Year,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.startLabel).isEqualTo("21 Aug")
        assertThat(geometry.endLabel).isEqualTo("3 Sep")
    }

    // ---- The goal line dropping out (C4) ----

    @Test
    fun `a goal far outside the range shown drops off the chart`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aNarrowMonth()),
            widthPx = 1000f,
            heightPx = 400f,
            targetKg = 70.0,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // A month of 400 g with the goal ten kilograms away: keeping the goal would balloon the axis
        // tenfold and flatten the readings — the thing the chart is for — into a corner.
        assertThat(geometry.targetY).isNull()
    }

    @Test
    fun `a goal near what is shown stays on the chart`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aNarrowMonth()),
            widthPx = 1000f,
            heightPx = 400f,
            targetKg = 79.0,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        val targetY = geometry.targetY
        assertThat(targetY).isNotNull()
        assertThat(targetY!!).isAtLeast(0f)
        assertThat(targetY).isAtMost(400f)
    }

    @Test
    fun `dropping the goal re-fits the axis to the readings`() {
        val trend = WeightTrend.of(aNarrowMonth())

        val withFarGoal = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            targetKg = 70.0,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!
        val withNoGoal = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            targetKg = null,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // This is the point of dropping it: the readings get the height back. A goal that merely
        // stopped being DRAWN while still stretching the axis would fix nothing.
        assertThat(withFarGoal.heaviestKg).isEqualTo(withNoGoal.heaviestKg)
        assertThat(withFarGoal.lightestKg).isEqualTo(withNoGoal.lightestKg)
    }

    @Test
    fun `over the whole history a distant goal is still drawn`() {
        // Four kilograms below the lightest reading, on an axis already covering eight. The rule is
        // relative to what is shown, not a fixed number of kilograms.
        val readings = listOf(
            aReading(epochDay = TEST_EPOCH_DAY - 364, kg = 90.0),
            aReading(epochDay = TEST_EPOCH_DAY, kg = 80.0),
        )

        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(readings),
            widthPx = 1000f,
            heightPx = 400f,
            targetKg = 76.0,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.targetY).isNotNull()
    }

    // ---- The minimum-span guard, per range (C6) ----

    @Test
    fun `the minimum span still applies inside a narrow range`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aNarrowMonth()),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // 400 g on an axis covering at least 2 kg: no more than a quarter of the height. A narrow
        // range makes this matter more, not less — it is the whole month's movement being drawn.
        val trendDrop = geometry.points.last().trendY - geometry.points.first().trendY
        assertThat(trendDrop).isLessThan(400f / 4f)
    }

    @Test
    fun `narrowing the range re-fits the axis to what is shown`() {
        val trend = WeightTrend.of(aYearEndingQuietly())

        val all = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!
        val month = ChartGeometry.of(
            trend = trend,
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.Month,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // The year covers 6 kg; its last month covers about 1, so the month's axis closes in — and
        // stops at the two-kilogram floor rather than going on closing.
        assertThat(month.axisSpanKg()).isLessThan(all.axisSpanKg())
        assertThat(month.axisSpanKg()).isWithin(1e-9).of(ChartGeometry.MIN_SPAN_KG)
    }

    // ---- The eight that were here before, unchanged except for the two new arguments ----

    @Test
    fun `the oldest reading is on the left and the newest on the right`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aFortnight()),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.points.first().x).isWithin(0.01f).of(0f)
        assertThat(geometry.points.last().x).isWithin(0.01f).of(1000f)
        assertThat(geometry.points.first().x).isLessThan(geometry.points.last().x)
    }

    @Test
    fun `the heaviest reading is at the top, because a chart of weight reads downwards`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(
                listOf(
                    aReading(epochDay = TEST_EPOCH_DAY - 1, kg = 82.0),
                    aReading(epochDay = TEST_EPOCH_DAY, kg = 78.0),
                ),
            ),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // Canvas y grows downwards, so the heavier reading has the SMALLER y.
        assertThat(geometry.points.first().y).isLessThan(geometry.points.last().y)
    }

    @Test
    fun `a fortnight of identical readings is a flat line, not a divide by zero`() {
        val flat = (0..13).map { aReading(epochDay = TEST_EPOCH_DAY - 13 + it, kg = 80.0) }

        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(flat),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.points.map { it.y }.toSet()).hasSize(1)
    }

    @Test
    fun `the axis covers what the readings need`() {
        // A fortnight spanning 80.0 down to 78.7 is 1.3 kg, which is less than the minimum span, so
        // the axis widens to two kilograms centred on the data: 80.35 down to 78.35.
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(aFortnight(startKg = 80.0, dailyChangeKg = -0.1)),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.heaviestKg).isWithin(1e-9).of(80.35)
        assertThat(geometry.lightestKg).isWithin(1e-9).of(78.35)
    }

    @Test
    fun `a small change does not fill the whole chart and look like a cliff`() {
        // Two readings half a kilogram apart once drew a line from the very top corner to the very
        // bottom, which looks like a collapse and is 81.0 to 80.5.
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(
                listOf(
                    aReading(epochDay = TEST_EPOCH_DAY - 8, kg = 81.0),
                    aReading(epochDay = TEST_EPOCH_DAY, kg = 80.5),
                ),
            ),
            widthPx = 1000f,
            heightPx = 400f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        // The trend moved 0.4 kg on an axis covering at least 2 kg, so it must use no more than a
        // quarter of the height.
        val trendDrop = geometry.points.last().trendY - geometry.points.first().trendY
        assertThat(trendDrop).isLessThan(400f / 4f)
    }

    @Test
    fun `points are held inside the edges so a dot is not half-clipped`() {
        val geometry = ChartGeometry.of(
            trend = WeightTrend.of(
                listOf(
                    aReading(epochDay = TEST_EPOCH_DAY - 8, kg = 95.0),
                    aReading(epochDay = TEST_EPOCH_DAY, kg = 85.0),
                ),
            ),
            widthPx = 1000f,
            heightPx = 400f,
            insetPx = 12f,
            range = ChartRange.All,
            todayEpochDay = TEST_EPOCH_DAY,
        )!!

        assertThat(geometry.points.first().x).isAtLeast(12f)
        assertThat(geometry.points.last().x).isAtMost(1000f - 12f)
        assertThat(geometry.points.minOf { it.y }).isAtLeast(12f)
        assertThat(geometry.points.maxOf { it.y }).isAtMost(400f - 12f)
    }

    // ---- Fixtures ----

    /** A daily reading for each of the [days] ending today, drifting gently down. */
    private fun aRun(days: Int, startKg: Double = 86.0): List<WeightReading> =
        (0 until days).map { n ->
            aReading(
                epochDay = TEST_EPOCH_DAY - (days - 1) + n,
                kg = startKg - 0.01 * n,
            )
        }

    private fun aYear(): List<WeightReading> = aRun(days = 365)

    /** A year that loses 5 kg slowly and then only 1 kg across its final month. */
    private fun aYearEndingQuietly(): List<WeightReading> = (0..364).map { n ->
        val kg = if (n <= 334) 86.0 - 5.0 * n / 334.0 else 81.0 - 1.0 * (n - 334) / 30.0
        aReading(epochDay = TEST_EPOCH_DAY - 364 + n, kg = kg)
    }

    /**
     * A month in which the weight moved 400 g — the case the minimum span exists for.
     *
     * The two goal tests below are written against this band: 80.4 down to 80.0. Everything the
     * chart computes from it is a DIFFERENCE, so the two targets are quoted as distances from this
     * band and not as figures of their own — 70.0 is ten kilograms under it, and 79.0 is one.
     */
    private fun aNarrowMonth(): List<WeightReading> = listOf(
        aReading(epochDay = TEST_EPOCH_DAY - 29, kg = 80.4),
        aReading(epochDay = TEST_EPOCH_DAY - 15, kg = 80.2),
        aReading(epochDay = TEST_EPOCH_DAY, kg = 80.0),
    )

    /** How many kilograms the vertical axis covers. */
    private fun ChartGeometry.axisSpanKg(): Double = heaviestKg - lightestKg
}
