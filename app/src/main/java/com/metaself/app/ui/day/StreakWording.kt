package com.metaself.app.ui.day

import com.metaself.app.domain.streak.ConsistencyFigure
import com.metaself.app.domain.streak.Streak

/**
 * What the app says about how consistently the owner has been logging.
 *
 * Three short figures, and nothing said about a run that has ended. Decision D14 — the past never
 * nags — means "0 days in a row" is not a thing this app prints: it is a reproach dressed as a
 * statistic, and the run coming back is a matter of logging one meal, not of being told off. When
 * there is no run, the other two counts stand on their own.
 */
object StreakWording {

    /** "12 days in a row", or nothing at all when there is no run to speak of. */
    fun run(streak: Streak): String? = when (streak.currentDays) {
        0 -> null
        else -> runOf(streak.currentDays)
    }

    /** "45 days logged", or nothing before anything has been. */
    fun lifetime(streak: Streak): String? = when (streak.lifetimeDays) {
        0 -> null
        1 -> "1 day logged"
        else -> "${streak.lifetimeDays} days logged"
    }

    /** "22 of the last 30", or nothing when there is no history to summarise yet. */
    fun recent(streak: Streak): String? =
        if (streak.lifetimeDays == 0) null else "${streak.daysInLast30} of the last 30"

    /**
     * The day's one figure (D52): the run in its usual words, or "22 of the last 30 days".
     *
     * The thirty-day count says "days" here and not in [recent], because on the day it stands alone
     * and "22 of the last 30" beside nothing does not say what it counts. On the record screen it
     * sits under "45 days logged", which does.
     */
    fun day(figure: ConsistencyFigure): String = when (figure) {
        is ConsistencyFigure.Run -> runOf(figure.days)
        is ConsistencyFigure.Recent -> "${figure.days} of the last 30 days"
    }

    /**
     * The words beside a milestone's figure, which is drawn on its own in the display face (D52).
     * Two pieces rather than one string, as every figure in the app is set beside its words.
     */
    fun runWords(days: Int): String = if (days == 1) "day in a row" else "days in a row"

    private fun runOf(days: Int): String = "$days ${runWords(days)}"
}
