package com.metaself.app.ui.screen.weight

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.weight.aReading
import org.junit.jupiter.api.Test

class WeightFormStateTest {

    @Test
    fun `a typed weight becomes a reading on the chosen day`() {
        val form = WeightFormState(epochDay = TEST_EPOCH_DAY, kg = "80.5")

        assertThat(form.error()).isNull()
        assertThat(form.toReading()?.kg).isEqualTo(80.5)
        assertThat(form.toReading()?.epochDay).isEqualTo(TEST_EPOCH_DAY)
    }

    @Test
    fun `an empty form is not a reading`() {
        assertThat(WeightFormState(epochDay = TEST_EPOCH_DAY).toReading()).isNull()
    }

    @Test
    fun `something that is not a number is refused`() {
        assertThat(WeightFormState(epochDay = TEST_EPOCH_DAY, kg = "heavy").error()).isNotNull()
    }

    @Test
    fun `an implausible weight is refused as a slipped finger`() {
        assertThat(WeightFormState(epochDay = TEST_EPOCH_DAY, kg = "0").error()).isNotNull()
        assertThat(WeightFormState(epochDay = TEST_EPOCH_DAY, kg = "800").error()).isNotNull()
    }

    @Test
    fun `a form can be built from a reading, for correcting one`() {
        val form = WeightFormState.from(aReading(kg = 80.5))

        assertThat(form.kg).isEqualTo("80.5")
        assertThat(form.epochDay).isEqualTo(TEST_EPOCH_DAY)
    }

    /**
     * "NaN" reads as a number, and both range comparisons are false for it, so it passed the check
     * and crashed Save when the reading refused it (issue #32). It is not a number, and is told so.
     * "Infinity" was, and is, refused by the range — the message that names the range.
     */
    @Test
    fun `NaN is not a weight, and saving it does not crash`() {
        val nan = WeightFormState(epochDay = TEST_EPOCH_DAY, kg = "NaN")

        assertThat(nan.error()).isEqualTo("A number, like 80.5")
        assertThat(nan.toReading()).isNull()

        val infinite = WeightFormState(epochDay = TEST_EPOCH_DAY, kg = "Infinity")
        assertThat(infinite.error()).isEqualTo("A weight between 20 and 400 kg")
        assertThat(infinite.toReading()).isNull()
    }
}
