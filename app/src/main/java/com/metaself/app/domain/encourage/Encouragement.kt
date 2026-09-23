package com.metaself.app.domain.encourage

/**
 * Something the owner did that is worth remarking on.
 *
 * Every one of these is already in the record; none needs anything new stored. They are ordered by
 * what is worth saying when several are true at once, and [WELCOME_BACK] is first on purpose:
 * **coming back after a gap is the moment a habit is actually saved**, and it is exactly when most
 * apps choose to show a broken streak instead.
 */
enum class Occasion {

    /** He has just opened the app after some days away. */
    WELCOME_BACK,

    /**
     * The logging streak has reached another multiple of seven.
     *
     * Second, above everything a common day can offer, because it is the only one below
     * [WELCOME_BACK] that is rare by construction: it comes round one day in seven, while keeping
     * the eating window can be true every day. Fifth, where it began, it essentially never got a
     * turn —
     * and a weekly occasion that loses the slot loses the occasion rather than deferring it
     * (issue #59).
     */
    LOGGING_WEEK,

    /** Yesterday's eating was entirely inside the window he set. */
    WINDOW_YESTERDAY,

    /** The smoothed weight trend is the lowest it has been since he started. */
    NEW_LOW,

    /** Every one of the last seven days kept the window. */
    WINDOW_WEEK,

    /** Today's walking is well past his usual day. */
    BIG_WALK,
}

/**
 * What the record says is worth mentioning today, if anything.
 *
 * **One thing a day at most, and never about a failure.** Praise that arrives every time stops being
 * praise — a point already settled when the milestones were made deliberately rare — so the budget
 * rather than the wording is what keeps this worth having. There is no occasion here for a window
 * missed, a streak broken or a target overshot: silence is the whole of what the app says about a
 * bad day (D14).
 */
object Encouragements {

    /** Away this long, and coming back is the thing worth noticing. */
    const val GAP_DAYS = 3

    /** A walk has to be half as long again as usual before it is remarkable. */
    const val BIG_WALK_SHARE = 1.5

    /**
     * @param alreadySaidToday what today has already had, so nothing is said twice.
     * @param somethingRarer true when a milestone or a goal arrival has the day's slot. Those are
     *   rare by design and always win it; these fill the days when nothing rare happened.
     */
    fun pick(
        somethingRarer: Boolean,
        alreadySaidToday: Boolean,
        daysAway: Int,
        windowKeptYesterday: Boolean,
        windowKeptLastSeven: Boolean,
        trendAtNewLow: Boolean,
        streakDays: Int,
        stepsToday: Int,
        usualSteps: Int?,
    ): Occasion? {
        if (somethingRarer || alreadySaidToday) return null

        return when {
            daysAway >= GAP_DAYS -> Occasion.WELCOME_BACK
            streakDays > 0 && streakDays % 7 == 0 -> Occasion.LOGGING_WEEK
            windowKeptYesterday -> Occasion.WINDOW_YESTERDAY
            trendAtNewLow -> Occasion.NEW_LOW
            windowKeptLastSeven -> Occasion.WINDOW_WEEK
            isABigWalk(stepsToday, usualSteps) -> Occasion.BIG_WALK
            else -> null
        }
    }

    private fun isABigWalk(stepsToday: Int, usualSteps: Int?): Boolean {
        val usual = usualSteps?.takeIf { it > 0 } ?: return false
        return stepsToday >= usual * BIG_WALK_SHARE
    }
}
