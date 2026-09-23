package com.metaself.app.domain.reminder

import com.google.common.truth.Truth.assertThat
import com.metaself.app.ui.day.ReminderWording
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderTest {

    private val eightInTheEvening = Reminder(enabled = true, hour = 20, minute = 0)
    private val today = LocalDate.of(2026, 9, 4)

    @Test
    fun `before the hour, it falls today`() {
        val next = eightInTheEvening.nextAfter(LocalDateTime.of(2026, 9, 4, 9, 0))

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 9, 4, 20, 0))
    }

    @Test
    fun `after the hour, it falls tomorrow`() {
        val next = eightInTheEvening.nextAfter(LocalDateTime.of(2026, 9, 4, 21, 0))

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 9, 5, 20, 0))
    }

    /** An alarm set for a moment that has arrived fires at once, and then again in a moment. */
    @Test
    fun `at exactly the hour, it falls tomorrow`() {
        val next = eightInTheEvening.nextAfter(LocalDateTime.of(2026, 9, 4, 20, 0))

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 9, 5, 20, 0))
    }

    @Test
    fun `switched off, there is nothing to schedule`() {
        assertThat(Reminder(enabled = false).nextAfter(LocalDateTime.of(2026, 9, 4, 9, 0))).isNull()
    }

    /** A stored hour from a corrupted or future version must not crash the phone at boot. */
    @Test
    fun `an impossible time schedules nothing rather than throwing`() {
        val broken = Reminder(enabled = true, hour = 42, minute = 0)

        assertThat(broken.isValid).isFalse()
        assertThat(broken.nextAfter(LocalDateTime.of(2026, 9, 4, 9, 0))).isNull()
        assertThat(broken.isDue(emptySet(), today)).isFalse()
    }

    @Test
    fun `an empty day earns the reminder`() {
        assertThat(eightInTheEvening.isDue(emptySet(), today)).isTrue()
    }

    /** Asked when the alarm fires, so a dinner logged at half past seven silences it. */
    @Test
    fun `a day with something in it earns nothing`() {
        assertThat(eightInTheEvening.isDue(setOf(today.toEpochDay()), today)).isFalse()
    }

    @Test
    fun `yesterday having been logged does not silence today`() {
        assertThat(eightInTheEvening.isDue(setOf(today.toEpochDay() - 1), today)).isTrue()
    }

    @Test
    fun `switched off, no day earns anything`() {
        assertThat(Reminder(enabled = false).isDue(emptySet(), today)).isFalse()
    }

    @Test
    fun `it says one thing and stops`() {
        assertThat(ReminderWording.BODY).isEqualTo("Nothing logged today.")
    }

    @Test
    fun `the settings screen states the hour and the condition`() {
        val text = ReminderWording.schedule(eightInTheEvening)

        assertThat(text).contains("20:00")
        assertThat(text).contains("unless the day already has something in it")
    }

    @Test
    fun `switched off, the settings screen says the app will not notify at all`() {
        assertThat(ReminderWording.schedule(Reminder(enabled = false)))
            .isEqualTo("Off. The app will not notify you about anything.")
    }
}
