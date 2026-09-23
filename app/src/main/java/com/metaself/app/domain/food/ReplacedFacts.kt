package com.metaself.app.domain.food

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * What a food knew before an action touched it, against what it knows after, and the one pure
 * comparison that decides whether anything worth saying happened (issue #13, D45).
 *
 * **Why the before-value is read rather than inferred.** The three guarded statements are written
 * `WHERE id = :id AND (rank IS NULL OR :rank >= rank)`, so an identical figure offered again gets
 * through: the row is written, only the "when this came to be believed" stamp moves, and the DAO
 * reports one row changed. A caller watching row counts would therefore announce a change every
 * time the same cucumber was logged twice — the defect of #13 in a new direction. So the store
 * reports the snapshot it saw immediately before offering, and this compares the two.
 *
 * **Why the source is not part of the comparison.** A packet's 15 kcal arriving at a typed 15
 * changes the story but not a number the owner can act on, and the food screen already shows where
 * each figure came from.
 *
 * **Why the rows carry snapshots rather than verdicts.** One action can name one food twice: a
 * described meal with two rows of milk, or a row that CREATES the food and a second row that then
 * writes over what the first just wrote. A per-row verdict cannot see that the food held nothing
 * when the action began, and would announce a replacement about a food that did not exist a second
 * earlier. So nothing decides until [collapse] runs one comparison per food, from what it held
 * before the first row that touched it to what it holds after the last.
 *
 * **Why the collapse keys on identity rather than on spelling.** Two foods can print one display
 * name — the same word under two brands — so grouping by name would merge two real foods into one
 * sentence and lose one of the two changes. The key is the id the store returned; it is then thrown
 * away and never reaches a sentence.
 */
sealed interface Replaced {

    /** What 100 grams of it are worth, before and after. */
    data class Per100g(val before: PerHundredGrams, val after: PerHundredGrams) : Replaced

    /** What one of it is worth, before and after. The unit's own name may have moved too. */
    data class PerOne(val before: PerUnit, val after: PerUnit) : Replaced

    /**
     * What one of it weighs, before and after.
     *
     * [unitName] is what "one" is called on the food as it now stands, because [GramsPerUnit] does
     * not carry it and the sentence has to say "one bar weighs 50 g" rather than "one weighs 50 g".
     * [FoodFacts.PORTION] when the food knows no unit of its own, exactly as `FoodWording` falls
     * back.
     */
    data class WhatOneWeighs(
        val unitName: String,
        val before: GramsPerUnit,
        val after: GramsPerUnit,
    ) : Replaced
}

/**
 * One food, named once, with every fact of it this action replaced.
 *
 * **No id field**, deliberately: no identifier can then reach a sentence even by accident.
 */
data class FoodRetaught(val foodName: String, val replaced: List<Replaced>)

/**
 * One row of one action, carrying the two snapshots it saw — never an already-computed verdict.
 *
 * [before] is null both when the row created the food and when the food held nothing; both mean the
 * same thing to the comparison.
 */
data class RetaughtRow(
    val foodId: Long,
    val foodName: String,
    val before: FoodFacts?,
    val after: FoodFacts,
)

object ReplacedFacts {

    /**
     * Every fact the food held, still holds, and holds different figures for.
     *
     * One entry per fact group — per 100 g, one of it, what one weighs — never across groups, and
     * always in that order. A group the food did not hold before is a blank being filled, which is
     * learning rather than replacing; a group nothing was offered for, and a group whose offer lost
     * the ranking, come back identical and so report nothing with no branch of their own.
     */
    fun between(before: FoodFacts?, after: FoodFacts): List<Replaced> {
        if (before == null) return emptyList()
        return buildList {
            val heldPer100g = before.per100g
            val holdsPer100g = after.per100g
            if (heldPer100g != null && holdsPer100g != null &&
                !sameFigures(heldPer100g.nutrients, holdsPer100g.nutrients)
            ) {
                add(Replaced.Per100g(heldPer100g, holdsPer100g))
            }

            val heldPerUnit = before.perUnit
            val holdsPerUnit = after.perUnit
            if (heldPerUnit != null && holdsPerUnit != null &&
                (
                    !sameUnitName(heldPerUnit.unitName, holdsPerUnit.unitName) ||
                        !sameFigures(heldPerUnit.nutrients, holdsPerUnit.nutrients)
                    )
            ) {
                add(Replaced.PerOne(heldPerUnit, holdsPerUnit))
            }

            val heldWeight = before.gramsPerUnit
            val holdsWeight = after.gramsPerUnit
            if (heldWeight != null && holdsWeight != null &&
                !sameFigure(heldWeight.grams, holdsWeight.grams)
            ) {
                // What "one" is called on the food as it now stands: the weight itself does not
                // carry the name, and the sentence has to say which one it means.
                add(
                    Replaced.WhatOneWeighs(
                        unitName = after.perUnit?.unitName ?: FoodFacts.PORTION,
                        before = heldWeight,
                        after = holdsWeight,
                    ),
                )
            }
        }
    }

    /** The single-food case, empty when there is nothing to say. */
    fun one(foodName: String, before: FoodFacts?, after: FoodFacts): List<FoodRetaught> {
        val replaced = between(before, after)
        return if (replaced.isEmpty()) emptyList() else listOf(FoodRetaught(foodName, replaced))
    }

    /**
     * One line per food, comparing what it held before the first row that touched it with what it
     * holds after the last.
     *
     * Grouped by the food's identity, in the order the action first touched each food; a food whose
     * comparison comes back empty is dropped entirely, which is how a round trip, a food created
     * during the action and a blank filled and then written over all say nothing.
     */
    fun collapse(rows: List<RetaughtRow>): List<FoodRetaught> =
        rows.groupBy { it.foodId }.values.flatMap { touched ->
            one(touched.last().foodName, touched.first().before, touched.last().after)
        }

    /**
     * "One bar" and "one Bar" are one unit; "one bar" and "one slice" are not.
     *
     * Case and spacing are how it was typed, not what it means, and a notice reading "counts 190
     * kcal per Bar, where it counted 190 per bar" tells him about his own shift key rather than
     * about his food — the same noise D45 refuses for a figure whose source improved but whose
     * number did not. The stored name still moves; only the decision to SPEAK ignores the
     * difference. Deliberately not `FoodKeys.nameKey`: that rule turns punctuation into separators
     * and refuses a name that normalises to nothing, and a unit may legitimately be "%" or "fl. oz".
     */
    internal fun sameUnitName(held: String, holds: String): Boolean = foldUnit(held) == foldUnit(holds)

    private fun foldUnit(unitName: String): String =
        unitName.split(SPACING).filter { it.isNotEmpty() }.joinToString(" ")
            .lowercase(Locale.ROOT)

    /**
     * Every run of spacing, not just the three keys on a keyboard.
     *
     * A unit name pasted from a label can carry a non-breaking space, which `\s` alone does not
     * match; left unfolded it would make "fl. oz" and "fl. oz" two units and produce a notice
     * reading "counts 190 kcal per fl. oz, where it counted 190 per fl. oz" — the very noise
     * [sameUnitName] exists to refuse. `\p{Z}` is Unicode's separator category, which is where the
     * non-breaking space lives.
     */
    private val SPACING = Regex("[\\s\\p{Z}]+")

    /**
     * Calories and all three macros, because a re-log can move the macros and leave the calories —
     * and the macros are visible in *Correct the food*, so rewriting them silently is the same
     * defect.
     */
    private fun sameFigures(held: Nutrients, holds: Nutrients): Boolean =
        sameFigure(held.kcal, holds.kcal) &&
            sameFigure(held.proteinG, holds.proteinG) &&
            sameFigure(held.carbsG, holds.carbsG) &&
            sameFigure(held.fatG, holds.fatG)

    /**
     * Two figures to about nine significant figures, not two doubles bit for bit.
     *
     * `DerivedFoods.per100gFrom` scales a row by a factor it computes first, so two routes to one
     * real number — 14 kcal in 21 g and 42 kcal in 63 g — differ in a double's last bit. Compared
     * exactly, a cucumber would be reported as having changed "from 67 to 67 kcal". The smallest
     * change any form can express is 0.1 of a gram, which is orders of magnitude larger than this
     * tolerance, so nothing he can actually type is swallowed by it.
     */
    private fun sameFigure(held: Double, holds: Double): Boolean {
        if (held == holds) return true
        val apart = abs(held - holds)
        // The absolute floor is for figures at or near zero, where a relative comparison has
        // nothing to be relative to.
        if (apart <= NEAR_ENOUGH_ABSOLUTE) return true
        return apart <= NEAR_ENOUGH_RELATIVE * max(abs(held), abs(holds))
    }

    /** Nine significant figures: far finer than anything a form can express, far coarser than a bit. */
    private const val NEAR_ENOUGH_RELATIVE = 1e-9

    /** The floor for figures at zero, in the same spirit as the relative test. */
    private const val NEAR_ENOUGH_ABSOLUTE = 1e-9
}
