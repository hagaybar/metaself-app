package com.metaself.app.domain.day

/**
 * How much of the day's allowance has gone, as a fraction — the arithmetic behind the ring.
 *
 * Kept apart from the drawing so it can be tested: a ring is a picture of a number, and the number
 * is the part that can be wrong in a way nobody notices.
 */
object DayProgress {

    /**
     * The fraction of the target eaten, from 0 to 1.
     *
     * Clamped at 1 for the ring's sake — a full circle is as full as a circle gets, and a sweep of
     * 1.4 would wrap round and draw a lie. [Remaining.overTarget] is what says the day went past,
     * and it is not this function's job to say it twice.
     */
    fun eatenFraction(target: Int, eatenKcal: Int): Float = when {
        target <= 0 -> 0f
        else -> (eatenKcal.toFloat() / target).coerceIn(0f, 1f)
    }
}
