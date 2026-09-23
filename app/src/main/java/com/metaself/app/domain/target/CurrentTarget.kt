package com.metaself.app.domain.target

import com.metaself.app.domain.profile.Profile

/**
 * The target in force: what setup produced, moved on by whatever the weekly revisions have done.
 *
 * The weight entered at setup is never overwritten. It is what the owner typed, and it stays what he
 * typed; the revision supplies a different weight to compute FROM. [weightUsedKg] exists so the
 * profile screen can say which of the two it used, and the two numbers can never silently disagree.
 */
object CurrentTarget {

    fun of(
        profile: Profile,
        revision: TargetRevision?,
        currentYear: Int,
        burnAdjustmentKcal: Int = 0,
    ): DailyTarget = DailyTargetCalculator.of(
        profile.copy(weightKg = weightUsedKg(profile, revision)),
        currentYear,
        burnAdjustmentKcal,
    )

    fun weightUsedKg(profile: Profile, revision: TargetRevision?): Double =
        revision?.trendKg ?: profile.weightKg
}
