package com.metaself.app.domain.day

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.product.Product
import com.metaself.app.domain.repeat.withAmount
import org.junit.jupiter.api.Test

/**
 * D44 (issue #35): a figure the owner changes in *Correct this item* stops claiming the packet
 * stated it.
 *
 * The rule is a statement about two rows — the one on the record and the one being saved — so it is
 * tested here, as arithmetic, with no dispatcher, repository or screen in the way. That the view
 * model actually applies it is a different claim, made in `DayViewModelTest`.
 *
 * The boundary this file exists to hold is the amount: changing how much was eaten rescales the
 * figures without correcting any of them, and a rule that read that as a correction would quietly
 * drop genuine label readings to the owner's own number.
 */
class CorrectedItemTest {

    /**
     * The same packet `DayViewModelTest` scans: half a gram of fat per 100 g, so a whole-gram row
     * cannot be worked back to it (issue #28).
     */
    private val ricePacket = Product(
        barcode = "2000000000011",
        name = "Rice cakes",
        brand = null,
        kcalPer100g = 387.4,
        proteinPer100g = 8.3,
        carbsPer100g = 81.6,
        fatPer100g = 0.5,
    )

    /** 30 g of it as the scan logs it (D38): 116 kcal, 2 / 24 / 0 g, whole grams, LABEL. */
    private val scanned = ricePacket.toFoodItem(30.0)!!.copy(id = 5, foodId = 7)

    @Test
    fun `the fixture is the scanned row this file reasons about`() {
        assertThat(scanned.source).isEqualTo(Source.LABEL)
        assertThat(scanned.confidence).isNull()
        assertThat(listOf(scanned.kcal, scanned.proteinG, scanned.carbsG, scanned.fatG))
            .containsExactly(116, 2, 24, 0)
            .inOrder()
        assertThat(scanned.portionAmount).isEqualTo(30.0)
        assertThat(scanned.portionUnit).isEqualTo("g")
    }

    // --- 1-2: a figure he changes is his ---------------------------------------------------------

    /**
     * The issue's own case. Nothing but the source may move: the correction is his, and the row's
     * identity, name, portion, amount and food link are the correction's business, not this rule's.
     */
    @Test
    fun `correcting the calories of a label row makes the figures his`() {
        val after = scanned.copy(kcal = 140)

        val corrected = after.correctionOf(scanned)

        assertThat(corrected.source).isEqualTo(Source.TYPED)
        assertThat(corrected.confidence).isNull()
        assertThat(corrected).isEqualTo(after.copy(source = Source.TYPED))
    }

    /**
     * Fat is the one that matters: D38 rounds half a gram per 100 g down to a row of 0 g, so fat is
     * the figure he is most likely to be correcting on a scanned row in the first place.
     */
    @Test
    fun `correcting any macro of a label row makes the figures his`() {
        val corrections = listOf(
            scanned.copy(proteinG = 3),
            scanned.copy(carbsG = 25),
            scanned.copy(fatG = 1),
        )

        corrections.forEach { after ->
            assertThat(after.correctionOf(scanned).source).isEqualTo(Source.TYPED)
        }
    }

    // --- 3-9: what is not a correction of a figure ------------------------------------------------

    /** A new name is a claim about what it was, never about what the packet declared. */
    @Test
    fun `correcting only the name leaves the label's figures the label's`() {
        val after = scanned.copy(name = "Puffed rice")

        assertThat(after.correctionOf(scanned)).isEqualTo(after)
        assertThat(after.correctionOf(scanned).source).isEqualTo(Source.LABEL)
    }

    /**
     * The boundary the whole rule turns on. Moving the amount to 60 g makes the editor rescale the
     * row through the very function used here, so the figures that arrive are not his — they are the
     * packet's reading of twice as much. A comparison that did not allow for the amount would call
     * this a correction and throw the label away.
     */
    @Test
    fun `changing only the amount rescales the label's figures without claiming them`() {
        val after = scanned.withAmount(60.0)

        // Worked from the ROW, not from the packet: 116 doubled is 232 and 2 doubled is 4, where
        // the label per 100 g would have given 232.44 and 4.98. The row is what was stored.
        assertThat(listOf(after.kcal, after.proteinG, after.carbsG, after.fatG))
            .containsExactly(232, 4, 48, 0)
            .inOrder()
        assertThat(after.correctionOf(scanned).source).isEqualTo(Source.LABEL)
    }

    /**
     * The editor always rescales from the row as logged rather than from whatever the boxes hold, so
     * 30 g → 60 g → 30 g arrives back at exactly the figures it started from. Equality is by
     * construction here, not by luck.
     */
    @Test
    fun `changing the amount down and back again is still no correction`() {
        val at60 = scanned.withAmount(60.0)
        val backAt30 = scanned.withAmount(30.0)

        assertThat(at60.correctionOf(scanned).source).isEqualTo(Source.LABEL)
        assertThat(backAt30.correctionOf(scanned).source).isEqualTo(Source.LABEL)
        assertThat(listOf(backAt30.kcal, backAt30.proteinG, backAt30.carbsG, backAt30.fatG))
            .containsExactly(116, 2, 24, 0)
            .inOrder()
    }

    /** The other side of the amount boundary: 60 g would have been 232, and he typed 240. */
    @Test
    fun `changing the amount and a figure together makes it his`() {
        val after = scanned.withAmount(60.0).copy(kcal = 240)

        assertThat(after.correctionOf(scanned).source).isEqualTo(Source.TYPED)
    }

