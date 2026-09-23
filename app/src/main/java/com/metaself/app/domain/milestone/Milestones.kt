package com.metaself.app.domain.milestone

import com.metaself.app.domain.goal.GoalProgress
import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.weight.TrendPoint

/**
 * Which milestones the record has reached.
 *
 * Purely a question about the record, and deliberately NOT a question about what has been said. That
 * separation is the whole design: this function happily reports the first kilogram every time it is
 * called, and it is the stored set of what has already been announced that makes it happen once. The
 * requirement was stated at the milestones themselves — the first kilogram, only once — and a
 * weight that crosses a threshold, drifts back and crosses again has not achieved anything twice.
 *
 * Nothing here ever reports going the wrong way. There is no un-reaching, no broken run announced,
 * no regained kilogram mentioned. Comment on the good and stay silent on the rest, or the weight
 * screen becomes a thing to avoid opening.
 */
object Milestones {

    /** Every fifth kilogram, up to a distance nobody is going to exceed by accident. */
    private const val FIFTH = 5

    /** Runs of weeks worth marking. Rare on purpose: roughly one announcement every six weeks. */
    val STEADY_WEEKS = listOf(4, 12)

    private const val LAST_KG_THRESHOLD = 1.0

    fun reached(goal: Goal, trend: List<TrendPoint>): Set<Milestone> {
        if (goal.direction == GoalDirection.HOLD || trend.isEmpty()) return emptySet()

        val progress = GoalProgress.of(goal, trend)
        val doneKg = progress?.doneKg ?: distanceMoved(goal, trend)

        return buildSet {
            if (doneKg >= 1.0) add(Milestone.firstKg)

            var mark = FIFTH
            while (doneKg >= mark) {
                add(Milestone.everyFifth(mark))
                mark += FIFTH
            }

            // Halfway and the last kilogram need somewhere to be going. Neither is reported once
            // the goal is reached: arriving has its own announcement, and following it with "you
            // are nearly there" would be absurd.
            if (progress != null && !progress.arrived) {
                val whole = progress.doneKg + progress.toGoKg
                if (whole > 0.0 && progress.doneKg >= whole / 2.0) add(Milestone.halfway)
                if (progress.toGoKg <= LAST_KG_THRESHOLD) add(Milestone.lastKg)
            }

            STEADY_WEEKS.filter { weeks -> movedEveryWeek(goal, trend, weeks) }
                .forEach { weeks -> add(Milestone.steady(weeks)) }
        }
    }

    /** How far the trend has moved the right way since the first reading. */
    private fun distanceMoved(goal: Goal, trend: List<TrendPoint>): Double {
        val start = trend.first().trendKg
        val now = trend.last().trendKg
        return when (goal.direction) {
            GoalDirection.LOSE -> start - now
            GoalDirection.GAIN -> now - start
            GoalDirection.HOLD -> 0.0
        }.coerceAtLeast(0.0)
    }

    /**
     * Whether each of the last [weeks] weeks moved the right way.
     *
     * Every week must move, not the average of them: this milestone is the only one of the three
     * kinds that is really praise, and praising consistency on the strength of one very good week
     * inside four bad ones would be praising the wrong thing.
     *
     * A week with no reading at or before its boundary is not a week that can be judged, so the run
     * is simply not claimed. Silence rather than a guess.
     */
    private fun movedEveryWeek(goal: Goal, trend: List<TrendPoint>, weeks: Int): Boolean {
        val today = trend.last().reading.epochDay
        val marks = (0..weeks).map { trendAtOrBefore(trend, today - it * 7L) ?: return false }

        return marks.zipWithNext().all { (later, earlier) ->
            when (goal.direction) {
                GoalDirection.LOSE -> later < earlier
                GoalDirection.GAIN -> later > earlier
                GoalDirection.HOLD -> false
            }
        }
    }

    private fun trendAtOrBefore(trend: List<TrendPoint>, epochDay: Long): Double? =
        trend.lastOrNull { it.reading.epochDay <= epochDay }?.trendKg
}
