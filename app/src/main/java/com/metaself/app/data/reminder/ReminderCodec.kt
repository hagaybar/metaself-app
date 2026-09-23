package com.metaself.app.data.reminder

import com.metaself.app.domain.reminder.Reminder

/**
 * The reminder's storage format, as a pure function both ways.
 *
 * Anything unreadable — a store written before reminders existed, a half-written entry, an hour of
 * 42 — decodes to "off". This runs at boot, in a broadcast receiver, where an exception is a crash
 * the owner sees as the app dying for no reason he can connect to anything.
 */
object ReminderCodec {

    const val KEY_ENABLED = "reminder_enabled"
    const val KEY_HOUR = "reminder_hour"
    const val KEY_MINUTE = "reminder_minute"

    fun encode(reminder: Reminder): Map<String, String> = mapOf(
        KEY_ENABLED to reminder.enabled.toString(),
        KEY_HOUR to reminder.hour.toString(),
        KEY_MINUTE to reminder.minute.toString(),
    )

    fun decode(values: Map<String, String>): Reminder {
        val enabled = values[KEY_ENABLED] == "true"
        val hour = values[KEY_HOUR]?.toIntOrNull() ?: Reminder.DEFAULT_HOUR
        val minute = values[KEY_MINUTE]?.toIntOrNull() ?: 0
        val decoded = Reminder(enabled = enabled, hour = hour, minute = minute)
        return if (decoded.isValid) decoded else Reminder()
    }
}
