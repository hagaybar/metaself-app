package com.metaself.app.ui.theme

/**
 * When the app moves, and for how long (public issue #16).
 *
 * **Today moves, the past is still.** A day that is still going answers what was just done to it:
 * the figure rolls to its new value, the rule fills. A day that is over is a record, and a record
 * arrives motionless — the same reasoning as D14, which never colours or nags the past.
 *
 * **Nothing moves when the system says not to.** Android's "Remove animations" accessibility switch
 * sets the animator duration scale to 0, and this app reads that as "still", everywhere.
 *
 * Motion is short and settles without overshoot: a figure that bounced past its value on the way
 * to it would, for a frame, be showing a number that is not the day's.
 */
object Motion {

    /** How long a pressed row takes to sink, and to come back. */
    const val PRESS_MILLIS = 100

    /** How long a figure or a rule takes to arrive at a new value. */
    const val SETTLE_MILLIS = 300

    /**
     * Whether the system allows motion at all, from Android's animator duration scale.
     *
     * 0 is "Remove animations". Anything that is not a positive number is treated the same way: a
     * setting that cannot be read as a speed is not permission to move.
     */
    fun systemAllows(animatorScale: Float): Boolean = animatorScale > 0f

    /** Whether something drawn for a day moves: only today, and only when the system allows it. */
    fun moves(isToday: Boolean, systemAllows: Boolean): Boolean = isToday && systemAllows
}
