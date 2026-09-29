package com.metaself.app.data.letter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether Android asks for a notifications permission here (13 and later) and whether it is held. */
interface NotificationAccess {
    val needed: Boolean
    fun allowed(): Boolean
}

class AndroidNotificationAccess @Inject constructor(@ApplicationContext private val context: Context) : NotificationAccess {
    override val needed: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun allowed(): Boolean = !needed ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/**
 * The one-time note on today that sets the weekly letter up (D99, D103): the letter is on by default, so
 * nobody switches it on and nobody is asked for the notification it arrives by. Pure.
 */
object LetterSetup {

    /** What the note asks for; each ask only where it is still wanted. */
    data class Asks(val notifications: Boolean, val background: Boolean)

    /**
     * The asks the note stands for, or null for no note: quiet once put away or gone through, while the
     * letter is off, and once nothing is left to allow.
     */
    fun asks(
        letterOn: Boolean,
        done: Boolean,
        notificationsNeeded: Boolean,
        notificationsAllowed: Boolean,
        backgroundOffered: Boolean,
        backgroundGranted: Boolean,
    ): Asks? {
        if (done || !letterOn) return null
        val asks = Asks(
            notifications = notificationsNeeded && !notificationsAllowed,
            background = backgroundOffered && !backgroundGranted,
        )
        return asks.takeIf { it.notifications || it.background }
    }
}
