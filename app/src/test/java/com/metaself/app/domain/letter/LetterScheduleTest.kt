package com.metaself.app.domain.letter

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/** Design questions 4–6. Dates invented (September 2026: the 6th is a Sunday). */
class LetterScheduleTest {

    private val sunday = LocalDateTime.of(2026, 9, 6, 0, 0)

    @Test
    fun `the next run is this Sunday at the hour, or next Sunday once it has passed`() {
        assertThat(LetterSchedule.nextRun(LocalDateTime.of(2026, 9, 3, 12, 0), 20)).isEqualTo(sunday.withHour(20))
        assertThat(LetterSchedule.nextRun(sunday.withHour(19), 20)).isEqualTo(sunday.withHour(20))
        assertThat(LetterSchedule.nextRun(sunday.withHour(20), 20)).isEqualTo(sunday.plusDays(7).withHour(20))
    }

    @Test
    fun `the week written is Sunday's, from the hour until Monday noon, and none otherwise`() {
        val monday = java.time.LocalDate.of(2026, 8, 31).toEpochDay()
        assertThat(LetterSchedule.weekToWrite(sunday.withHour(20), 20)).isEqualTo(monday)
        assertThat(LetterSchedule.weekToWrite(sunday.plusDays(1).withHour(11), 20)).isEqualTo(monday)
        assertThat(LetterSchedule.weekToWrite(sunday.withHour(19), 20)).isNull()
        assertThat(LetterSchedule.weekToWrite(sunday.plusDays(2).withHour(9), 20)).isNull()
        assertThat(LetterSchedule.weekToWrite(sunday.plusDays(1).withHour(12), 20)).isNull()
        assertThat(LetterSchedule.weekToWrite(sunday.minusDays(1).withHour(22), 20)).isNull()
    }

    @Test
    fun `a Saturday night's next run is the next day, and the latest hour still falls on Sunday`() {
        assertThat(LetterSchedule.nextRun(sunday.minusDays(1).withHour(23).withMinute(59), 23)).isEqualTo(sunday.withHour(23))
        assertThat(LetterSchedule.nextRun(sunday.withHour(23).withMinute(30), 23)).isEqualTo(sunday.plusDays(7).withHour(23))
    }

    @Test
    fun `the run after a Sunday's run is the next Sunday, even when that run started a moment before its hour`() {
        val monday = java.time.LocalDate.of(2026, 8, 31).toEpochDay()
        val next = sunday.plusDays(7).withHour(20)
        assertThat(LetterSchedule.nextRunAfter(sunday.withHour(19).withMinute(59).withSecond(55), monday, 20)).isEqualTo(next)
        assertThat(LetterSchedule.nextRunAfter(sunday.withHour(20), monday, 20)).isEqualTo(next)
        assertThat(LetterSchedule.nextRunAfter(sunday.plusDays(1).withHour(11), monday, 20)).isEqualTo(next)
        assertThat(LetterSchedule.nextRunAfter(sunday.withHour(20), monday, 22)).isEqualTo(sunday.plusDays(7).withHour(22))
        // Without the week, a run a moment early would name its own Sunday — the case this rules out.
        assertThat(LetterSchedule.nextRun(sunday.withHour(19).withMinute(59).withSecond(55), 20)).isEqualTo(sunday.withHour(20))
    }

    @Test
    fun `a failure may be retried until Monday noon after the letter's Sunday`() {
        val monday = java.time.LocalDate.of(2026, 8, 31).toEpochDay()
        assertThat(LetterSchedule.mayRetry(monday, sunday.plusDays(1).withHour(11))).isTrue()
        assertThat(LetterSchedule.mayRetry(monday, sunday.plusDays(1).withHour(12))).isFalse()
        assertThat(LetterSchedule.mayRetry(monday, sunday.plusDays(2).withHour(9))).isFalse()
    }
}
