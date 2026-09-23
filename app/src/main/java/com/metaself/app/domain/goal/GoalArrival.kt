package com.metaself.app.domain.goal

/**
 * The fact that a goal weight was reached, and the day it happened.
 *
 * Stored rather than recomputed, and that is the whole design. The requirement is the one the
 * milestones already set: "only once". A weight that crosses a threshold, drifts back and crosses
 * again has not achieved anything twice, and anything derived from today's numbers would say it had.
 *
 * Reaching the goal also switches the goal to holding (D21), so after this is written there is no
 * longer a target to arrive at — two independent reasons the celebration cannot repeat, which is one
 * more than strictly needed and the right number for something that is unbearable when it is wrong.
 */
data class GoalArrival(
    val targetKg: Double,
    val epochDay: Long,
) {
    /** Shown on the day it happened and never again (D14: the past does not nag). */
    fun isTodayS(todayEpochDay: Long): Boolean = epochDay == todayEpochDay
}
