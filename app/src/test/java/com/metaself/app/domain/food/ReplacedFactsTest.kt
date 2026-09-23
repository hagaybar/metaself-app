package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * What counts as a figure REPLACED, and how one action's rows collapse to one line per food
 * (issue #13, D45).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`. The two annotations look
 * identical at the call site and the wrong one produces a test that silently never runs.
 *
 * **The failure mode being guarded against is over-firing.** A notice on every second log would
 * train the owner to dismiss without reading, which is worse than the silence this issue is about —
 * so most of these cases are about saying NOTHING: a blank filled, an identical figure re-logged, a
 * better source arriving at the same numbers, a figure that lost the ranking, a round trip, and a
 * food that did not exist when the action began.
 */
class ReplacedFactsTest {

    // --- The pieces -------------------------------------------------------------------------------

    private fun per100g(
        kcal: Double = 15.0,
        proteinG: Double = 1.0,
        carbsG: Double = 3.0,
        fatG: Double = 0.1,
        source: Source = Source.TYPED,
        confidence: Confidence? = null,
        setAtMillis: Long = 1_000,
    ) = PerHundredGrams(
        Nutrients(kcal, proteinG, carbsG, fatG),
        Provenance(source, confidence, setAtMillis),
    )

    private fun perUnit(
        unitName: String = "bar",
        kcal: Double = 190.0,
        source: Source = Source.TYPED,
        setAtMillis: Long = 1_000,
    ) = PerUnit(unitName, Nutrients(kcal, 15.0, 17.0, 6.0), Provenance(source, null, setAtMillis))

    private fun weighs(grams: Double = 45.0, setAtMillis: Long = 1_000) =
        GramsPerUnit(grams, Provenance(Source.TYPED, null, setAtMillis))

    // --- 1-9: the comparison ----------------------------------------------------------------------

    /** The issue itself: a figure typed as 15 once and 18 later, and the food quietly became 18. */
    @Test
    fun `a typed per-100 g figure replaced by another typed one is a replacement`() {
        val before = per100g(kcal = 15.0)
        val after = per100g(kcal = 18.0, setAtMillis = 2_000)

        val replaced = ReplacedFacts.between(
            FoodFacts(per100g = before),
            FoodFacts(per100g = after),
        )

        assertThat(replaced).containsExactly(Replaced.Per100g(before, after))
    }

    /**
     * Filling a blank is learning, not replacing — in both the shapes a blank arrives in: a food
     * that knew only what one of it is worth, and a food that read back as knowing nothing at all.
     */
    @Test
    fun `a figure the food never held is not a replacement, whether the before is empty or absent`() {
        val after = FoodFacts(per100g = per100g(kcal = 42.0), perUnit = perUnit())

        val hadNoPer100g = ReplacedFacts.between(FoodFacts(perUnit = perUnit()), after)
        val hadNothingAtAll = ReplacedFacts.between(null, after)

        assertThat(hadNoPer100g).isEmpty()
        assertThat(hadNothingAtAll).isEmpty()
    }

    /**
     * The case a DAO row count gets wrong, and the whole reason the before-value is READ.
     *
     * `>=` lets an identical figure through, so the guarded statement fires and the row count comes
     * back 1 — while only the "when this came to be believed" stamp has moved. Nothing the owner
     * can act on changed, so nothing is said.
     */
    @Test
    fun `the identical figure from the same source is not a replacement, though its stamp moved`() {
        val replaced = ReplacedFacts.between(
            FoodFacts(per100g = per100g(kcal = 15.0, setAtMillis = 1_000)),
            FoodFacts(per100g = per100g(kcal = 15.0, setAtMillis = 2_000)),
        )

        assertThat(replaced).isEmpty()
    }

    /** A packet's 15 arriving at a typed 15 changes the story but not the number he can act on. */
    @Test
    fun `the same numbers from a better source are not a replacement`() {
        val replaced = ReplacedFacts.between(
            FoodFacts(per100g = per100g(kcal = 15.0, source = Source.TYPED)),
            FoodFacts(per100g = per100g(kcal = 15.0, source = Source.LABEL, setAtMillis = 2_000)),
        )

        assertThat(replaced).isEmpty()
    }

    /**
     * A guess arriving at a figure he typed writes nothing, so what comes back is what was there.
     * Free, with no branch of its own — which is the point of comparing the two snapshots.
     */
    @Test
    fun `a figure that lost the ranking is not a replacement`() {
        val held = FoodFacts(per100g = per100g(kcal = 15.0, source = Source.TYPED))

        assertThat(ReplacedFacts.between(held, held)).isEmpty()
    }

    /** All three facts can move in one action, and each is reported in the fixed group order. */
    @Test
    fun `all three facts replaced come back in one list, in group order`() {
        val beforePer100g = per100g(kcal = 400.0)
        val beforePerUnit = perUnit(kcal = 190.0)
        val beforeWeighs = weighs(grams = 45.0)
        val afterPer100g = per100g(kcal = 420.0, setAtMillis = 2_000)
        val afterPerUnit = perUnit(kcal = 200.0, setAtMillis = 2_000)
        val afterWeighs = weighs(grams = 50.0, setAtMillis = 2_000)

        val replaced = ReplacedFacts.between(
            FoodFacts(beforePer100g, beforePerUnit, beforeWeighs),
            FoodFacts(afterPer100g, afterPerUnit, afterWeighs),
        )

        assertThat(replaced).containsExactly(
            Replaced.Per100g(beforePer100g, afterPer100g),
            Replaced.PerOne(beforePerUnit, afterPerUnit),
            Replaced.WhatOneWeighs("bar", beforeWeighs, afterWeighs),
        ).inOrder()
    }

    /**
     * Two routes to one real number are one figure, not a change.
     *
     * Built exactly as `DerivedFoods.per100gFrom` builds them — the row's nutrients times
     * `100 / grams` — from 14 kcal in 21 g and 42 kcal in 63 g, which are the same number and
     * differ in a double's last bit (66.66666666666667 against 66.66666666666666). Without a
     * tolerance the owner is told his cucumber changed "from 67 to 67 kcal".
     *
     * The plan's own illustration (10 kcal in 33 g against 20 kcal in 66 g) proves nothing: halving
     * both sides scales by a power of two exactly, so those two come out bit-identical. This pair
     * really differs.
     */
    @Test
    fun `two figures a hair apart are one figure`() {
        val fromASmallRow = Nutrients(14.0, 1.0, 3.0, 0.5) * (100.0 / 21.0)
        val fromABigRow = Nutrients(42.0, 3.0, 9.0, 1.5) * (100.0 / 63.0)
        // The premise: an exact comparison really would call these two different figures.
        assertThat(fromASmallRow).isNotEqualTo(fromABigRow)

        val replaced = ReplacedFacts.between(
            FoodFacts(per100g = PerHundredGrams(fromASmallRow, Provenance(Source.TYPED, null, 1_000))),
            FoodFacts(per100g = PerHundredGrams(fromABigRow, Provenance(Source.TYPED, null, 2_000))),
        )

        assertThat(replaced).isEmpty()
    }

    /** "One bar" becoming "one slice" changes what the number means, whatever the number is. */
    @Test
    fun `a unit renamed is a replacement even when the calories are identical`() {
        val before = perUnit(unitName = "bar", kcal = 190.0)
        val after = perUnit(unitName = "slice", kcal = 190.0, setAtMillis = 2_000)

        val replaced = ReplacedFacts.between(
            FoodFacts(perUnit = before),
            FoodFacts(perUnit = after),
        )

        assertThat(replaced).containsExactly(Replaced.PerOne(before, after))
    }

    /**
     * The shift key is not a change to his food.
     *
     * "bar" stored as "Bar" moves the stored name and nothing he can act on, so it says nothing —
     * the same silence D45 gives a figure whose source improved but whose number did not.
     */
    @Test
    fun `a unit that only changed its case or spacing says nothing`() {
        val replaced = ReplacedFacts.between(
            FoodFacts(perUnit = perUnit(unitName = "fl oz", kcal = 190.0)),
            FoodFacts(perUnit = perUnit(unitName = " FL  OZ ", kcal = 190.0, setAtMillis = 2_000)),
        )

        assertThat(replaced).isEmpty()
    }

    /**
     * A space pasted from a packet label is still a space.
     *
     * A non-breaking space is what a copied unit name carries, and it is not in the `\s` class; if
     * the fold missed it the notice would read "counts 190 kcal per fl. oz, where it counted 190
     * per fl. oz" — two identical-looking units, which is exactly the noise the fold refuses.
     */
    @Test
    fun `a unit spaced with a non-breaking space is the same unit`() {
        val replaced = ReplacedFacts.between(
            FoodFacts(perUnit = perUnit(unitName = "fl. oz", kcal = 190.0)),
            FoodFacts(perUnit = perUnit(unitName = "fl. oz", kcal = 190.0, setAtMillis = 2_000)),
        )

        assertThat(replaced).isEmpty()
    }

    /** The macros are visible in *Correct the food*, so rewriting them silently is the same defect. */
    @Test
    fun `macros moved with the calories unchanged is a replacement`() {
        val before = per100g(kcal = 15.0, proteinG = 1.0, carbsG = 3.0, fatG = 0.1)
        val after = per100g(kcal = 15.0, proteinG = 2.0, carbsG = 2.0, fatG = 0.1, setAtMillis = 2_000)

        val replaced = ReplacedFacts.between(
            FoodFacts(per100g = before),
            FoodFacts(per100g = after),
        )

        assertThat(replaced).containsExactly(Replaced.Per100g(before, after))
    }

    // --- 10-16: the collapse ----------------------------------------------------------------------

    private fun row(
        foodId: Long,
        foodName: String,
        before: FoodFacts?,
        after: FoodFacts,
    ) = RetaughtRow(foodId, foodName, before, after)

    /** A described meal naming one food twice is one thing he did, so it is one line. */
    @Test
    fun `one food touched twice gives one line, from the first before to the last after`() {
        val first = per100g(kcal = 15.0)
        val middle = per100g(kcal = 18.0, setAtMillis = 2_000)
        val last = per100g(kcal = 20.0, setAtMillis = 3_000)

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(1, "Milk", FoodFacts(per100g = first), FoodFacts(per100g = middle)),
                row(1, "Milk", FoodFacts(per100g = middle), FoodFacts(per100g = last)),
            ),
        )

        assertThat(retaught).containsExactly(
            FoodRetaught("Milk", listOf(Replaced.Per100g(first, last))),
        )
    }

    /** Moved and moved back is where it started, so there is nothing to tell him. */
    @Test
    fun `a figure moved and moved back says nothing`() {
        val started = per100g(kcal = 15.0)
        val middle = per100g(kcal = 18.0, setAtMillis = 2_000)
        val ended = per100g(kcal = 15.0, setAtMillis = 3_000)

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(1, "Milk", FoodFacts(per100g = started), FoodFacts(per100g = middle)),
                row(1, "Milk", FoodFacts(per100g = middle), FoodFacts(per100g = ended)),
            ),
        )

        assertThat(retaught).isEmpty()
    }

    /**
     * A food the action itself created and then taught again held nothing when the action began, so
     * announcing a replacement would be announcing a change to a food that did not exist a second
     * earlier. This is the case a per-row verdict cannot express, and why the rows carry snapshots.
     */
    @Test
    fun `a food created during the action and then taught again says nothing`() {
        val made = per100g(kcal = 18.0)
        val then = per100g(kcal = 20.0, setAtMillis = 2_000)

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(1, "Milk", before = null, after = FoodFacts(per100g = made)),
                row(1, "Milk", FoodFacts(per100g = made), FoodFacts(per100g = then)),
            ),
        )

        assertThat(retaught).isEmpty()
    }

    /** The same rule where the first row only filled a blank: the food held no such figure. */
    @Test
    fun `a blank filled and then written over says nothing`() {
        val filled = per100g(kcal = 42.0)
        val then = per100g(kcal = 47.0, setAtMillis = 2_000)
        val counted = perUnit()

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(1, "Milk", FoodFacts(perUnit = counted), FoodFacts(per100g = filled, perUnit = counted)),
                row(1, "Milk", FoodFacts(per100g = filled, perUnit = counted), FoodFacts(per100g = then, perUnit = counted)),
            ),
        )

        assertThat(retaught).isEmpty()
    }

    /** Different facts of one food merge into one line rather than competing for it. */
    @Test
    fun `two rows moving different facts of one food give one line holding both`() {
        val beforePer100g = per100g(kcal = 400.0)
        val afterPer100g = per100g(kcal = 420.0, setAtMillis = 2_000)
        val beforeWeighs = weighs(grams = 45.0)
        val afterWeighs = weighs(grams = 50.0, setAtMillis = 3_000)
        val counted = perUnit()

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(
                    1, "Protein bar",
                    FoodFacts(beforePer100g, counted, beforeWeighs),
                    FoodFacts(afterPer100g, counted, beforeWeighs),
                ),
                row(
                    1, "Protein bar",
                    FoodFacts(afterPer100g, counted, beforeWeighs),
                    FoodFacts(afterPer100g, counted, afterWeighs),
                ),
            ),
        )

        assertThat(retaught).containsExactly(
            FoodRetaught(
                "Protein bar",
                listOf(
                    Replaced.Per100g(beforePer100g, afterPer100g),
                    Replaced.WhatOneWeighs("bar", beforeWeighs, afterWeighs),
                ),
            ),
        )
    }

    /** Several foods give one line each, in the order the action first touched them. */
    @Test
    fun `two foods give two lines, in the order they were first touched`() {
        val milkBefore = per100g(kcal = 60.0)
        val milkAfter = per100g(kcal = 64.0, setAtMillis = 2_000)
        val breadBefore = per100g(kcal = 250.0)
        val breadAfter = per100g(kcal = 265.0, setAtMillis = 2_000)

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(2, "Bread", FoodFacts(per100g = breadBefore), FoodFacts(per100g = breadAfter)),
                row(1, "Milk", FoodFacts(per100g = milkBefore), FoodFacts(per100g = milkAfter)),
            ),
        )

        assertThat(retaught).containsExactly(
            FoodRetaught("Bread", listOf(Replaced.Per100g(breadBefore, breadAfter))),
            FoodRetaught("Milk", listOf(Replaced.Per100g(milkBefore, milkAfter))),
        ).inOrder()
    }

    /**
     * Two foods can print one name — the same word under two brands — so the grouping is by the
     * identity the store returned, never by the spelling shown. Collapsing on the name would merge
     * two real foods into one sentence and lose one of the two changes.
     */
    @Test
    fun `two foods sharing a display name stay two lines`() {
        val plainBefore = per100g(kcal = 60.0)
        val plainAfter = per100g(kcal = 64.0, setAtMillis = 2_000)
        val brandedBefore = per100g(kcal = 55.0)
        val brandedAfter = per100g(kcal = 58.0, setAtMillis = 2_000)

        val retaught = ReplacedFacts.collapse(
            listOf(
                row(1, "Milk", FoodFacts(per100g = plainBefore), FoodFacts(per100g = plainAfter)),
                row(2, "Milk", FoodFacts(per100g = brandedBefore), FoodFacts(per100g = brandedAfter)),
            ),
        )

        assertThat(retaught).containsExactly(
            FoodRetaught("Milk", listOf(Replaced.Per100g(plainBefore, plainAfter))),
            FoodRetaught("Milk", listOf(Replaced.Per100g(brandedBefore, brandedAfter))),
        ).inOrder()
    }
}
