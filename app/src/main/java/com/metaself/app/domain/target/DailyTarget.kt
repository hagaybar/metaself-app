package com.metaself.app.domain.target

/**
 * A day's calorie target and everything that went into it.
 *
 * Every intermediate number is kept because decision D9 requires the arithmetic to be shown. If
 * this carried only the final figure, the screen would have to recompute the steps in order to
 * display them — two implementations of one sum, free to disagree.
 *
 * @property kcal what the app proposes for the day.
 * @property requestedKcal what the goal rate alone implied, before the floor was considered.
 * @property floorKcal the lowest target the app will propose for this body.
 * @property restingBurnKcal what the body spends doing nothing.
 * @property maintenanceKcal resting burn times the activity factor: a normal day.
 * @property goalShiftKcal the daily change the goal rate implies; negative when losing.
 * @property floorApplied the requested target was below the floor and was held at it.
 * @property belowFloorByChoice the requested target was below the floor and the owner has already
 *   overruled it, so it stands. The screen still says so; it does not stay silent.
 */
data class DailyTarget(
    val kcal: Int,
    val requestedKcal: Int,
    val floorKcal: Int,
    val restingBurnKcal: Int,
    val maintenanceKcal: Int,
    val goalShiftKcal: Int,
    val floorApplied: Boolean,
    val belowFloorByChoice: Boolean,
    val macros: MacroSplit,
)
