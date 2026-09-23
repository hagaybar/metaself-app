package com.metaself.app.domain.target

import com.metaself.app.domain.profile.Sex
import kotlin.math.roundToInt

/**
 * The lowest daily target the app will propose — the first guardrail of decision D9.
 *
 * Two ideas, combined by taking whichever is higher. Never proposing less than resting burn means
 * the app never suggests eating below what the body spends lying still. The fixed figures are the
 * conventional clinical minimum and exist as a backstop for a small body whose computed resting
 * burn is itself low. For most bodies the floor is therefore personal, and conventional only at the
 * extreme.
 *
 * This is a floor on what the app PROPOSES, not a limit on the owner. Asking for a rate that
 * breaches it is stated plainly and then allowed — see [DailyTargetCalculator] and
 * [com.metaself.app.domain.profile.Profile.allowBelowFloor].
 */
object SafeFloor {

    const val CONVENTIONAL_MALE_KCAL = 1500
    const val CONVENTIONAL_FEMALE_KCAL = 1200

    fun kcal(sex: Sex, restingBurnKcal: Double): Int = maxOf(
        restingBurnKcal.roundToInt(),
        when (sex) {
            Sex.MALE -> CONVENTIONAL_MALE_KCAL
            Sex.FEMALE -> CONVENTIONAL_FEMALE_KCAL
        },
    )
}
