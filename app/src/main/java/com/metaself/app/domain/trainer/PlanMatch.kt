package com.metaself.app.domain.trainer

import com.metaself.app.domain.movement.Workout

/**
 * Which kept plan counts (D86, D87). Measured from the plan's creation: Keep follows the answer by
 * seconds and no second time is stored (design question 2).
 */
object PlanMatch {

    const val DAYS = 7
    private const val WINDOW_MILLIS = DAYS * 86_400_000L

    /** The kept plan while it is still offered on the Trainer screen. */
    fun offered(plan: TrainerPlan?, nowMillis: Long): TrainerPlan? =
        plan?.takeIf { it.kept && nowMillis - it.createdAtMillis in 0..WINDOW_MILLIS }

    /** The kept plan when it was made within seven days before [session] started; never one made after. */
    fun forSession(kept: TrainerPlan?, session: Workout): TrainerPlan? =
        kept?.takeIf { it.kept && session.startedAtMillis - it.createdAtMillis in 0..WINDOW_MILLIS }
}
