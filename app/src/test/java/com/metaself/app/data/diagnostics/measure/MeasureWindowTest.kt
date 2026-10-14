package com.metaself.app.data.diagnostics.measure

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** D152: three days from the switch, and the hours measured. Invented times. */
class MeasureWindowTest {

    private val start = 1_000_000L
    private val hour = 3_600_000L

    @Test
    fun `on from the switch until 72 hours later, then off`() {
        val until = MeasureWindow.endOf(start)

        assertThat(until - start).isEqualTo(72 * hour)
        assertThat(MeasureWindow.isOn(start, until, start)).isTrue()
        assertThat(MeasureWindow.isOn(start, until, until - 1)).isTrue()
        assertThat(MeasureWindow.isOn(start, until, until)).isFalse()
        assertThat(MeasureWindow.isOn(null, null, start)).isFalse()
    }

    @Test
    fun `hours measured stop at the end, or at the switch turned off`() {
        val until = MeasureWindow.endOf(start)

        assertThat(MeasureWindow.hoursMeasured(start, until, start + 5 * hour)).isEqualTo(5.0)
        assertThat(MeasureWindow.hoursMeasured(start, until, start + 100 * hour)).isEqualTo(72.0)
        assertThat(MeasureWindow.hoursMeasured(start, start + 2 * hour, start + 100 * hour)).isEqualTo(2.0)
        assertThat(MeasureWindow.hoursMeasured(null, null, start)).isNull()
    }
}
