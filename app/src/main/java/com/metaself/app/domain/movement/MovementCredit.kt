package com.metaself.app.domain.movement

import com.metaself.app.domain.profile.Goal
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.target.DailyTargetCalculator
import kotlin.math.roundToInt

/**
 * One day's movement, as the phone and the band recorded it.
 *
 * @property activeKcal what the band says the day's non-resting energy was, or null when nothing
 *   reported one. Kept beside the steps rather than instead of them: a walk appears in BOTH, and
 *   adding them would pay for it twice (D12b).
 * @property sessions what he actually did, by name, for a screen rather than for arithmetic.
 */
data class DayMovement(
    val epochDay: Long,
    val steps: Int,
    val activeKcal: Int? = null,
    val sessions: List<ExerciseSession> = emptyList(),
    /**
     * What the workouts the owner typed for this day cost, by [MetEstimate] or his own figure —
     * summed across them, since two typed sessions are two different things done. Zero until the
     * store that holds typed workouts exists (phase 2). It is a THIRD reading beside steps and the
     * band, never added to either (D60).
     */
    val typedWorkoutsKcal: Int = 0,
)

/** One workout the band recorded: what it was, and how long it went on. */
data class ExerciseSession(val name: String, val minutes: Int)

/**
 * Where a credit came from.
 *
 * The same idea as a food item's source, and here for the same reason: further kinds of exercise
 * are expected later, and when they arrive they should slot in beside this rather than replace it.
 * That pattern has already paid for itself once in this app — a barcode's label became a new
 * source of nutrition numbers without anything being rewritten.
 */
enum class MovementSource {

    /** Steps, from the phone itself or from a band, via Health Connect. */
    STEPS,

    /** The band's own figure for the day's non-resting energy, which beat the step count. */
    ACTIVE_CALORIES,

    /** What the owner's typed workouts cost, by the MET table, which beat both the other readings. */
    TYPED_WORKOUT,
}

/**
 * What today's movement is worth, if anything.
 *
 * @property normalSteps his own usual day, which earns nothing. The daily target already assumes he
 *   moves a normal amount; paying again for an ordinary Tuesday is paying twice for the same
 *   walking, and is why most calorie apps hand back hundreds of calories nobody burned (D12).
 * @property grossKcal the arithmetic before the discount and the cap, kept so the screen can show
 *   its working rather than only its conclusion (D9).
 */
data class MovementCredit(
    val source: MovementSource,
    val energyKcal: Int,
    val normalEnergyKcal: Int,
    val extraKcal: Int,
    val kcal: Int,
    val capped: Boolean,
) {
    companion object {

        /**
         * The net cost of walking, per step, per kilogram of the owner.
         *
         * About half a kilocalorie per kilogram per kilometre, over a stride of roughly 0.75 m. NET
         * rather than gross, because the target already pays for existing — this is only what the
         * walking adds on top.
         */
        const val KCAL_PER_STEP_PER_KG = 0.000375

        /**
         * How much of it is credited.
         *
         * A quarter is held back because every part of this is an estimate stacked on an estimate:
         * a pedometer's count, an assumed stride, an average cost of walking, and — where the band
         * supplies the figure instead — a wrist-worn guess that is known to run high. The error
         * that matters is the generous one: eating back calories nobody burned is invisible and
         * stalls everything, whereas being slightly under-credited on a big day is something the
         * owner can see and simply eat through.
         */
        const val CREDITED_SHARE = 0.75

        fun of(
            today: ActivityEnergy,
            normalEnergyKcal: Int,
            capKcal: Int,
        ): MovementCredit {
            val extra = (today.kcal - normalEnergyKcal).coerceAtLeast(0)
            val discounted = (extra * CREDITED_SHARE).roundToInt()
            val credited = discounted.coerceAtMost(capKcal).coerceAtLeast(0)

            return MovementCredit(
                source = today.source,
                energyKcal = today.kcal,
                normalEnergyKcal = normalEnergyKcal,
                extraKcal = extra,
                kcal = credited,
                capped = discounted > capKcal,
            )
        }
    }
}

/**
 * The most a day's movement may ever give back.
 *
 * A pedometer can be wrong in a large way — a phone in a car on a rough road invents thousands of
 * steps — and the linear cost of walking is least trustworthy at the extremes. Both are reasons to
 * stop somewhere.
 *
 * But the binding reason is different: **a big walking day must never cancel the diet.** The cap is
 * therefore tied to the goal rather than fixed, so that at most half of any day's deficit can be
 * given back. A flat 300 would erase the ENTIRE deficit of a quarter-kilogram-a-week goal, turning a
 * hard walk into a maintenance day without saying so.
 */
object MovementCap {

    /** The ceiling, whatever the goal. Roughly a snack, and past where a pedometer is believable. */
    const val MOST_KCAL = 300

    /** At most half a day's deficit, so something is always still being lost. */
    private const val SHARE_OF_DEFICIT = 0.5

    fun forGoal(goal: Goal): Int = when (goal.direction) {
        // Nothing to protect: if he burns more while holding his weight, he may eat more.
        GoalDirection.HOLD, GoalDirection.GAIN -> MOST_KCAL

        GoalDirection.LOSE -> {
            val dailyDeficit =
                goal.kgPerWeek * DailyTargetCalculator.KCAL_PER_KG_BODY_FAT / DAYS_PER_WEEK
            (dailyDeficit * SHARE_OF_DEFICIT).roundToInt().coerceIn(0, MOST_KCAL)
        }
    }

    private const val DAYS_PER_WEEK = 7.0
}
