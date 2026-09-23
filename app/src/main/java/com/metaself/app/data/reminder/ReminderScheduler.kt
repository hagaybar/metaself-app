package com.metaself.app.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.reminder.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Setting and cancelling the one alarm, behind an interface so the callers can be tested. */
interface ReminderScheduler {

    fun schedule(reminder: Reminder, now: LocalDateTime = LocalDateTime.now())

    fun cancel()
}

/**
 * The real one, over Android's alarm service.
 *
 * Exact where the phone will grant it and inexact where it will not. A reminder that arrives a few
 * minutes late has lost nothing, and asking for a permission Android increasingly reserves for
 * clocks and calendars would be claiming to be something this app is not.
 *
 * What it must never do is fail silently. If the alarm cannot be set at all, that goes in the
 * problem log, because the alternative is an owner who thinks he has a reminder and does not.
 */
@Singleton
class AlarmReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val problems: ProblemLog,
) : ReminderScheduler {

    private val alarms: AlarmManager?
        get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(reminder: Reminder, now: LocalDateTime) {
        val next = reminder.nextAfter(now)
        if (next == null) {
            cancel()
            return
        }

        val alarmManager = alarms
        if (alarmManager == null) {
            problems.record(kind = "reminder", detail = "no alarm service on this device")
            return
        }

        val atMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching {
            if (canBeExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    atMillis,
                    pendingIntent(),
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    atMillis,
                    pendingIntent(),
                )
            }
        }.onFailure { error ->
            problems.record(
                kind = "reminder",
                detail = "could not set the alarm: ${error::class.java.simpleName} " +
                    "${error.message}",
            )
        }
    }

    override fun cancel() {
        alarms?.cancel(pendingIntent())
    }

    private fun canBeExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * One intent, reused. The same request code and action means setting an alarm replaces the
     * previous one rather than adding a second, and cancelling finds the one that exists.
     */
    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val REQUEST_CODE = 1
        const val ACTION = "com.metaself.app.REMIND"
    }
}
