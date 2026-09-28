package com.metaself.app.domain.movement

/**
 * D81: the one rule for whether a workout counts as one, wherever workouts are counted — the day's
 * summary, the Movement screen, the band page's "not counted". Pure. Whether it is hidden is a
 * separate rule, applied beside this one.
 */
object CountedWorkouts {

    /**
     * False only for a WALK written by an app in [uncountedWalkApps], the packages the owner switched
     * off on "What the band sends". A typed workout has no writing app ([origin] null) and always
     * counts; every other kind from a switched-off app still counts.
     */
    fun counts(kind: WorkoutKind, origin: String?, uncountedWalkApps: Set<String>): Boolean =
        kind != WorkoutKind.WALK || origin == null || origin !in uncountedWalkApps
}
