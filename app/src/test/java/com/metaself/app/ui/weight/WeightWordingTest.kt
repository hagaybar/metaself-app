package com.metaself.app.ui.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.WeightTrend
import com.metaself.app.domain.weight.aFortnight
import com.metaself.app.domain.weight.aReading
import org.junit.jupiter.api.Test

class WeightWordingTest {

    @Test
    fun `the trend is shown to one decimal place, because a scale's last digit is noise`() {
        val points = WeightTrend.of(listOf(aReading(kg = 80.52)))
        assertThat(WeightWording.trend(points)).isEqualTo("80.5 kg")
    }

    @Test
    fun `with nothing logged there is no number to show`() {
        assertThat(WeightWording.trend(emptyList())).isNull()
    }

    @Test
    fun `a fortnight of loss says how much, over how long`() {
        val points = WeightTrend.of(aFortnight())
        assertThat(WeightWording.change(points)).startsWith("Down ")
        assertThat(WeightWording.change(points)).endsWith(" over 13 days")
    }

    @Test
    fun `a gain says gained, not a negative loss`() {
        val points = WeightTrend.of(
            listOf(
                aReading(epochDay = TEST_EPOCH_DAY - 10, kg = 78.0),
                aReading(epochDay = TEST_EPOCH_DAY, kg = 82.0),
            ),
        )
        assertThat(WeightWording.change(points)).startsWith("Up ")
    }

    @Test
    fun `one reading cannot be a change`() {
        assertThat(WeightWording.change(WeightTrend.of(listOf(aReading())))).isNull()
    }

    @Test
    fun `a trend that has barely moved says so rather than reporting nought point nought`() {
        val points = WeightTrend.of(
            listOf(
                aReading(epochDay = TEST_EPOCH_DAY - 10, kg = 80.0),
                aReading(epochDay = TEST_EPOCH_DAY, kg = 80.02),
            ),
        )
        assertThat(WeightWording.change(points)).isEqualTo("Level over 10 days")
    }

    @Test
    fun `the last thing the scale said is stated beside the trend`() {
        val points = WeightTrend.of(
            listOf(
                aReading(epochDay = TEST_EPOCH_DAY - 8, kg = 81.0),
                aReading(epochDay = TEST_EPOCH_DAY, kg = 80.5),
            ),
        )

        assertThat(WeightWording.latestReading(points, TEST_EPOCH_DAY))
            .isEqualTo("Last weighed 80.5 kg, today")
    }

    @Test
    fun `a reading from yesterday says yesterday`() {
        val points = WeightTrend.of(listOf(aReading(epochDay = TEST_EPOCH_DAY - 1, kg = 80.5)))

        assertThat(WeightWording.latestReading(points, TEST_EPOCH_DAY))
            .isEqualTo("Last weighed 80.5 kg, yesterday")
    }

    @Test
    fun `an older reading says how long ago, because a stale trend should look stale`() {
        val points = WeightTrend.of(listOf(aReading(epochDay = TEST_EPOCH_DAY - 9, kg = 80.5)))

        assertThat(WeightWording.latestReading(points, TEST_EPOCH_DAY))
            .isEqualTo("Last weighed 80.5 kg, 9 days ago")
    }

    @Test
    fun `with nothing logged there is no last reading`() {
        assertThat(WeightWording.latestReading(emptyList(), TEST_EPOCH_DAY)).isNull()
    }

    @Test
    fun `a raw reading is shown as it was, because it is a measurement`() {
        assertThat(WeightWording.reading(aReading(kg = 80.52))).isEqualTo("80.5 kg")
    }
}
