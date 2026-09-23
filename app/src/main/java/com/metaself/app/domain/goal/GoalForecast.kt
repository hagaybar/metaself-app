package com.metaself.app.domain.goal

import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.weight.MeasuredRate
import kotlin.math.roundToLong

/**
 * When the goal is reached at the rate he chose, and when it would be reached at the rate he is
 * actually managing (D47).
 *
 * The only unit that knows about both a goal and a measurement, which is why the sign convention is
 * resolved here and nowhere else: [MeasuredRate] is direction-agnostic, and "is this progress?" is a
 * question only a goal can answer.
 *
 * Both are divisions and whatever displays them must say so — D21 on the chosen rate, and D4's rule
 * that an estimate is never presented as a measurement, applied to the future.
 *
 * @property measuredTowardGoalKgPerWeek the measured rate re-signed so that POSITIVE means closing
 *   the distance, whichever way the goal points.
 * @property measuredWeeks null when there is no measurement, when the trend is flat or moving away,
 *   and when the division lands past [MAX_PROJECTION_WEEKS]. A null here with a non-null [measured]
 *   is the state that says the rate and gives no finish line.
 */
data class GoalForecast(
    val arrived: Boolean,
    val chosenKgPerWeek: Double,
    val chosenWeeks: Double?,
    val chosenFinishEpochDay: Long?,
    val measured: MeasuredRate?,
    val measuredTowardGoalKgPerWeek: Double?,
    val measuredWeeks: Double?,
    val measuredFinishEpochDay: Long?,
) {
    companion object {

        /**
         * The owner's rule (spec §3): a measured finish line is shown only when it falls within
         * two years.
         *
         * One comparison disposes of the stalled trend, the flat trend and the trend going the
         * wrong way, with none of them a special case and without the app ever deciding that a
         * stretch is "bad". It also disposes of an arithmetic absurdity: at 20 kg to go, a trend
         * moving 0.02 kg a week projects about 1,000 weeks, which is true, useless, and reads as
         * mockery.
         */
        const val MAX_PROJECTION_WEEKS = 104.0

        /**
         * Below this the trend is held steady, not moving.
         *
         * Exactly the values that print as "0" at the two decimals the wording formats to, so that
         * rounding and meaning are the same test and no sentence can ever state a rate of zero.
         */
        const val FLAT_KG_PER_WEEK = 0.005

        fun of(
            goal: Goal,
            progress: GoalProgress,
            measured: MeasuredRate?,
            todayEpochDay: Long,
        ): GoalForecast {
            val chosenWeeks = when {
                progress.arrived -> null
                // No rate, no division. A goal with a destination and no speed has no arrival
                // date and must not be given one.
                goal.kgPerWeek <= 0.0 -> null
                else -> progress.toGoKg / goal.kgPerWeek
            }

            val toward = measured?.let {
                when (goal.direction) {
                    GoalDirection.LOSE -> -it.kgPerWeek
                    GoalDirection.GAIN -> it.kgPerWeek
                    GoalDirection.HOLD -> 0.0
                }
            }

            // Flat is excluded before direction is consulted: a rate of -0.002 against a LOSE goal
            // is "closing the distance" by the sign and standing still by any honest reading.
            val measuredWeeks = when {
                progress.arrived -> null
                toward == null || toward < FLAT_KG_PER_WEEK -> null
                else -> (progress.toGoKg / toward).takeIf { it <= MAX_PROJECTION_WEEKS }
            }

            return GoalForecast(
                // Carried so that the wording can fall silent on an arrived goal without being
                // handed the progress as well. A measurement still EXISTS once he has arrived —
                // the trend kept moving — and without this the measured sentence would print
                // underneath the arrival announcement.
                arrived = progress.arrived,
                chosenKgPerWeek = goal.kgPerWeek,
                chosenWeeks = chosenWeeks,
                chosenFinishEpochDay = chosenWeeks?.let { todayEpochDay + weekDays(it) },
                measured = measured,
                measuredTowardGoalKgPerWeek = toward,
                measuredWeeks = measuredWeeks,
                measuredFinishEpochDay = measuredWeeks?.let { todayEpochDay + weekDays(it) },
            )
        }

        /**
         * Counted from TODAY and never from the last weigh-in. A finish line is a statement about
         * the future from now; anchoring the measured one to a reading up to four days old would
         * date the two lines from different days, the chosen one having no reading to anchor to.
         */
        private fun weekDays(weeks: Double): Long = (weeks * 7.0).roundToLong()
    }
}
