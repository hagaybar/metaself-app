package com.metaself.app.domain.target

import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.weight.TrendPoint

/**
 * Whether the target is due a recalculation, and what it should become.
 *
 * Decision D11, in one place: weekly, from the smoothed trend, never from a single reading.
 *
 * Returns null when nothing is due, which is almost always — this is asked on every app open and
 * answers "no" six days out of seven.
 */
object TargetRevisionRule {

    /** A target that moved daily would be a target nobody could plan around. */
    const val DAYS_BETWEEN = 7L

    fun revise(
        profile: Profile,
        trend: List<TrendPoint>,
        last: TargetRevision?,
        today: Long,
        currentYear: Int,
        burnAdjustmentKcal: Int = 0,
    ): TargetRevision? {
        val latest = trend.lastOrNull() ?: return null

        // The clock runs from the last revision, or from the first reading if there has been none.
        // Waiting a week before the first revision is what keeps a single reading — which is a
        // trend of one, and mostly water — from moving the target.
        val since = last?.epochDay ?: trend.first().reading.epochDay
        if (today - since < DAYS_BETWEEN) return null

        val target = DailyTargetCalculator.of(
            profile.copy(weightKg = latest.trendKg),
            currentYear,
            burnAdjustmentKcal,
        )

        return TargetRevision(
            epochDay = today,
            trendKg = latest.trendKg,
            kcal = target.kcal,
            previousKcal = last?.kcal,
        )
    }
}
