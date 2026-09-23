package com.metaself.app.domain.target

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.weight.TrendPoint
import com.metaself.app.domain.weight.WeightReading
import org.junit.jupiter.api.Test

class MeasuredBurnTest {

    private val today = 20_699L

    /**
     * The worked example from D25: 2,150 a day logged, the trend down 1.2 kg over 28 days.
     * 1.2 × 7700 = 9,240 out of storage, ÷ 28 = 330 a day, so a day cost about 2,480.
     */
    @Test
    fun `what a day actually cost`() {
        val burn = MeasuredBurnCalculator.of(
            loggedKcalByDay = everyDay(2_150),
            trend = trendFrom(81.2, 80.0),
            formulaKcal = 2_640,
            todayEpochDay = today,
        )!!

        assertThat(burn.averageLoggedKcal).isEqualTo(2_150)
        assertThat(burn.trendChangeKg).isWithin(1e-9).of(-1.2)
        assertThat(burn.fromStoresKcalPerDay).isEqualTo(330)
        assertThat(burn.measuredKcal).isEqualTo(2_480)
        assertThat(burn.differenceKcal).isEqualTo(-160)
    }

    /** Gaining weight means food went INTO storage, so a day cost less than was eaten. */
    @Test
    fun `a rising trend means the day cost less than was eaten`() {
        val burn = MeasuredBurnCalculator.of(
            loggedKcalByDay = everyDay(2_600),
            trend = trendFrom(80.0, 80.5),
            formulaKcal = 2_400,
            todayEpochDay = today,
        )!!

        assertThat(burn.fromStoresKcalPerDay).isLessThan(0)
        assertThat(burn.measuredKcal).isLessThan(2_600)
    }

    /** The average is of the days that hold food. An unlogged day was not a day of fasting. */
    @Test
    fun `unlogged days are left out of the average rather than counted as nothing`() {
        val someDays = everyDay(2_000).filterKeys { (it - today) % 5L != 0L }

        val burn = MeasuredBurnCalculator.of(
            loggedKcalByDay = someDays,
            trend = trendFrom(80.0, 80.0),
            formulaKcal = 2_000,
            todayEpochDay = today,
        )!!

        assertThat(burn.averageLoggedKcal).isEqualTo(2_000)
        assertThat(burn.daysLogged).isLessThan(28)
    }

    /**
     * Twelve missing days out of twenty-eight makes an average that says more about which days he
     * were logged than about what was eaten.
     */
    @Test
    fun `too few logged days says nothing at all`() {
        val barely = everyDay(2_000).entries.take(MeasuredBurnCalculator.MIN_DAYS_LOGGED - 1)
            .associate { it.key to it.value }

        assertThat(
            MeasuredBurnCalculator.of(barely, trendFrom(80.0, 79.0), 2_400, today),
        ).isNull()
    }

    @Test
    fun `no weight readings says nothing at all`() {
        assertThat(
            MeasuredBurnCalculator.of(everyDay(2_000), emptyList(), 2_400, today),
        ).isNull()
    }

    /** A weight last taken three weeks ago is not a reading of where the window ended. */
    @Test
    fun `a stale weight says nothing at all`() {
        val stale = listOf(
            TrendPoint(WeightReading(today - 27, 81.0), 81.0),
            TrendPoint(WeightReading(today - 20, 80.5), 80.5),
        )

        assertThat(MeasuredBurnCalculator.of(everyDay(2_000), stale, 2_400, today)).isNull()
    }

    /** Days before the window are none of its business. */
    @Test
    fun `food logged before the window is ignored`() {
        val withHistory = everyDay(2_150) + mapOf((today - 200) to 9_000)

        val burn = MeasuredBurnCalculator.of(
            withHistory,
            trendFrom(81.2, 80.0),
            2_640,
            today,
        )!!

        assertThat(burn.averageLoggedKcal).isEqualTo(2_150)
    }

    private fun everyDay(kcal: Int): Map<Long, Int> =
        (0 until MeasuredBurnCalculator.WINDOW_DAYS).associate { (today - it) to kcal }

    /** A reading at each end of the window, smoothed values given directly. */
    private fun trendFrom(startKg: Double, endKg: Double): List<TrendPoint> = listOf(
        TrendPoint(WeightReading(today - 27, startKg), startKg),
        TrendPoint(WeightReading(today, endKg), endKg),
    )
}
