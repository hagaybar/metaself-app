package com.metaself.app.domain.trainer

/** D96: the single-session form opened from a running plan's next session. Pure. */
object NextInPlan {

    /** The smallest choice at least that long; 60-or-more when longer. */
    fun time(minutes: Int): TimeAvailable =
        TimeAvailable.entries.sortedBy { it.minutes }.firstOrNull { it.minutes >= minutes } ?: TimeAvailable.MIN_60_OR_MORE

    fun wish(effort: PlannedEffort): Wish = when (effort) {
        PlannedEffort.EASY -> Wish.EASY
        PlannedEffort.PUSH -> Wish.PUSH
        PlannedEffort.STEADY -> Wish.NOT_SURE
    }
}
