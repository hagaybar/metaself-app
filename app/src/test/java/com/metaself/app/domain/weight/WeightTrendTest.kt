package com.metaself.app.domain.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import org.junit.jupiter.api.Test
import kotlin.math.abs

class WeightTrendTest {

    @Test
    fun `nothing logged is no trend at all, rather than a trend of zero`() {
        assertThat(WeightTrend.of(emptyList())).isEmpty()
    }

    @Test
    fun `the first reading is the trend, because there is nothing to smooth towards`() {
        val trend = WeightTrend.of(listOf(aReading(kg = 80.0)))
        assertThat(trend.single().trendKg).isWithin(0.001).of(80.0)
        assertThat(trend.single().reading.kg).isWithin(0.001).of(80.0)
    }

    @Test
    fun `a single heavy day moves the trend by about a tenth of the jump`() {
        // The case the whole step exists for: a 2 kg salty Sunday must not read as a setback.
        val steady = (0..9).map { aReading(epochDay = TEST_EPOCH_DAY - 10 + it, kg = 80.0) }
        val settled = WeightTrend.of(steady).last().trendKg

        val withSpike = steady + aReading(epochDay = TEST_EPOCH_DAY, kg = 82.0)
        val afterSpike = WeightTrend.of(withSpike).last().trendKg

        assertThat(afterSpike - settled).isWithin(0.05).of(0.2)
    }

    @Test
    fun `a fortnight of real loss is followed, spike and all`() {
        val trend = WeightTrend.of(aFortnight(spikeOnDay = 7))

        assertThat(trend.last().trendKg).isLessThan(trend.first().trendKg)
        // Simulated: 0.52 kg with the spike, 0.63 without. The threshold is set below both with
        // room to spare, because a test that passes by two hundredths is a test that will fail on
        // a change nobody meant to make.
        assertThat(trend.first().trendKg - trend.last().trendKg).isGreaterThan(0.4)
    }

    @Test
    fun `the spike does not lurch the trend`() {
        val withSpike = WeightTrend.of(aFortnight(spikeOnDay = 7)).last().trendKg
        val without = WeightTrend.of(aFortnight(spikeOnDay = null)).last().trendKg

        // Simulated: the two fortnights end 0.11 kg apart. A 2 kg salty Sunday costs the trend
        // about a hundred grams by the end of the fortnight, which is the whole claim of this step.
        assertThat(abs(withSpike - without)).isLessThan(0.35)
    }

    @Test
    fun `a gap counts, so the first reading after a fortnight away moves the trend a long way`() {
        val before = listOf(
            aReading(epochDay = TEST_EPOCH_DAY - 20, kg = 80.0),
            aReading(epochDay = TEST_EPOCH_DAY - 19, kg = 80.0),
        )
        val afterAGap = before + aReading(epochDay = TEST_EPOCH_DAY, kg = 84.0)

        val trend = WeightTrend.of(afterAGap).last().trendKg

        val nextDay = before + aReading(epochDay = TEST_EPOCH_DAY - 18, kg = 84.0)
        val promptTrend = WeightTrend.of(nextDay).last().trendKg

        assertThat(trend).isGreaterThan(promptTrend + 2.0)
    }

    @Test
    fun `readings out of order are put in order before smoothing`() {
        val jumbled = listOf(
            aReading(epochDay = TEST_EPOCH_DAY, kg = 79.0),
            aReading(epochDay = TEST_EPOCH_DAY - 2, kg = 81.0),
            aReading(epochDay = TEST_EPOCH_DAY - 1, kg = 80.0),
        )
        val trend = WeightTrend.of(jumbled)

        assertThat(trend.map { it.reading.epochDay })
            .containsExactly(TEST_EPOCH_DAY - 2, TEST_EPOCH_DAY - 1, TEST_EPOCH_DAY)
            .inOrder()
    }

    @Test
    fun `the change over a window is measured from the trend, never from the raw readings`() {
        val trend = WeightTrend.of(aFortnight(spikeOnDay = 13))

        // The last reading is 2 kg heavy. The change reported must come from the smoothed line, or
        // a salty final day would report a gain across a fortnight of loss.
        assertThat(WeightTrend.changeKg(trend)).isLessThan(0.0)
    }
}
