package com.metaself.app.ui.encourage

import com.metaself.app.domain.encourage.Occasion

/**
 * What each occasion says.
 *
 * Short, and about the thing that happened rather than about him. "Yesterday stayed inside your
 * window" is a fact he can check; "well done, you're doing great" is a claim he cannot, and one that
 * reads as hollow the second time.
 *
 * Nothing here congratulates him on a number going down as though the number were the point, and
 * nothing implies a next time.
 */
object EncouragementWording {

    fun of(occasion: Occasion, streakDays: Int = 0): String = when (occasion) {
        // Deliberately warm and entirely without reference to what was missed.
        Occasion.WELCOME_BACK -> "Good to see you back."

        Occasion.WINDOW_YESTERDAY -> "Yesterday stayed inside your window."

        Occasion.NEW_LOW -> "That is the lightest your trend has been."

        Occasion.WINDOW_WEEK -> "Seven days, seven inside your window."

        Occasion.LOGGING_WEEK -> if (streakDays == 7) {
            "A week of logging, unbroken."
        } else {
            "$streakDays days of logging, unbroken."
        }

        Occasion.BIG_WALK -> "A good deal more walking than usual today."
    }
}
