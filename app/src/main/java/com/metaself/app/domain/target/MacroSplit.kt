package com.metaself.app.domain.target

/** Grams of each macronutrient for one day. */
data class MacroSplit(
    val proteinG: Int,
    val fatG: Int,
    val carbsG: Int,
)

/**
 * Macros derived from body weight and the day's calorie target — decision D10, "derived, not
 * invented".
 *
 * Protein at 1.8 g per kg sits in the middle of the 1.6-2.2 g/kg range the evidence supports for
 * holding on to muscle while losing fat. Fat at 0.8 g per kg is a floor rather than a goal: below
 * roughly that level, hormone production and the absorption of fat-soluble vitamins start to
 * suffer. Carbohydrate is genuinely the remainder, which is why it is the number that moves when
 * the target moves.
 *
 * When protein and fat already account for the whole target — a heavy body on a low target —
 * carbohydrate lands at zero rather than going negative. The safe floor in [SafeFloor] makes that
 * combination unlikely, but "unlikely" is not a reason to return a nonsense number.
 */
object Macros {

    const val PROTEIN_G_PER_KG = 1.8
    const val FAT_G_PER_KG = 0.8

    const val KCAL_PER_G_PROTEIN = 4
    const val KCAL_PER_G_CARB = 4
    const val KCAL_PER_G_FAT = 9

    private const val GRAM_STEP = 5

    fun derive(targetKcal: Int, weightKg: Double): MacroSplit {
        val proteinG = roundToNearest(weightKg * PROTEIN_G_PER_KG, GRAM_STEP)
        val fatG = roundToNearest(weightKg * FAT_G_PER_KG, GRAM_STEP)
        val remainingKcal =
            targetKcal - proteinG * KCAL_PER_G_PROTEIN - fatG * KCAL_PER_G_FAT
        val carbsG = maxOf(
            0,
            roundToNearest(remainingKcal.toDouble() / KCAL_PER_G_CARB, GRAM_STEP),
        )
        return MacroSplit(proteinG = proteinG, fatG = fatG, carbsG = carbsG)
    }
}
