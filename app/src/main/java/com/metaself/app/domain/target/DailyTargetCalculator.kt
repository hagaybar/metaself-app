package com.metaself.app.domain.target

import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.profile.ageYears
import kotlin.math.roundToInt

/**
 * Profile in, daily target out. The whole of decision D9's arithmetic, in one readable place.
 *
 * The order is: what the body burns at rest, times how much a normal day moves, shifted by the rate
 * of change the owner asked for, then held at the safe floor unless he has already overruled it.
 */
object DailyTargetCalculator {

    /**
     * The calories in a kilogram of body fat.
     *
     * The standard approximation, and an approximation it remains — body-composition change is not
     * a fixed exchange rate. The screen says as much where it uses this number.
     */
    const val KCAL_PER_KG_BODY_FAT = 7700.0

    private const val DAYS_PER_WEEK = 7.0
    private const val KCAL_STEP = 10
    private const val SHIFT_STEP = 5

    /**
     * @param burnAdjustmentKcal the standing correction from what has actually happened (D25).
     *   Added to maintenance, which is exactly where the error it corrects came from: the activity
     *   multiplier is five buckets from a dropdown, and adjacent ones are hundreds of calories
     *   apart. Zero until there is a month of logging to measure against.
     */
    fun of(profile: Profile, currentYear: Int, burnAdjustmentKcal: Int = 0): DailyTarget {
        val restingBurn = RestingBurn.kcal(
            sex = profile.sex,
            weightKg = profile.weightKg,
            heightCm = profile.heightCm,
            ageYears = profile.ageYears(currentYear),
        )
        val maintenance = restingBurn * profile.activity.factor + burnAdjustmentKcal

        val dailyShift = when (profile.goal.direction) {
            GoalDirection.HOLD -> 0.0
            GoalDirection.LOSE -> -profile.goal.kgPerWeek * KCAL_PER_KG_BODY_FAT / DAYS_PER_WEEK
            GoalDirection.GAIN -> profile.goal.kgPerWeek * KCAL_PER_KG_BODY_FAT / DAYS_PER_WEEK
        }

        val requested = roundToNearest(maintenance + dailyShift, KCAL_STEP)
        val floor = SafeFloor.kcal(profile.sex, restingBurn)
        val breachesFloor = requested < floor

        val floorApplied = breachesFloor && !profile.allowBelowFloor
        val kcal = if (floorApplied) floor else requested

        return DailyTarget(
            kcal = kcal,
            requestedKcal = requested,
            floorKcal = floor,
            restingBurnKcal = restingBurn.roundToInt(),
            maintenanceKcal = roundToNearest(maintenance, KCAL_STEP),
            goalShiftKcal = roundToNearest(dailyShift, SHIFT_STEP),
            floorApplied = floorApplied,
            belowFloorByChoice = breachesFloor && profile.allowBelowFloor,
            macros = Macros.derive(kcal, profile.weightKg),
        )
    }
}
