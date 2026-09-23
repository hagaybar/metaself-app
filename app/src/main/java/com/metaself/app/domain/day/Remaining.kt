package com.metaself.app.domain.day

import com.metaself.app.domain.target.DailyTarget

/**
 * What is left of the day's target after what has been eaten.
 *
 * Every figure may go negative, and is meant to: "−210" is a truthful answer and "0" is not. Only
 * [overTarget] is a judgement, and it is about calories alone — being past the protein figure is
 * not being over target, because protein is a floor to reach rather than a ceiling to stay under.
 */
data class Remaining(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val overTarget: Boolean,
) {
    companion object {

        fun of(target: DailyTarget, eaten: DayTotals): Remaining = Remaining(
            kcal = target.kcal - eaten.kcal,
            proteinG = target.macros.proteinG - eaten.proteinG,
            carbsG = target.macros.carbsG - eaten.carbsG,
            fatG = target.macros.fatG - eaten.fatG,
            overTarget = eaten.kcal > target.kcal,
        )
    }
}
