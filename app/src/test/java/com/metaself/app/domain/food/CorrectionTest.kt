package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * What Save does to each of a food's three groups (D54 §5): leave it alone, empty it, or replace it.
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`.
 *
 * The owner's rule: **a group whose figures did not change is not touched** — whatever provenance
 * the incoming group claims — so an untouched label stays a label and an accepted estimate can
 * replace a better group only by changing it. Figures are compared by D45's nine significant
 * figures; the unit name exactly. Every figure here is invented; the Oat biscuit is D54's own
 * invented example.
 */
class CorrectionTest {

    private val label = Provenance(Source.LABEL, null, setAtMillis = 700)
    private val typed = Provenance(Source.TYPED, null, setAtMillis = 2_000)
    private val estimate = Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, setAtMillis = 2_000)

    private fun per100g(kcal: Double = 480.0, provenance: Provenance = label) =
        PerHundredGrams(Nutrients(kcal, 7.0, 62.0, 22.0), provenance)

    private fun perBiscuit(
        fat: Double = 1.0,
        unitName: String = "biscuit",
        provenance: Provenance = typed,
    ) = PerUnit(unitName, Nutrients(90.0, 1.0, 12.0, fat), provenance)

    private fun weighs(grams: Double = 18.0, provenance: Provenance = typed) =
        GramsPerUnit(grams, provenance)

    private val oatBiscuit = FoodFacts(per100g(), perBiscuit(), weighs())

    @Test
    fun `an unchanged food keeps every group`() {
        val plan = Correction.plan(oatBiscuit, oatBiscuit)

        assertThat(plan).isEqualTo(
            Correction.Plan(Correction.Keep, Correction.Keep, Correction.Keep),
        )
    }

    /** The owner's rule: equal figures are not rewritten, whatever the incoming group says it is. */
    @Test
    fun `equal figures are kept whatever provenance they arrive with`() {
        val arriving = FoodFacts(
            per100g = per100g(provenance = estimate),
            perUnit = perBiscuit(provenance = estimate),
            gramsPerUnit = weighs(provenance = typed),
        )

        val plan = Correction.plan(oatBiscuit, arriving)

        assertThat(plan).isEqualTo(
            Correction.Plan(Correction.Keep, Correction.Keep, Correction.Keep),
        )
    }

    /** D45's comparison: two routes to one number differ in a double's last bit, not in meaning. */
    @Test
    fun `figures equal to nine significant figures are kept`() {
        val nearly = 480.0 * (1 + 1e-12)
        check(nearly != 480.0)
        val arriving = oatBiscuit.copy(per100g = per100g(kcal = nearly, provenance = typed))

        assertThat(Correction.plan(oatBiscuit, arriving).per100g).isEqualTo(Correction.Keep)
    }

    /** D54's invented example: per biscuit changed fat 1 → 4 and was accepted. */
    @Test
    fun `a group with a different figure is replaced, and only that group`() {
        val accepted = perBiscuit(fat = 4.0, provenance = estimate)

        val plan = Correction.plan(oatBiscuit, oatBiscuit.copy(perUnit = accepted))

        assertThat(plan).isEqualTo(
            Correction.Plan(Correction.Keep, Correction.Replace(accepted), Correction.Keep),
        )
    }

    @Test
    fun `a different weight is replaced`() {
        val heavier = weighs(grams = 20.0)

        val plan = Correction.plan(oatBiscuit, oatBiscuit.copy(gramsPerUnit = heavier))

        assertThat(plan.gramsPerUnit).isEqualTo(Correction.Replace(heavier))
    }

    /** Compared exactly, so the stored name moves: there is no statement that renames a unit alone. */
    @Test
    fun `a unit name differing only in case is replaced`() {
        val recased = perBiscuit(unitName = "Biscuit")

        val plan = Correction.plan(oatBiscuit, oatBiscuit.copy(perUnit = recased))

        assertThat(plan.perUnit).isEqualTo(Correction.Replace(recased))
    }

    @Test
    fun `an emptied group is cleared`() {
        val plan = Correction.plan(oatBiscuit, FoodFacts(per100g = per100g()))

        assertThat(plan).isEqualTo(
            Correction.Plan(Correction.Keep, Correction.Clear, Correction.Clear),
        )
    }

    @Test
    fun `a group the food did not hold is written`() {
        val stored = FoodFacts(per100g = per100g())
        val filled = perBiscuit(provenance = estimate)

        val plan = Correction.plan(stored, stored.copy(perUnit = filled))

        assertThat(plan).isEqualTo(
            Correction.Plan(Correction.Keep, Correction.Replace(filled), Correction.Clear),
        )
    }

    /**
     * A group the food reads as not holding is cleared even when nothing arrives for it: the read
     * drops a stored group it cannot believe (`toDomain`), and the Save before D54 swept such a
     * group away. Keeping it would leave something unreadable standing that nobody can see.
     */
    @Test
    fun `a group neither side holds is cleared`() {
        val stored = FoodFacts(per100g = per100g())

        assertThat(Correction.plan(stored, stored).perUnit).isEqualTo(Correction.Clear)
        assertThat(Correction.plan(stored, stored).gramsPerUnit).isEqualTo(Correction.Clear)
    }

    /** A food that does not read back as a food holds nothing to keep. */
    @Test
    fun `with nothing stored every arriving group is written and every other cleared`() {
        val arriving = FoodFacts(per100g = per100g(), gramsPerUnit = weighs())

        val plan = Correction.plan(null, arriving)

        assertThat(plan).isEqualTo(
            Correction.Plan(
                Correction.Replace(arriving.per100g!!),
                Correction.Clear,
                Correction.Replace(arriving.gramsPerUnit!!),
            ),
        )
    }

    // --- D54 §12.7: the one relabel of an unchanged figure ----------------------------------------

    /**
     * A weight echoed under a unit the review renamed, accepted, now describes the model's unit: it
     * arrives as an estimate over the same figure held higher, and is written. Only downward, only
     * the weight, and only for an arriving estimate — the form sends one only when he accepted it.
     */
    @Test
    fun `an accepted estimate weight over the same figure held higher is written`() {
        val overTyped = Correction.plan(oatBiscuit, oatBiscuit.copy(gramsPerUnit = weighs(provenance = estimate)))
        val overLabel = Correction.plan(
            oatBiscuit.copy(gramsPerUnit = weighs(provenance = label)),
            oatBiscuit.copy(gramsPerUnit = weighs(provenance = estimate)),
        )

        assertThat(overTyped.gramsPerUnit).isEqualTo(Correction.Replace(weighs(provenance = estimate)))
        assertThat(overLabel.gramsPerUnit).isEqualTo(Correction.Replace(weighs(provenance = estimate)))
        assertThat(overTyped.per100g).isEqualTo(Correction.Keep)
        assertThat(overTyped.perUnit).isEqualTo(Correction.Keep)
    }

    @Test
    fun `the same weight is kept when it arrives typed, or as an estimate over one held no higher`() {
        val repeated = Provenance(Source.REPEATED, null, setAtMillis = 700)
        val lowerHeld = oatBiscuit.copy(gramsPerUnit = weighs(provenance = repeated))
        val estimateHeld = oatBiscuit.copy(gramsPerUnit = weighs(provenance = estimate))

        assertThat(Correction.plan(oatBiscuit, oatBiscuit).gramsPerUnit).isEqualTo(Correction.Keep)
        assertThat(Correction.plan(lowerHeld, oatBiscuit.copy(gramsPerUnit = weighs(provenance = estimate))).gramsPerUnit)
            .isEqualTo(Correction.Keep)
        assertThat(Correction.plan(estimateHeld, estimateHeld).gramsPerUnit).isEqualTo(Correction.Keep)
        // A label arriving typed is never relabelled by this: only an arriving estimate is.
        assertThat(Correction.plan(oatBiscuit.copy(gramsPerUnit = weighs(provenance = label)), oatBiscuit).gramsPerUnit)
            .isEqualTo(Correction.Keep)
    }

    /** Groups keep the owner's rule unchanged: equal figures, whatever provenance, are kept. */
    @Test
    fun `an estimate over an equal group is still kept`() {
        val plan = Correction.plan(oatBiscuit, oatBiscuit.copy(per100g = per100g(provenance = estimate)))

        assertThat(plan.per100g).isEqualTo(Correction.Keep)
    }
}
