package com.metaself.app.domain.food

import com.metaself.app.domain.day.Source

/**
 * What the food form's Save does to each of a food's three groups (D54 §5): leave it alone, empty
 * it, or replace it.
 *
 * **A group whose figures did not change is not touched** — no statement at all, so it keeps its
 * source, confidence and date, whatever provenance the incoming group claims. This is the owner's
 * rule that untouched groups never pass through the clear: before it, every Save cleared all three
 * and rewrote them `TYPED`, which relabelled a packet's figures as his own and, once a group can
 * arrive as an estimate, would have let a guess wipe a label nobody chose to replace.
 *
 * **A group that changed replaces what was there, whatever its rank.** In the editor that means he
 * typed it or accepted it from a review; the ranking protects him from a guess, not from his own
 * choice.
 *
 * Figures are compared by D45's comparison ([ReplacedFacts.sameFigures]); the unit name exactly,
 * so a change of case or spacing moves the stored name — there is no statement that renames a unit
 * without writing its group.
 */
object Correction {

    /** One group's fate. */
    sealed interface Step<out T>

    /** Same figures: no statement at all. */
    data object Keep : Step<Nothing>

    /** Emptied: the group is cleared. */
    data object Clear : Step<Nothing>

    /** Different, or newly known: cleared, then written with the provenance it arrived with. */
    data class Replace<T>(val fact: T) : Step<T>

    data class Plan(
        val per100g: Step<PerHundredGrams>,
        val perUnit: Step<PerUnit>,
        val gramsPerUnit: Step<GramsPerUnit>,
    )

    /**
     * @param stored what the food reads back as, or null when it does not read back as a food —
     *   which is holding nothing, so every arriving group is written.
     *
     * A group that arrives empty is cleared even when the food reads as not holding it: the read
     * drops a stored group it cannot believe, and clearing is what Save always did with one.
     */
    fun plan(stored: FoodFacts?, incoming: FoodFacts): Plan = Plan(
        per100g = step(stored?.per100g, incoming.per100g) { held, arriving ->
            ReplacedFacts.sameFigures(held.nutrients, arriving.nutrients)
        },
        perUnit = step(stored?.perUnit, incoming.perUnit) { held, arriving ->
            held.unitName == arriving.unitName &&
                ReplacedFacts.sameFigures(held.nutrients, arriving.nutrients)
        },
        gramsPerUnit = step(stored?.gramsPerUnit, incoming.gramsPerUnit) { held, arriving ->
            ReplacedFacts.sameFigure(held.grams, arriving.grams) && !relabelledDown(held, arriving)
        },
    )

    /**
     * The one case in which the same figure is written again (D54 §12.7): a weight he accepted as
     * an estimate under a unit the review named or renamed, held at a higher rank. Its figure did
     * not change; what it describes did. Only ever downward, and only for the weight — an accepted
     * group always differs from the stored one, in a figure or in its unit name.
     */
    private fun relabelledDown(held: GramsPerUnit, arriving: GramsPerUnit): Boolean =
        arriving.provenance.source == Source.AI_ESTIMATE && arriving.provenance.rank < held.provenance.rank

    private fun <T : Any> step(held: T?, arriving: T?, same: (T, T) -> Boolean): Step<T> = when {
        arriving == null -> Clear
        held != null && same(held, arriving) -> Keep
        else -> Replace(arriving)
    }
}
