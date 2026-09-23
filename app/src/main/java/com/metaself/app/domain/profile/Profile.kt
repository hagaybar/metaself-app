package com.metaself.app.domain.profile

/**
 * Everything the daily target is computed from. Entered once at setup, edited when it changes.
 *
 * [allowBelowFloor] records a decision, not a preference: a requested rate of loss can put the
 * target under the safe floor, and when it does the app says so plainly and the choice is the
 * owner's (decision D9). It is stored so that the app never silently re-imposes a limit that has
 * already been overruled.
 */
data class Profile(
    val heightCm: Int,
    val birthYear: Int,
    val sex: Sex,
    val weightKg: Double,
    val activity: ActivityLevel,
    val goal: Goal,
    val allowBelowFloor: Boolean = false,
)

/**
 * Age in whole years.
 *
 * The current year is a parameter rather than read from the clock, so that the arithmetic stays
 * pure and no test changes its answer overnight on the 31st of December.
 */
fun Profile.ageYears(currentYear: Int): Int = currentYear - birthYear
