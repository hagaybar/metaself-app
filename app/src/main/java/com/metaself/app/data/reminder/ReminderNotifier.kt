package com.metaself.app.data.reminder

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posting the reminder, behind an interface so the settings screen can be tested without a phone.
 *
 * The alarm path does not use this — a broadcast receiver has a context of its own and no injection
 * worth the trouble. This exists for the "send it now" button.
 */
interface ReminderNotifier {

    fun postDailyReminderNow()
}

@Singleton
class AndroidReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderNotifier {

    override fun postDailyReminderNow() {
        Notifications.postDailyReminder(context)
    }
}
