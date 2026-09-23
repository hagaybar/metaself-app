package com.metaself.app.domain.movement

import kotlin.math.roundToInt

/**
 * What one day's movement cost, as a single number.
 *
 * **The larger of two readings, never their sum (D12b).** A band records a long walk twice — once as
 * steps and once as a session with calories attached — and adding them would pay for that walk
 * twice, which is precisely the error the whole surplus-only design exists to prevent.
 *
 * A walk is caught by whichever reading is bigger and counted once. A swim moves no steps at all, so
 * the band's figure carries it. A day with no band behaves exactly as it did before any of this
 * existed, because the step reading is then the only one there is.
 *
 * The stingy direction is chosen deliberately: a swim taken on top of a normal walk is
 * under-credited by whichever of the two is smaller. Eating back calories nobody burned is invisible
 * and stalls everything; being slightly under-credited on a big day is visible and can be eaten
 * through.
 */
data class ActivityEnergy(
    val kcal: Int,
    val source: MovementSource,
) {
    companion object {

        fun of(day: DayMovement, weightKg: Double): ActivityEnergy {
            val fromSteps =
                (day.steps * MovementCredit.KCAL_PER_STEP_PER_KG * weightKg).roundToInt()
            val fromBand = day.activeKcal ?: 0

            return if (fromBand > fromSteps) {
                ActivityEnergy(fromBand, MovementSource.ACTIVE_CALORIES)
            } else {
                ActivityEnergy(fromSteps, MovementSource.STEPS)
            }
        }
    }
}
