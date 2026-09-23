package com.metaself.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.metaself.app.data.day.MealRepository
import com.metaself.app.data.time.Today
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * The alarm going off.
 *
 * The order matters and is the point: read the setting, read the day, post only if the day is
 * empty, and only then set tomorrow's alarm. A reminder scheduled at breakfast for the evening must
 * not arrive after a dinner logged at half past seven, so the day is checked here and never at the
 * moment the alarm was set.
 *
 * Tomorrow's alarm is set whatever today's answer was. It does not catch up, count what it missed,
 * or say anything about yesterday (D14).
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: ReminderStore

    @Inject
    lateinit var scheduler: ReminderScheduler

    @Inject
    lateinit var meals: MealRepository

    @Inject
    lateinit var today: Today

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // A receiver has ten seconds and no scope of its own. Two small reads of a local database
        // is what this is, and goAsync keeps the process alive for them.
        runBlocking(Dispatchers.IO) {
            runCatching {
                val reminder = reminders.current()
                val loggedDays = meals.observeLoggedDays().first()

                if (reminder.isDue(loggedDays, today())) {
                    Notifications.postDailyReminder(context)
                }
                scheduler.schedule(reminder)
            }
            pending.finish()
        }
    }
}
