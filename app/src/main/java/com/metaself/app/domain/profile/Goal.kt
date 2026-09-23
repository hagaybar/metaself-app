package com.metaself.app.domain.profile

enum class GoalDirection { LOSE, HOLD, GAIN }

/**
 * What the owner is trying to do to his weight, and how fast.
 *
 * [kgPerWeek] is always a positive magnitude; [direction] carries the sign. Holding forces the rate
 * to zero so that a rate left behind by switching away from "lose" cannot leak into the arithmetic.
 *
 * [targetKg] is where he is going, and is optional: a rate with no destination is still a goal, and
 * was the only kind this app had until now. Holding cannot carry one — a destination with no
 * direction is not a goal, it is a number — for the same reason holding cannot carry a rate.
 */
data class Goal(
    val direction: GoalDirection,
    val kgPerWeek: Double,
    val targetKg: Double? = null,
) {
    init {
        require(kgPerWeek >= 0.0) {
            "kgPerWeek is a magnitude; direction carries the sign"
        }
        require(direction != GoalDirection.HOLD || kgPerWeek == 0.0) {
            "holding weight cannot carry a rate"
        }
        require(direction != GoalDirection.HOLD || targetKg == null) {
            "holding weight is not going anywhere, so it has no target"
        }
        require(targetKg == null || targetKg > 0.0) {
            "a target weight is a weight"
        }
    }

    companion object {
        fun hold(): Goal = Goal(GoalDirection.HOLD, 0.0)

        fun lose(kgPerWeek: Double, targetKg: Double? = null): Goal =
            Goal(GoalDirection.LOSE, kgPerWeek, targetKg)

        fun gain(kgPerWeek: Double, targetKg: Double? = null): Goal =
            Goal(GoalDirection.GAIN, kgPerWeek, targetKg)

        /**
         * The rates the setup form offers.
         *
         * A free-text rate would invite precision the underlying formula does not have: the resting
         * burn it builds on is a population average carrying roughly a 10% error bar.
         */
        val OFFERED_RATES_KG_PER_WEEK = listOf(0.25, 0.5, 0.75, 1.0)
    }
}
