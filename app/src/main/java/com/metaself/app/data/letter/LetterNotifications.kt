package com.metaself.app.data.letter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.metaself.app.MainActivity
import com.metaself.app.R
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.ui.letter.LetterWording
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The weekly letter's two notifications (D99, D103): one when a letter has arrived, whose tap opens it;
 * one when it could not be written, whose tap opens Weekly letters and its Write it now.
 *
 * A channel of its own ("Weekly letter", amends D15), so the letter can be silenced without silencing the
 * meal reminder; created lazily, like the reminder's, only when something is about to be posted into it.
 */
object LetterNotifications {

    const val CHANNEL_ID = "weekly_letter"

    /** The tap's extra naming the letter to open: its week's Monday (epoch day). */
    const val EXTRA_WEEK = "letter_week"

    /** The tap's extra asking for Weekly letters. */
    const val EXTRA_LIST = "letters"

    private const val ARRIVED_ID = 2
    private const val FAILED_ID = 3

    /** A letter was written and stored. Its headline is the model's words, shown only on this phone. */
    fun arrived(context: Context, letter: WeeklyLetter) {
        val tap = Intent(context, MainActivity::class.java).putExtra(EXTRA_WEEK, letter.weekMonday)
        post(context, ARRIVED_ID, LetterWording.ARRIVED_TITLE, letter.texts.headline, tap)
    }

    /** No letter this week: no key, a refusal, the ceiling, or retries that ran out at Monday noon. */
    fun failed(context: Context) {
        val tap = Intent(context, MainActivity::class.java).putExtra(EXTRA_LIST, true)
        post(context, FAILED_ID, LetterWording.FAILED_TITLE, LetterWording.FAILED_TEXT, tap)
    }

    /** Takes down "couldn't be written", if it is showing: the letter has been written after all. */
    fun cancelFailed(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.let { runCatching { it.cancel(FAILED_ID) } }
    }

    private fun post(context: Context, id: Int, title: String, text: String, tap: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannel(manager)
        val open = PendingIntent.getActivity(
            context,
            id,
            tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        // Throws on Android 13 and later without the permission, asked when the letter is switched on; a
        // refusal means silence, never a crash inside the worker. The letter is stored either way.
        runCatching { manager.notify(id, notification) }
    }

    /** minSdk is 26, so channels always exist. */
    private fun ensureChannel(manager: NotificationManager) {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, LetterWording.CHANNEL, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }
}

/**
 * The notifications [WriteWeeklyLetter] touches (D103): when a letter is stored — by a Sunday retry or by
 * Write it now — the "couldn't be written" notification is out of date and is taken down.
 */
fun interface LetterNotifier {
    fun letterWritten()

    companion object {
        val NONE = LetterNotifier { }
    }
}

class AndroidLetterNotifier @Inject constructor(@ApplicationContext private val context: Context) : LetterNotifier {
    override fun letterWritten() = LetterNotifications.cancelFailed(context)
}
