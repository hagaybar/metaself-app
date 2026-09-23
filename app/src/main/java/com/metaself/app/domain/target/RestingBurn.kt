package com.metaself.app.domain.target

import com.metaself.app.domain.profile.Sex

/**
 * What the body spends in a day doing nothing at all, by the Mifflin-St Jeor equation.
 *
 * Chosen over the older Harris-Benedict equation because clinical guidance has preferred it since
 * 2005: it is measurably less wrong across ordinary body types. It remains a population average and
 * is commonly out by around 10% for an individual, which is the whole reason decision D9 requires
 * the arithmetic to be shown rather than the answer to be trusted.
 */
object RestingBurn {

    private const val KCAL_PER_KG = 10.0
    private const val KCAL_PER_CM = 6.25
    private const val KCAL_PER_YEAR = 5.0
    private const val MALE_CONSTANT = 5.0
    private const val FEMALE_CONSTANT = -161.0

    fun kcal(sex: Sex, weightKg: Double, heightCm: Int, ageYears: Int): Double =
        KCAL_PER_KG * weightKg +
            KCAL_PER_CM * heightCm -
            KCAL_PER_YEAR * ageYears +
            when (sex) {
                Sex.MALE -> MALE_CONSTANT
                Sex.FEMALE -> FEMALE_CONSTANT
            }
}
