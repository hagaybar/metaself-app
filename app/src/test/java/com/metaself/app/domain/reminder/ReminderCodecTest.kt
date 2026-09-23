package com.metaself.app.domain.reminder

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.reminder.ReminderCodec
import org.junit.jupiter.api.Test

class ReminderCodecTest {

    @Test
    fun `a reminder survives a round trip`() {
        val reminder = Reminder(enabled = true, hour = 7, minute = 30)

        assertThat(ReminderCodec.decode(ReminderCodec.encode(reminder))).isEqualTo(reminder)
    }

    /** Every install from before this step. It must read as off, not as broken. */
    @Test
    fun `a store written before reminders existed is simply off`() {
        val decoded = ReminderCodec.decode(emptyMap())

        assertThat(decoded.enabled).isFalse()
        assertThat(decoded.isValid).isTrue()
    }

    /**
     * This is decoded at boot inside a broadcast receiver, where an exception is the app dying for
     * a reason the owner cannot connect to anything he did.
     */
    @Test
    fun `an impossible stored time reads as off rather than throwing`() {
        val decoded = ReminderCodec.decode(
            mapOf(
                ReminderCodec.KEY_ENABLED to "true",
                ReminderCodec.KEY_HOUR to "42",
                ReminderCodec.KEY_MINUTE to "0",
            ),
        )

        assertThat(decoded.enabled).isFalse()
    }

    @Test
    fun `rubbish in the hour reads as the default rather than throwing`() {
        val decoded = ReminderCodec.decode(
            mapOf(
                ReminderCodec.KEY_ENABLED to "true",
                ReminderCodec.KEY_HOUR to "half past",
            ),
        )

        assertThat(decoded.hour).isEqualTo(Reminder.DEFAULT_HOUR)
        assertThat(decoded.enabled).isTrue()
    }
}
