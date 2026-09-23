package com.metaself.app.ui.day

import com.metaself.app.domain.reminder.Reminder
import java.util.Locale

/**
 * What the reminder says, and what the settings screen says about it.
 *
 * The notification says one thing and stops. No calorie count, no streak, no encouragement and no
 * reproach: a notification that argues with you is a notification you turn off, and this is the only
 * one the app is allowed to send (D15).
 */
object ReminderWording {

    const val TITLE = "MetaSelf"
    const val BODY = "Nothing logged today."

    /** "Every day at 20:00" — twenty-four hour, the clock the rest of the app is written in. */
    fun schedule(reminder: Reminder): String =
        if (!reminder.enabled) {
            "Off. The app will not notify you about anything."
        } else {
            "Every day at ${clock(reminder)}, unless the day already has something in it."
        }

    fun clock(reminder: Reminder): String =
        String.format(Locale.US, "%02d:%02d", reminder.hour, reminder.minute)
}
