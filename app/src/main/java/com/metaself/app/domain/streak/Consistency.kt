package com.metaself.app.domain.streak

/** The one consistency figure the day shows (D52). */
sealed interface ConsistencyFigure {

    /** The run in force, and whether today is the day it reached a milestone length. */
    data class Run(val days: Int, val milestone: Boolean) : ConsistencyFigure

    /** How many of the last thirty days hold something. Never zero: zero is not shown. */
    data class Recent(val days: Int) : ConsistencyFigure
}

/**
 * Which of the three counts the day shows, chosen by the record rather than rotated (D52).
 *
 * The three answer one question, and early on they are the same number three times. So the day
 * shows the run while there is one worth naming, and otherwise the thirty-day count, which after a
 * single missed day still describes the month where a run of one describes only this morning.
 * Nothing at all rather than a zero, for either: D14, the past never nags. The other two are on the
 * record screen, in [com.metaself.app.ui.day.StreakWording]'s words.
 */
object Consistency {

    /** A run shorter than this is not yet a run worth naming. */
    const val RUN_SHOWN_FROM = 3

    /** Run lengths that get an achievement treatment on the day they are reached. */
    val MILESTONES = setOf(7, 30, 100, 365)

    /**
     * @param runEndsToday whether today itself holds something. A run counted back from yesterday
     *   (a morning before the first logging, D13) still stands, but its milestone was yesterday's.
     * @param milestoneAlreadySaid the weekly congratulation fired today, so the milestone has been
     *   said and the figure stays plain rather than saying it twice.
     */
    fun of(
        streak: Streak,
        runEndsToday: Boolean,
        milestoneAlreadySaid: Boolean,
    ): ConsistencyFigure? = when {
        streak.currentDays >= RUN_SHOWN_FROM -> ConsistencyFigure.Run(
            days = streak.currentDays,
            milestone = runEndsToday &&
                !milestoneAlreadySaid &&
                streak.currentDays in MILESTONES,
        )
        streak.daysInLast30 > 0 -> ConsistencyFigure.Recent(streak.daysInLast30)
        else -> null
    }
}
