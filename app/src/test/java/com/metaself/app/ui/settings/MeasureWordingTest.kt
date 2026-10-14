package com.metaself.app.ui.settings

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** D152: the switch's line. Invented time. */
class MeasureWordingTest {

    @Test
    fun `on, the line says until when, and off says off`() {
        val until = LocalDateTime.of(2026, 9, 3, 14, 5).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertThat(MeasureWording.status(until, ZoneOffset.UTC)).isEqualTo("On until Thu 3 Sep, 14:05; it turns itself off then.")
        assertThat(MeasureWording.status(null, ZoneOffset.UTC)).isEqualTo("Off.")
    }

    @Test
    fun `the explanation promises timings and counts only, kept on the phone until downloaded`() {
        assertThat(MeasureWording.WHAT).contains("timings and counts only")
        assertThat(MeasureWording.WHAT).contains("until you download it")
        assertThat(MeasureWording.WHAT).contains("never sent anywhere")
    }
}
