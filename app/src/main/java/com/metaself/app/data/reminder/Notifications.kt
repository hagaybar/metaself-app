package com.metaself.app.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.metaself.app.MainActivity
import com.metaself.app.R
import com.metaself.app.ui.day.ReminderWording

/**
 * The one notification this app posts.
 *
 * One channel, created lazily and only when something is about to be posted into it. An app that
 * registers channels it never uses gives the owner a settings screen full of switches for things
 * that do not exist.
 */
object Notifications {

    private const val CHANNEL_ID = "daily_reminder"
    private const val NOTIFICATION_ID = 1

    fun postDailyReminder(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(ReminderWording.TITLE)
            .setContentText(ReminderWording.BODY)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Throws on Android 13 and later if the owner has not granted the permission. He is asked
        // when he turns the reminder on; if he said no, silence is the correct outcome and a crash
        // inside a broadcast receiver is not.
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                ReminderWording.TITLE,
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
