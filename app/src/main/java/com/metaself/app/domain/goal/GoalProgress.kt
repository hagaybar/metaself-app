package com.metaself.app.domain.goal

import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.weight.TrendPoint

/**
 * How far there is to go.
 *
 * Every figure here comes from the smoothed trend and never from a single reading. A weight taken
 * one morning crosses a threshold and uncrosses it the next; decision D11 already settled that the
 * trend is what this app believes about the owner's weight, and arriving at a goal is far too
 * consequential a thing to decide on a number that includes yesterday's dinner.
 *
 * @property startKg the trend at the first reading there is. It is honest about what it means —
 *   since tracking began, not since the goal was set — because the app has never recorded when a
 *   goal was set and inventing a date would be worse than the imprecision.
 *
 * Distance only. Every projection lives in [GoalForecast], which is the one place a division by a
 * rate happens — two sources for one number is how they come to disagree.
 */
data class GoalProgress(
    val targetKg: Double,
    val startKg: Double,
    val trendKg: Double,
    val kgPerWeek: Double,
    val toGoKg: Double,
    val doneKg: Double,
    val arrived: Boolean,
) {
    companion object {

        /**
         * Null whenever there is nothing truthful to say: a goal with no destination, a goal to
         * hold, or no weight readings at all. The screens show nothing rather than a placeholder,
         * because a distance of "—" invites the owner to wonder whether it is broken.
         */
        fun of(goal: Goal, trend: List<TrendPoint>): GoalProgress? {
            val targetKg = goal.targetKg ?: return null
            if (goal.direction == GoalDirection.HOLD) return null
            val now = trend.lastOrNull()?.trendKg ?: return null
            val start = trend.first().trendKg

            val remaining = when (goal.direction) {
                GoalDirection.LOSE -> now - targetKg
                GoalDirection.GAIN -> targetKg - now
                GoalDirection.HOLD -> 0.0
            }
            val toGo = remaining.coerceAtLeast(0.0)
            val arrived = remaining <= 0.0

            val done = when (goal.direction) {
                GoalDirection.LOSE -> start - now
                GoalDirection.GAIN -> now - start
                GoalDirection.HOLD -> 0.0
            }.coerceAtLeast(0.0)

            return GoalProgress(
                targetKg = targetKg,
                startKg = start,
                trendKg = now,
                kgPerWeek = goal.kgPerWeek,
                toGoKg = toGo,
                doneKg = done,
                arrived = arrived,
            )
        }
    }
}
