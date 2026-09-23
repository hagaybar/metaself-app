package com.metaself.app.domain.weight

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MeasuredRateTest {

    /** 2.8 kg down across 28 days is 0.7 kg a week, and the sign says downwards. */
    @Test
    fun `a falling trend measures a negative rate`() {
        val rate = MeasuredRate.of(trend(fromDaysAgo = 28, changeKg = -2.8), todayEpochDay = TODAY)!!

        assertThat(rate.spanDays).isEqualTo(28)
        assertThat(rate.changeKg).isWithin(1e-9).of(-2.8)
        assertThat(rate.kgPerWeek).isWithin(1e-9).of(-0.7)
        assertThat(rate.asOfEpochDay).isEqualTo(TODAY)
    }

    /** A measurement of a line has no opinion about which way the owner wanted to go. */
    @Test
    fun `a rising trend measures a positive rate`() {
        val rate = MeasuredRate.of(trend(fromDaysAgo = 28, changeKg = 1.0), todayEpochDay = TODAY)!!

        // 1 kg over 28 days = 1/4 kg a week.
        assertThat(rate.kgPerWeek).isWithin(1e-9).of(0.25)
    }

    /**
     * The divisor is the span actually observed, NOT the 28-day window. The same 2.8 kg over 21
     * days is a faster rate, and a calculator dividing by a constant would report them identical.
     *
     * The span runs from 24 days ago — the latest a start point may sit, GAP_DAYS after the window
     * opens — to 3 days ago, within GAP_DAYS of today.
     */
    @Test
    fun `the divisor is the observed span and not the window`() {
        val short = MeasuredRate.of(
            trend(fromDaysAgo = 24, toDaysAgo = 3, changeKg = -2.8),
            todayEpochDay = TODAY,
        )!!

        assertThat(short.spanDays).isEqualTo(21)
        assertThat(short.kgPerWeek).isWithin(1e-9).of(-2.8 / 21.0 * 7.0)
        assertThat(short.asOfEpochDay).isEqualTo(TODAY - 3)
    }

    /**
     * A fortnight of history has no point near the window's start, so there is nothing to measure
     * from. (MIN_SPAN_DAYS would refuse it too, but cannot be reached first at today's constants:
     * see its KDoc.)
     */
    @Test
    fun `a fortnight of history measures nothing`() {
        assertThat(MeasuredRate.of(trend(fromDaysAgo = 13, changeKg = -1.0), TODAY)).isNull()
    }

    /** A rate that ended a fortnight ago is not a rate he is managing now. */
    @Test
    fun `a stale last reading measures nothing`() {
        val stale = trend(fromDaysAgo = 28, changeKg = -2.8).map {
            it.copy(reading = it.reading.copy(epochDay = it.reading.epochDay - 5))
        }

        assertThat(MeasuredRate.of(stale, todayEpochDay = TODAY)).isNull()
    }

    @Test
    fun `nothing at all with no readings`() {
        assertThat(MeasuredRate.of(emptyList(), TODAY)).isNull()
    }

    /** One reading is a weight, not a rate. */
    @Test
    fun `a single reading measures nothing`() {
        val one = listOf(TrendPoint(WeightReading(epochDay = TODAY, kg = 80.0), trendKg = 80.0))

        assertThat(MeasuredRate.of(one, TODAY)).isNull()
    }

    private companion object {
        const val TODAY = 20_000L

        /** A straight line of daily points from [fromDaysAgo] to [toDaysAgo], moving [changeKg]. */
        fun trend(fromDaysAgo: Int, toDaysAgo: Int = 0, changeKg: Double): List<TrendPoint> {
            val days = fromDaysAgo - toDaysAgo
            val startDay = TODAY - fromDaysAgo
            val startKg = 80.0
            return (0..days).map { n ->
                val kg = startKg + changeKg * n / days
                TrendPoint(WeightReading(epochDay = startDay + n, kg = kg), trendKg = kg)
            }
        }
    }
}
