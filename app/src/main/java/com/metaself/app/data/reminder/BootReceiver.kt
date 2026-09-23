package com.metaself.app.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Setting the alarm again after a restart.
 *
 * Android forgets every alarm when the phone reboots. Without this the reminder works until the
 * first restart and then silently stops, which is the worst of both: the owner believes he has one.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: ReminderStore

    @Inject
    lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        runBlocking(Dispatchers.IO) {
            runCatching { scheduler.schedule(reminders.current()) }
            pending.finish()
        }
    }
}
