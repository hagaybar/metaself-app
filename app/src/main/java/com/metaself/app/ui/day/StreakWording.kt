package com.metaself.app.ui.day

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
        1 -> "1 day in a row"
        else -> "${streak.currentDays} days in a row"
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
}