    /** Opening the editor and saving is not a claim about anything. */
    @Test
    fun `saving a label row with nothing changed keeps the label`() {
        assertThat(scanned.correctionOf(scanned).source).isEqualTo(Source.LABEL)
    }

    /**
     * The comparison is on the parsed whole numbers, so re-typing the same figures cannot flip the
     * source. There is no string left to compare by the time a row exists: the editor refuses a
     * decimal outright rather than rounding it, which `EntryFormStateTest` pins.
     */
    @Test
    fun `re-typing the same numbers keeps the label`() {
        val after = scanned.copy(kcal = 116, proteinG = 2, carbsG = 24, fatG = 0)

        assertThat(after.correctionOf(scanned).source).isEqualTo(Source.LABEL)
    }

    /**
     * A row with no amount cannot be scaled at all, so the figures are compared as they stand. Both
     * halves matter: nothing is lost by having no amount, and nothing is excused by it either.
     */
    @Test
    fun `a label row with no amount keeps the label when nothing but the name moves`() {
        val noAmount = scanned.copy(portion = null, portionAmount = 0.0, portionUnit = "")

        assertThat(noAmount.copy(name = "Puffed rice").correctionOf(noAmount).source)
            .isEqualTo(Source.LABEL)
        assertThat(noAmount.copy(kcal = noAmount.kcal + 10).correctionOf(noAmount).source)
            .isEqualTo(Source.TYPED)
    }

    /**
     * The row issue #23 left behind: a unit was named but no number, so there is nothing to scale
     * from. Filling the number in is the editor recording how much it was, not a claim about the
     * figures — they were stated for that much all along — so the packet keeps them.
     *
     * Two independent rules have to agree for that to come out right: the editor leaves the figures
     * alone on such a row, and the rescale here declines to divide by an amount of nothing. This
     * pins the agreement, so that changing either one alone is caught.
     */
    @Test
    fun `a label row with a unit but no amount keeps the label when it gains one`() {
        val unitOnly = scanned.copy(portion = null, portionAmount = 0.0, portionUnit = "g")

        val gainsAnAmount = unitOnly.copy(portionAmount = 30.0, portion = "30 g")

        assertThat(gainsAnAmount.correctionOf(unitOnly).source).isEqualTo(Source.LABEL)
    }

    /** And nothing is excused by it: a figure changed in the same save is still his. */
    @Test
    fun `a label row gaining an amount and a figure together makes it his`() {
        val unitOnly = scanned.copy(portion = null, portionAmount = 0.0, portionUnit = "g")

        val after = unitOnly.copy(portionAmount = 30.0, portion = "30 g", kcal = 140)

        assertThat(after.correctionOf(unitOnly).source).isEqualTo(Source.TYPED)
    }

    // --- 10-11: every other source is left exactly as it was (D4) ---------------------------------

    /**
     * **D4 regression — passes before this change and must keep passing.** Correcting a guess does
     * not turn it into a measurement, and the confidence it was made with is part of the record.
     */
    @Test
    fun `correcting an estimate leaves it an estimate`() {
        val estimate = anItem(source = Source.AI_ESTIMATE, confidence = Confidence.MEDIUM)

        val corrected = estimate.copy(kcal = 140).correctionOf(estimate)

        assertThat(corrected.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(corrected.confidence).isEqualTo(Confidence.MEDIUM)
    }

    /**
     * Exhaustive over the enum, so a source added later cannot be swept into this rule by accident:
     * only a label row is demoted, because only a label row is claiming something it stopped being
     * able to claim.
     */
    @Test
    fun `no other source is touched`() {
        val others = Source.entries.filter { it != Source.LABEL }
            .map { anItem(source = it, confidence = confidenceFor(it)) } +
            // A repeat carries whatever the row it was copied from carried, so REPEATED is the one
            // source that exists both with and without a confidence.
            anItem(source = Source.REPEATED, confidence = Confidence.LOW)

        others.forEach { before ->
            val corrected = before.copy(kcal = before.kcal + 25).correctionOf(before)
            assertThat(corrected.source).isEqualTo(before.source)
            assertThat(corrected.confidence).isEqualTo(before.confidence)
        }
    }

    // --- 12 and the both-ends guard ---------------------------------------------------------------

    /**
     * The flipped row is a legal row: a typed number carries no confidence, and a label carries none
     * either, so the invariant `FoodItem` enforces holds across the flip with nothing to invent and
     * nothing to lose.
     */
    @Test
    fun `a corrected row carries no confidence`() {
        val corrected = scanned.copy(kcal = 140).correctionOf(scanned)

        assertThat(corrected.confidence).isNull()
        // Constructing it again from its own fields is the invariant check: it would throw if a
        // typed row ever arrived carrying one.
        assertThat(corrected.copy()).isEqualTo(corrected)
    }

    /**
     * Both ends must be a label before anything is demoted.
     *
     * The editor carries a row's source through untouched, so a saved row whose source is NOT the
     * one it replaces did not come from the editor — something else built it and said what it is.
     * Overwriting that with TYPED would be this rule inventing provenance, which is the exact thing
     * D4 exists to stop. Checking `before` alone would do it.
     */
    @Test
    fun `a label row saved as something else keeps what it was saved as`() {
        val relabelled = scanned.copy(
            kcal = 140,
            source = Source.AI_ESTIMATE,
            confidence = Confidence.MEDIUM,
        )

        val corrected = relabelled.correctionOf(scanned)

        assertThat(corrected.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(corrected.confidence).isEqualTo(Confidence.MEDIUM)
    }

    private fun confidenceFor(source: Source): Confidence? =
        if (source == Source.AI_ESTIMATE) Confidence.MEDIUM else null
}
