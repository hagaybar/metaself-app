package com.metaself.app.domain.reminder

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The one notification this app is allowed to send (D15).
 *
 * @property hour, [minute] when the owner wants asking. Kept as numbers rather than a formatted
 *   string so that nothing has to parse a time back out of a phone's locale.
 */
data class Reminder(
    val enabled: Boolean = false,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
) {
    val isValid: Boolean get() = hour in 0..23 && minute in 0..59

    /**
     * When it next falls due, or null when there is nothing to schedule.
     *
     * At exactly the appointed minute the answer is tomorrow: "now" is not later than "now", and an
     * alarm set for a moment that has arrived is an alarm that fires immediately and then again in
     * a moment.
     */
    fun nextAfter(now: LocalDateTime): LocalDateTime? {
        if (!enabled || !isValid) return null
        val todayAt = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        return if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    }

    /**
     * Whether the day it lands on has earned it.
     *
     * Asked when the alarm fires and never when it is set: a reminder scheduled at breakfast for
     * the evening must not arrive after a dinner logged at half past seven.
     */
    fun isDue(loggedDays: Set<Long>, today: LocalDate): Boolean =
        enabled && isValid && !loggedDays.contains(today.toEpochDay())

    companion object {
        /** Early evening: late enough that a blank day means something, early enough to act on. */
        const val DEFAULT_HOUR = 20
    }
}
