package com.metaself.app.ui.target

import com.metaself.app.domain.profile.ActivityLevel
import com.metaself.app.domain.profile.GoalDirection
import com.metaself.app.domain.profile.Profile
import com.metaself.app.domain.target.DailyTarget
import com.metaself.app.domain.target.DailyTargetCalculator
import com.metaself.app.domain.target.Macros
import java.util.Locale

/** One step of the arithmetic: what it produced, and why. */
data class ExplanationLine(
    val heading: String,
    val detail: String,
)

/**
 * The arithmetic behind the daily target, in sentences.
 *
 * Pure, so every sentence the owner reads is pinned by a test. In `ui/` rather than `domain/` so
 * that no calculation ever has to reach for a phrase — the same division the version marker uses.
 *
 * Numbers are formatted with [Locale.US] deliberately: the grouping separator has to be stable, or
 * a test passing on this machine fails on a differently configured one.
 */
object TargetWording {

    fun arithmetic(target: DailyTarget, profile: Profile): List<ExplanationLine> = buildList {
        add(
            ExplanationLine(
                heading = "Resting burn: ${kcal(target.restingBurnKcal)}",
                detail = "What your body spends in a day doing nothing at all, worked out from " +
                    "your height, weight, age and sex. It is an average for bodies like yours, " +
                    "not a measurement of yours.",
            ),
        )
        add(
            ExplanationLine(
                heading = "A normal day: ${kcal(target.maintenanceKcal)}",
                detail = "Resting burn multiplied by ${factor(profile.activity)}, for " +
                    "${activityWords(profile.activity)}. Eat this and your weight holds.",
            ),
        )
        add(goalLine(target, profile))
        if (target.floorApplied) {
            add(
                ExplanationLine(
                    heading = "Held at the safe floor: ${kcal(target.floorKcal)}",
                    detail = "That rate would have meant ${kcal(target.requestedKcal)}, which is " +
                        "under the least this app will suggest for your body — your resting " +
                        "burn, or the conventional minimum, whichever is higher. You can " +
                        "overrule this.",
                ),
            )
        }
        if (target.belowFloorByChoice) {
            add(
                ExplanationLine(
                    heading = "Below the safe floor, by your choice: ${kcal(target.floorKcal)} " +
                        "is the floor",
                    detail = "You asked for this rate knowing it goes under the floor. The app " +
                        "is not arguing; it is only not staying quiet about it.",
                ),
            )
        }
        add(
            ExplanationLine(
                heading = "Your daily target: ${kcal(target.kcal)}",
                detail = "This is the number the rest of the app counts against.",
            ),
        )
    }

    fun macros(target: DailyTarget): List<ExplanationLine> = listOf(
        ExplanationLine(
            heading = "Protein: ${target.macros.proteinG} g",
            detail = "${Macros.PROTEIN_G_PER_KG} g per kg of body weight — enough to hold on to " +
                "muscle while the weight comes off.",
        ),
        ExplanationLine(
            heading = "Fat: ${target.macros.fatG} g",
            detail = "${Macros.FAT_G_PER_KG} g per kg, as a floor rather than a goal. Below " +
                "roughly this, hormones and vitamin absorption suffer.",
        ),
        ExplanationLine(
            heading = "Carbohydrate: ${target.macros.carbsG} g",
            detail = "Whatever the calories leave over once protein and fat are accounted for. " +
                "This is the number that moves when your target moves, and it is what is left.",
        ),
    )

    private fun goalLine(target: DailyTarget, profile: Profile): ExplanationLine =
        when (profile.goal.direction) {
            GoalDirection.HOLD -> ExplanationLine(
                heading = "To hold your weight: no change",
                detail = "A normal day is the target.",
            )

            GoalDirection.LOSE -> ExplanationLine(
                heading = "To lose ${rate(profile.goal.kgPerWeek)} kg a week: " +
                    "−${target.goalShiftKcal.absoluteText()} kcal",
                detail = "A kilogram of body fat is about " +
                    "${grouped(DailyTargetCalculator.KCAL_PER_KG_BODY_FAT.toInt())} kcal, spread " +
                    "over seven days. About, not exactly: bodies are not an exchange rate.",
            )

            GoalDirection.GAIN -> ExplanationLine(
                heading = "To gain ${rate(profile.goal.kgPerWeek)} kg a week: " +
                    "+${target.goalShiftKcal.absoluteText()} kcal",
                detail = "A kilogram is about " +
                    "${grouped(DailyTargetCalculator.KCAL_PER_KG_BODY_FAT.toInt())} kcal, spread " +
                    "over seven days. About, not exactly: bodies are not an exchange rate.",
            )
        }

    private fun activityWords(level: ActivityLevel): String = when (level) {
        ActivityLevel.SEDENTARY -> "mostly sitting"
        ActivityLevel.LIGHT -> "lightly active"
        ActivityLevel.MODERATE -> "moderately active"
        ActivityLevel.ACTIVE -> "active"
        ActivityLevel.VERY_ACTIVE -> "very active"
    }

    private fun factor(level: ActivityLevel): String =
        String.format(Locale.US, "%.3f", level.factor).trimEnd('0').trimEnd('.')

    private fun rate(kgPerWeek: Double): String =
        String.format(Locale.US, "%.2f", kgPerWeek).trimEnd('0').trimEnd('.')

    private fun kcal(value: Int): String = "${grouped(value)} kcal"

    private fun grouped(value: Int): String = String.format(Locale.US, "%,d", value)

    private fun Int.absoluteText(): String = grouped(if (this < 0) -this else this)
}
