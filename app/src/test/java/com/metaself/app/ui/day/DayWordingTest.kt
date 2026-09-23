package com.metaself.app.ui.day

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DayWordingTest {

    private val today = LocalDate.of(2026, 9, 3)

    @Test
    fun `today is called today`() {
        assertThat(DayWording.label(today, today)).isEqualTo("Today")
    }

    @Test
    fun `yesterday is called yesterday`() {
        assertThat(DayWording.label(today.minusDays(1), today)).isEqualTo("Yesterday")
    }

    @Test
    fun `tomorrow is called tomorrow`() {
        assertThat(DayWording.label(today.plusDays(1), today)).isEqualTo("Tomorrow")
    }

    @Test
    fun `any other day this year is a weekday and a date, with no year`() {
        assertThat(DayWording.label(LocalDate.of(2026, 8, 26), today))
            .isEqualTo("Wed 26 August")
    }

    @Test
    fun `a day in another year says which, because that is when it matters`() {
        assertThat(DayWording.label(LocalDate.of(2025, 12, 31), today))
            .isEqualTo("Wed 31 December 2025")
    }

    @Test
    fun `the day before yesterday is a date, not 'two days ago'`() {
        // Counting in days stops being readable almost immediately: "4 days ago" makes the reader
        // do arithmetic to work out which day that was.
        assertThat(DayWording.label(today.minusDays(2), today)).isEqualTo("Tue 1 September")
    }
}
