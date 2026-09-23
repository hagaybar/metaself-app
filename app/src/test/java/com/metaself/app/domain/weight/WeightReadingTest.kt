package com.metaself.app.domain.weight

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WeightReadingTest {

    @Test
    fun `a reading is a day and a number`() {
        val reading = aReading(kg = 80.5)
        assertThat(reading.kg).isEqualTo(80.5)
    }

    @Test
    fun `a weight of zero or less is not a reading`() {
        try {
            aReading(kg = 0.0)
            throw AssertionError("expected a weight of zero to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("weight")
        }
    }

    @Test
    fun `an implausible weight is rejected as a slipped finger`() {
        try {
            aReading(kg = 800.0)
            throw AssertionError("expected an implausible weight to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected).hasMessageThat().contains("weight")
        }
    }
}
