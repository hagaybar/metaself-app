package com.metaself.app.data.time

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TodayTest {

    @Test
    fun `a fixed today reports the date it was given`() {
        val today = Today { LocalDate.of(2026, 9, 3) }
        assertThat(today().toEpochDay()).isEqualTo(LocalDate.of(2026, 9, 3).toEpochDay())
    }

    @Test
    fun `the current year is derived from today, so there is only one clock`() {
        val today = Today { LocalDate.of(2026, 9, 3) }
        assertThat(today.asCurrentYear()()).isEqualTo(2026)
    }
}
