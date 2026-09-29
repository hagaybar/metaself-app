package com.metaself.app.data.letter

import android.content.Context
import com.metaself.app.domain.letter.WeeklyLetter

/**
 * The weekly letter's two notifications (D99, D103): one when a letter has arrived, one when it could not
 * be written. The worker calls these; posting them — the channel, the text and what a tap opens — is
 * added with D103's notifications, and until then they post nothing.
 */
object LetterNotifications {

    /** A letter was written and stored. */
    @Suppress("UNUSED_PARAMETER")
    fun arrived(context: Context, letter: WeeklyLetter) = Unit

    /** No letter this week: no key, a refusal, the ceiling, or retries that ran out at Monday noon. */
    @Suppress("UNUSED_PARAMETER")
    fun failed(context: Context) = Unit
}
