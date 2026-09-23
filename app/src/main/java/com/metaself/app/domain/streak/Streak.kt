package com.metaself.app.domain.streak

/**
 * How consistently the owner has been writing things down.
 *
 * @property currentDays the run in force, or zero when there is not one.
 * @property lifetimeDays every day that holds a meal.
 * @property daysInLast30 how many of the last thirty days hold one.
 */
data class Streak(
    val currentDays: Int,
    val lifetimeDays: Int,
    val daysInLast30: Int,
)

/**
 * The streak, counted from the record and never stored as a counter (D13).
 *
 * This is the resolution of wanting a streak and of losing one to a gap in the writing-down:
 * **there is no forgiveness rule and no grace period, there is a record that can be completed.**
 * Filling in a skipped day repairs the run with no other action, because the run was
 * always intact — the writing-down was simply late. A stored counter could not do that without an
 * "undo" for every way a day can change.
 */
object Streaks {

    private const val RECENT_WINDOW_DAYS = 30

    fun of(loggedDays: Set<Long>, todayEpochDay: Long): Streak {
        // A day that has not happened yet is not a day he logged. Nothing in the app offers to log
        // forwards, so this is a guard against a clock that moved rather than a case to support.
        val logged = loggedDays.filter { it <= todayEpochDay }.toSet()

        return Streak(
            currentDays = runEndingAt(anchorFor(logged, todayEpochDay), logged),
            lifetimeDays = logged.size,
            daysInLast30 = logged.count { it > todayEpochDay - RECENT_WINDOW_DAYS },
        )
    }

    /**
     * Where the run is counted back from.
     *
     * Today when it holds something, and otherwise yesterday: a morning with nothing logged yet must
     * show yesterday's run intact rather than zero, or the app is hostile before breakfast. Anything
     * older than that is a run that has already ended, and reporting it as current would be a lie
     * the owner would eventually catch.
     */
    private fun anchorFor(logged: Set<Long>, todayEpochDay: Long): Long? = when {
        logged.contains(todayEpochDay) -> todayEpochDay
        logged.contains(todayEpochDay - 1) -> todayEpochDay - 1
        else -> null
    }

    private fun runEndingAt(anchor: Long?, logged: Set<Long>): Int {
        if (anchor == null) return 0
        var day = anchor
        var length = 0
        while (logged.contains(day)) {
            length++
            day--
        }
        return length
    }
}
