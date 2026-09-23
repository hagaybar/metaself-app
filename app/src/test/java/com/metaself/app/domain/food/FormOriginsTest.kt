package com.metaself.app.domain.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import org.junit.jupiter.api.Test

/**
 * Where each group on the food form came from, as a review is told it (D54 §2).
 *
 * Pure, so JUnit 5 — `org.junit.jupiter.api.Test`, never `org.junit.Test`.
 *
 * The rule is the one Save uses (§5), so what the model is told and what is then stored cannot
 * disagree: boxes equal to the stored group → the stored source; a group accepted from a review this
 * session → an estimate with that review's confidence; anything else typed → `TYPED`; a group empty,
 * half filled or refused → not known. Every figure is invented; the Oat biscuit is D54's own example.
 */
class FormOriginsTest {

    private val label = Provenance(Source.LABEL, null, 700)
    private val typed = Provenance(Source.TYPED, null, 700)

    private val stored = FoodFacts(
        per100g = PerHundredGrams(Nutrients(480.0, 7.0, 62.0, 22.0), label),
        perUnit = PerUnit("biscuit", Nutrients(90.0, 1.0, 12.0, 1.0), typed),
        gramsPerUnit = GramsPerUnit(18.0, typed),
    )

    private val untouched = FoodForm.of(Food(name = "Oat biscuit", facts = stored))

    private fun origin(source: Source, confidence: Confidence? = null) =
        FormOrigins.Origin(source, confidence)

    @Test
    fun `an untouched food is told as stored`() {
        val origins = FormOrigins.of(stored, untouched, accepted = emptyMap())

        assertThat(origins).isEqualTo(
            FormOrigins.Origins(
                per100g = origin(Source.LABEL),
                perUnit = origin(Source.TYPED),
                gramsPerUnit = origin(Source.TYPED),
            ),
        )
    }

    @Test
    fun `an earlier estimate is told with its confidence`() {
        val guessed = stored.copy(
            per100g = stored.per100g!!.copy(
                provenance = Provenance(Source.AI_ESTIMATE, Confidence.LOW, 700),
            ),
        )

        val origins = FormOrigins.of(guessed, untouched, accepted = emptyMap())

        assertThat(origins.per100g).isEqualTo(origin(Source.AI_ESTIMATE, Confidence.LOW))
    }

    @Test
    fun `a group accepted from a review is an estimate with that review's confidence`() {
        val accepted = untouched.copy(fatPerUnit = "4")

        val origins = FormOrigins.of(
            stored,
            accepted,
            accepted = mapOf(FactGroup.PER_UNIT to Confidence.MEDIUM),
        )

        assertThat(origins.perUnit).isEqualTo(origin(Source.AI_ESTIMATE, Confidence.MEDIUM))
        assertThat(origins.per100g).isEqualTo(origin(Source.LABEL))
    }

    /** Save would issue no statement for it, so it stays what it was — and is told so. */
    @Test
    fun `an accepted group whose boxes equal the stored ones is told as stored`() {
        val origins = FormOrigins.of(
            stored,
            untouched,
            accepted = mapOf(FactGroup.PER_100G to Confidence.HIGH),
        )

        assertThat(origins.per100g).isEqualTo(origin(Source.LABEL))
    }

    @Test
    fun `a group typed over is typed`() {
        val origins = FormOrigins.of(stored, untouched.copy(kcalPer100g = "470"), emptyMap())

        assertThat(origins.per100g).isEqualTo(origin(Source.TYPED))
    }

    /** Compared exactly, as Save compares it: the stored name would move, so it is his. */
    @Test
    fun `a unit renamed only in case is typed`() {
        val labelled = stored.copy(perUnit = stored.perUnit!!.copy(provenance = label))
        val origins = FormOrigins.of(labelled, untouched.copy(unitName = "Biscuit"), emptyMap())

        assertThat(origins.perUnit).isEqualTo(origin(Source.TYPED))
    }

    /** Save stores the unit as its display name, so spacing the form tidies away is no change. */
    @Test
    fun `spacing the form tidies away is not a change`() {
        val labelled = stored.copy(perUnit = stored.perUnit!!.copy(provenance = label))
        val origins = FormOrigins.of(labelled, untouched.copy(unitName = " biscuit  "), emptyMap())

        assertThat(origins.perUnit).isEqualTo(origin(Source.LABEL))
    }

    @Test
    fun `a half-typed or refused group is not known`() {
        val half = untouched.copy(proteinPer100g = "")
        val refused = untouched.copy(kcalPerUnit = "5001")
        val nameless = untouched.copy(unitName = "")

        assertThat(FormOrigins.of(stored, half, emptyMap()).per100g).isNull()
        assertThat(FormOrigins.of(stored, refused, emptyMap()).perUnit).isNull()
        assertThat(FormOrigins.of(stored, nameless, emptyMap()).perUnit).isNull()
    }

    @Test
    fun `an empty group is not known, even with a unit named`() {
        val soup = FoodForm(name = "Lentil soup", unitName = "bowl")

        assertThat(FormOrigins.of(null, soup, emptyMap())).isEqualTo(
            FormOrigins.Origins(per100g = null, perUnit = null, gramsPerUnit = null),
        )
    }

    /** The prompt sends this as `UNKNOWN`; here it stays what the food holds. */
    @Test
    fun `a source this version cannot read is told as unrecognised`() {
        val odd = stored.copy(
            per100g = stored.per100g!!.copy(provenance = Provenance(Source.UNRECOGNISED, null, 700)),
        )

        assertThat(FormOrigins.of(odd, untouched, emptyMap()).per100g)
            .isEqualTo(origin(Source.UNRECOGNISED))
    }

    @Test
    fun `a new food is typed where typed and an estimate where accepted`() {
        val soup = FoodForm(name = "Lentil soup", unitName = "bowl")
            .with(FactGroup.PER_100G, Nutrients(60.0, 4.0, 9.0, 1.0))
            .with(FactGroup.PER_UNIT, Nutrients(180.0, 12.0, 27.0, 3.0))
            .copy(gramsPerUnit = "300")

        val origins = FormOrigins.of(null, soup, mapOf(FactGroup.PER_UNIT to Confidence.LOW))

        assertThat(origins).isEqualTo(
            FormOrigins.Origins(
                per100g = origin(Source.TYPED),
                perUnit = origin(Source.AI_ESTIMATE, Confidence.LOW),
                gramsPerUnit = origin(Source.TYPED),
            ),
        )
    }

    @Test
    fun `a weight typed over is typed, and a weight is never an estimate`() {
        val packet = stored.copy(gramsPerUnit = GramsPerUnit(18.0, label))
        val heavier = untouched.copy(gramsPerUnit = "20")

        assertThat(FormOrigins.of(packet, untouched, emptyMap()).gramsPerUnit)
            .isEqualTo(origin(Source.LABEL))
        assertThat(FormOrigins.of(packet, heavier, emptyMap()).gramsPerUnit)
            .isEqualTo(origin(Source.TYPED))
        assertThat(FormOrigins.of(packet, untouched.copy(gramsPerUnit = ""), emptyMap()).gramsPerUnit)
            .isNull()
    }
}
