package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * What a join carries from the absorbed food onto the one that stays (issue #19, D36 as amended).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`.
 *
 * Two rules. **The food that stays keeps every figure it holds** — the join question promises it
 * keeps its numbers — so only a group it lacks is filled, and filled with the absorbed food's
 * figure and provenance exactly. **What one weighs travels only to the same "one"**: the weight
 * names no unit of its own, so it is carried only when the food that stays will count in the unit
 * the weight was measured against.
 */
class JoinedFactsTest {

    private fun per100g(kcal: Double = 100.0, source: Source = Source.LABEL, setAt: Long = 1_000) =
        PerHundredGrams(Nutrients(kcal, 5.0, 10.0, 2.0), provenance(source, setAt))

    private fun perUnit(unitName: String = "bar", source: Source = Source.TYPED, setAt: Long = 1_000) =
        PerUnit(unitName, Nutrients(190.0, 15.0, 17.0, 6.0), provenance(source, setAt))

    private fun weighs(grams: Double = 45.0, setAt: Long = 1_000) =
        GramsPerUnit(grams, provenance(Source.TYPED, setAt))

    private fun provenance(source: Source, setAt: Long) = Provenance(
        source,
        if (source == Source.AI_ESTIMATE) Confidence.MEDIUM else null,
        setAt,
    )

    @Test
    fun `a per-100 g group only the absorbed food knew is carried with its provenance`() {
        val theirs = per100g(source = Source.AI_ESTIMATE, setAt = 700)

        val filled = JoinedFacts.fill(
            stays = FoodFacts(perUnit = perUnit()),
            absorbed = FoodFacts(per100g = theirs),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling(per100g = theirs))
    }

    @Test
    fun `a per-one group only the absorbed food knew is carried with its unit and provenance`() {
        val theirs = perUnit(unitName = "slice", source = Source.LABEL, setAt = 700)

        val filled = JoinedFacts.fill(
            stays = FoodFacts(per100g = per100g()),
            absorbed = FoodFacts(perUnit = theirs),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling(perUnit = theirs))
    }

    @Test
    fun `a weight comes along with the per-one group it was measured against`() {
        val unit = perUnit(unitName = "bar")
        val weight = weighs(45.0)

        val filled = JoinedFacts.fill(
            stays = FoodFacts(per100g = per100g()),
            absorbed = FoodFacts(perUnit = unit, gramsPerUnit = weight),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling(perUnit = unit, gramsPerUnit = weight))
    }

    /** "Bar" and "bar " are one unit — the same fold the replacement notice uses. */
    @Test
    fun `a weight is carried when both foods count in the same unit`() {
        val weight = weighs(45.0)

        val filled = JoinedFacts.fill(
            stays = FoodFacts(perUnit = perUnit(unitName = "Bar ")),
            absorbed = FoodFacts(perUnit = perUnit(unitName = "bar"), gramsPerUnit = weight),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling(gramsPerUnit = weight))
    }

    /** One bar's weight is not one slice's, and carrying it would invent a measurement. */
    @Test
    fun `a weight is left behind when the food that stays counts in a different unit`() {
        val filled = JoinedFacts.fill(
            stays = FoodFacts(perUnit = perUnit(unitName = "slice")),
            absorbed = FoodFacts(perUnit = perUnit(unitName = "bar"), gramsPerUnit = weighs()),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling())
    }

    /** Neither names a unit, so both count in the portion the app falls back to. */
    @Test
    fun `a weight is carried when neither food names a unit`() {
        val weight = weighs(45.0)

        val filled = JoinedFacts.fill(
            stays = FoodFacts(per100g = per100g()),
            absorbed = FoodFacts(per100g = per100g(), gramsPerUnit = weight),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling(gramsPerUnit = weight))
    }

    @Test
    fun `a weight is left behind when only the food that stays names a unit`() {
        val filled = JoinedFacts.fill(
            stays = FoodFacts(perUnit = perUnit(unitName = "slice")),
            absorbed = FoodFacts(per100g = per100g(), gramsPerUnit = weighs()),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling(per100g = per100g()))
    }

    /**
     * Every group the food that stays holds is kept, whichever way the ranking would have gone:
     * a label below it, an equal source beside it, and an estimate above it.
     */
    @Test
    fun `nothing the food that stays already holds is replaced, whatever its rank`() {
        val filled = JoinedFacts.fill(
            stays = FoodFacts(
                per100g = per100g(kcal = 60.0, source = Source.AI_ESTIMATE),
                perUnit = perUnit(unitName = "bar", source = Source.TYPED),
                gramsPerUnit = weighs(30.0),
            ),
            absorbed = FoodFacts(
                per100g = per100g(kcal = 422.0, source = Source.LABEL),
                perUnit = perUnit(unitName = "bar", source = Source.TYPED),
                gramsPerUnit = weighs(45.0),
            ),
        )

        assertThat(filled).isEqualTo(JoinedFacts.Filling())
    }

    /** A food that no longer reads back as a food knows nothing, so every blank is open. */
    @Test
    fun `a food that stays with nothing readable takes every group the absorbed one knows`() {
        val theirs = FoodFacts(per100g = per100g(), perUnit = perUnit(), gramsPerUnit = weighs())

        val filled = JoinedFacts.fill(stays = null, absorbed = theirs)

        assertThat(filled).isEqualTo(
            JoinedFacts.Filling(theirs.per100g, theirs.perUnit, theirs.gramsPerUnit),
        )
    }
}
