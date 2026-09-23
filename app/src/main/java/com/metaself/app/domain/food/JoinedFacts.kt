package com.metaself.app.domain.food

/**
 * What a join carries from the absorbed food onto the food that stays (issue #19, D36 as amended
 * 2026-09-23).
 *
 * Before this, a join moved the absorbed food's names and history and then deleted it, figures and
 * all — so a figure only the absorbed food knew was destroyed moments before the form asked for it
 * again.
 *
 * **The food that stays keeps every figure it holds.** The join question tells the owner it "keeps
 * its name and its numbers", so the ranking is NOT consulted between the two: a group it holds stays,
 * whether the absorbed one's is a label above it or an estimate below it. Only a group it lacks is
 * filled, with the absorbed food's figures and provenance exactly as they stood — source,
 * confidence and the moment it came to be believed. Nothing is averaged or worked out (D4).
 *
 * **What one weighs travels only to the same "one".** The weight names no unit of its own; it is
 * the weight of whatever the food's per-one group counts in, or of a portion when there is none
 * (the fallback logging and the replacement notice both use). So it is carried only when the food
 * that stays will count in that same unit afterwards — because it takes the absorbed food's per-one
 * group with it, or already counts in a unit of the same name. One bar's weight set against one
 * slice would be a measurement nobody made.
 */
object JoinedFacts {

    /** The groups to offer the food that stays; each null one is left as it is. */
    data class Filling(
        val per100g: PerHundredGrams? = null,
        val perUnit: PerUnit? = null,
        val gramsPerUnit: GramsPerUnit? = null,
    )

    /**
     * @param stays what the food that stays knows, or null when it no longer reads back as a food —
     *   which is knowing nothing, so every group is open.
     */
    fun fill(stays: FoodFacts?, absorbed: FoodFacts): Filling {
        val per100g = absorbed.per100g.takeIf { stays?.per100g == null }
        val perUnit = absorbed.perUnit.takeIf { stays?.perUnit == null }

        val weighedAgainst = absorbed.perUnit?.unitName ?: FoodFacts.PORTION
        val willCountIn = (stays?.perUnit ?: perUnit)?.unitName ?: FoodFacts.PORTION
        val gramsPerUnit = absorbed.gramsPerUnit.takeIf {
            stays?.gramsPerUnit == null && ReplacedFacts.sameUnitName(weighedAgainst, willCountIn)
        }

        return Filling(per100g, perUnit, gramsPerUnit)
    }
}
