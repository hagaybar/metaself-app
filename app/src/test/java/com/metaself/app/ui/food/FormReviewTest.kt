package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.NameSuggestion
import com.metaself.app.domain.ai.ReviewItem
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.ai.UnitSuggestion
import com.metaself.app.domain.ai.WeightSuggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.Correction
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.food.WeightAccepted
import org.junit.jupiter.api.Test

/**
 * A review's suggestions in the boxes (D54 §12.6): pending, put back, typed over, cancelled, and
 * what Accept changes and save takes (§12.7). Pure, so JUnit 5. Every food and figure is invented;
 * the Humus is the spec's own invented example.
 */
class FormReviewTest {

    /** The spec's oat biscuit (D54 §2), as the form holds it. */
    private val biscuit = FoodForm(
        name = "Oat biscuit",
        kcalPer100g = "480", proteinPer100g = "7", carbsPer100g = "62", fatPer100g = "22",
        unitName = "biscuit",
        kcalPerUnit = "90", proteinPerUnit = "1", carbsPerUnit = "12", fatPerUnit = "1",
        gramsPerUnit = "18",
    )

    /** The spec's §12.4 example: a label with an impossible fat, no unit, no weight. */
    private val humus = FoodForm(
        name = "Humus",
        kcalPer100g = "166", proteinPer100g = "7.9", carbsPer100g = "14.3", fatPer100g = "19.6",
    )

    private val fatChange = Suggestion(
        nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
        confidence = Confidence.MEDIUM,
        filled = false,
        changes = listOf(FigureChange(Figure.FAT, 1.0, 4.0, "A reason.")),
        reason = null,
        heldSource = Source.TYPED,
    )

    /** §12.4's reply to the Humus: a name, one change to the label, a unit with its figures, a weight. */
    private val humusAnswer = FoodReview(
        per100g = Suggestion(
            nutrients = Nutrients(166.0, 7.9, 14.3, 9.6),
            confidence = Confidence.MEDIUM,
            filled = false,
            changes = listOf(FigureChange(Figure.FAT, 19.6, 9.6, "The macros are too many.")),
            reason = null,
            heldSource = Source.LABEL,
        ),
        perUnit = Suggestion(
            nutrients = Nutrients(24.9, 1.2, 2.1, 1.4),
            confidence = Confidence.MEDIUM,
            filled = true,
            changes = emptyList(),
            reason = "One tablespoon of it.",
            heldSource = null,
        ),
        note = "A note.",
        setAside = emptyList(),
        name = NameSuggestion("Hummus", "The usual spelling."),
        unit = UnitSuggestion("tablespoon", "A dip is counted by the spoon.", Confidence.MEDIUM, null),
        weight = WeightSuggestion(15.0, Confidence.MEDIUM, "A level tablespoon.", null),
        weightInBundle = true,
    )

    private fun arrive(form: FoodForm, answer: FoodReview): Pair<FoodForm, FormReview> =
        FormReview().asked(form).answered(answer, raw = null, form = form)

    // --- In the boxes ------------------------------------------------------------------------------

    @Test
    fun `every suggestion goes straight into its box, pending, with what the box held`() {
        val (form, reviewing) = arrive(humus, humusAnswer)

        assertThat(form.name).isEqualTo("Hummus")
        assertThat(form.fatPer100g).isEqualTo("9.6")
        assertThat(form.unitName).isEqualTo("tablespoon")
        assertThat(listOf(form.kcalPerUnit, form.proteinPerUnit, form.carbsPerUnit, form.fatPerUnit))
            .containsExactly("24.9", "1.2", "2.1", "1.4").inOrder()
        assertThat(form.gramsPerUnit).isEqualTo("15")
        assertThat(reviewing.pending).hasSize(8)
        assertThat(reviewing.pending[FormBox.NAME]).isEqualTo(PendingBox("Humus", "Hummus", "The usual spelling."))
        assertThat(reviewing.pending[FormBox.FAT_100G]!!.original).isEqualTo("19.6")
        assertThat(reviewing.pending[FormBox.KCAL_100G]).isNull()
        assertThat(reviewing.hasPending).isTrue()
    }

    @Test
    fun `a stored figure shown rounded goes back as exactly that text, so Save keeps the stored figure`() {
        val stored = FoodFacts(
            per100g = PerHundredGrams(Nutrients(480.0, 8.571428571428571, 62.0, 22.0), Provenance(Source.LABEL, null, 1)),
        )
        val opened = FoodForm(name = "Oat biscuit", kcalPer100g = "480", proteinPer100g = "8.57", carbsPer100g = "62", fatPer100g = "22")
        val change = Suggestion(
            Nutrients(480.0, 9.0, 62.0, 22.0), Confidence.LOW, false,
            listOf(FigureChange(Figure.PROTEIN, 8.571428571428571, 9.0, "Why.")), null, heldSource = Source.LABEL,
        )
        val (written, reviewing) = arrive(opened, FoodReview(change, null, null, emptyList()))

        val (back, left) = reviewing.putBack(written, FormBox.PROTEIN_100G)!!

        assertThat(back.proteinPer100g).isEqualTo("8.57")
        assertThat(left.hasPending).isFalse()
        val facts = back.toFacts(5, stored = stored)!!
        assertThat(Correction.plan(stored, facts).per100g).isEqualTo(Correction.Keep)
    }

    @Test
    fun `a reason shared by a group is said once, under its first box`() {
        val (_, reviewing) = arrive(humus, humusAnswer)

        assertThat(reviewing.view(FormBox.KCAL_UNIT)!!.reason).isEqualTo("One tablespoon of it.")
        assertThat(reviewing.view(FormBox.PROTEIN_UNIT)!!.reason).isNull()
        assertThat(reviewing.view(FormBox.KCAL_100G)).isNull()
    }

    // --- Back ----------------------------------------------------------------------------------

    @Test
    fun `Back puts exactly what the box held back, and Clear an empty box`() {
        val (form, reviewing) = arrive(humus, humusAnswer.copy(unit = null, perUnit = null, weight = null, weightInBundle = false))

        val (named, afterName) = reviewing.putBack(form, FormBox.NAME)!!
        assertThat(named.name).isEqualTo("Humus")
        assertThat(afterName.pending.keys).containsExactly(FormBox.FAT_100G)
        assertThat(reviewing.view(FormBox.NAME)!!.back).isEqualTo("Humus")
    }

    @Test
    fun `the unit goes back with its whole bundle, and its figures have no Back of their own`() {
        val (form, reviewing) = arrive(humus, humusAnswer)

        assertThat(reviewing.view(FormBox.UNIT)!!.back).isEqualTo("")
        assertThat(reviewing.view(FormBox.KCAL_UNIT)!!.back).isNull()
        assertThat(reviewing.view(FormBox.WEIGHT)!!.back).isNull()
        assertThat(reviewing.putBack(form, FormBox.KCAL_UNIT)).isNull()

        val (back, left) = reviewing.putBack(form, FormBox.UNIT)!!
        assertThat(back.unitName).isEmpty()
        assertThat(listOf(back.kcalPerUnit, back.proteinPerUnit, back.carbsPerUnit, back.fatPerUnit, back.gramsPerUnit))
            .containsExactly("", "", "", "", "")
        assertThat(left.pending.keys).containsExactly(FormBox.NAME, FormBox.FAT_100G)
    }

    @Test
    fun `a bundle box typed over stays his when the unit goes back`() {
        val (form, reviewing) = arrive(humus, humusAnswer)
        val (typed, afterTyping) = reviewing.typed(form, form.copy(kcalPerUnit = "30"))

        val (back, _) = afterTyping.putBack(typed, FormBox.UNIT)!!

        assertThat(back.kcalPerUnit).isEqualTo("30")
        assertThat(back.proteinPerUnit).isEmpty()
    }

    @Test
    fun `a weight suggested for a unit goes back when the unit goes back to empty`() {
        val alone = humusAnswer.copy(weightInBundle = false)
        val (form, reviewing) = arrive(humus, alone)

        val (back, left) = reviewing.putBack(form, FormBox.UNIT)!!

        assertThat(back.gramsPerUnit).isEmpty()
        assertThat(FormBox.WEIGHT in left.pending).isFalse()
    }

    // --- Typing ----------------------------------------------------------------------------------

    @Test
    fun `typing into a pending box makes it his, and nothing else moves`() {
        val (form, reviewing) = arrive(humus, humusAnswer)

        val (typed, left) = reviewing.typed(form, form.copy(fatPer100g = "10"))

        assertThat(typed.fatPer100g).isEqualTo("10")
        assertThat(FormBox.FAT_100G in left.pending).isFalse()
        assertThat(left.pending).hasSize(7)
    }

    @Test
    fun `typing the name or the brand while suggestions wait moves nothing else`() {
        val (form, reviewing) = arrive(humus, humusAnswer.copy(name = null))

        val (_, left) = reviewing.typed(form, form.copy(name = "Chickpea dip", brand = "Examplebrand"))

        assertThat(left.pending).hasSize(7)
    }

    /** §12.6: a new unit takes down every per-one figure and weight still pending, back as they were. */
    @Test
    fun `typing a new unit takes down what was stated for another, whether or not the unit was pending`() {
        val (form, reviewing) = arrive(humus, humusAnswer)
        val (typed, left) = reviewing.typed(form, form.copy(unitName = "spoon"))

        assertThat(typed.unitName).isEqualTo("spoon")
        assertThat(listOf(typed.kcalPerUnit, typed.fatPerUnit, typed.gramsPerUnit)).containsExactly("", "", "")
        assertThat(left.pending.keys).containsExactly(FormBox.NAME, FormBox.FAT_100G)

        val change = FoodReview(null, fatChange, null, emptyList(), weight = WeightSuggestion(21.0, Confidence.LOW, "Why.", 18.0))
        val (onBiscuit, pending) = arrive(biscuit, change)
        val (retyped, rest) = pending.typed(onBiscuit, onBiscuit.copy(unitName = "cookie"))
        assertThat(retyped.fatPerUnit).isEqualTo("1")
        assertThat(retyped.gramsPerUnit).isEqualTo("18")
        assertThat(rest.hasPending).isFalse()
    }

    @Test
    fun `while the request is out, typing withdraws what it answers`() {
        val asked = FormReview().asked(humus)
        fun left(after: FoodForm): FoodReview {
            val withdrawing = asked.typed(humus, after).second
            return (withdrawing.answered(humusAnswer, null, after).second.review as Review.Shown).review
        }

        val per100g = left(humus.copy(kcalPer100g = "170"))
        assertThat(per100g.per100g).isNull()
        assertThat(per100g.perUnit).isNotNull()

        val unit = left(humus.copy(unitName = "spoon"))
        assertThat(unit.unit).isNull()
        assertThat(unit.perUnit).isNull()
        assertThat(unit.weight).isNull()
        assertThat(unit.per100g).isNotNull()

        val weight = left(humus.copy(gramsPerUnit = "14"))
        assertThat(weight.weight).isNull()
        assertThat(weight.unit).isNotNull()

        val renamed = left(humus.copy(name = "Chickpea dip"))
        assertThat(renamed.suggestsAnything).isFalse()
    }

    /** D54 §9.4: never silent — an answer that arrived is said, even with nothing of it left. */
    @Test
    fun `an answer whose every suggestion was withdrawn while it was out is still said`() {
        val withdrawing = FormReview().asked(biscuit).typed(biscuit, biscuit.copy(unitName = "cookie")).second

        val (_, answered) = withdrawing.answered(FoodReview(null, fatChange, "A note.", emptyList()), null, biscuit)

        assertThat(answered.review).isEqualTo(Review.Shown(FoodReview(null, null, "A note.", emptyList())))
        assertThat(answered.hasPending).isFalse()
    }

    // --- Cancel ----------------------------------------------------------------------------------

    @Test
    fun `Cancel puts back the page as it was when Review was pressed, earlier typing included`() {
        val typedFirst = biscuit.copy(kcalPer100g = "470")
        val (form, reviewing) = arrive(typedFirst, FoodReview(null, fatChange, null, emptyList()))
        val (typedAfter, stillPending) = reviewing.typed(form, form.copy(name = "Oat cookie"))

        val (cancelled, gone) = stillPending.cancel()!!

        assertThat(typedAfter.name).isEqualTo("Oat cookie")
        assertThat(cancelled).isEqualTo(typedFirst)
        assertThat(gone).isEqualTo(FormReview())
    }

    @Test
    fun `there is nothing to cancel once nothing waits`() {
        assertThat(FormReview().cancel()).isNull()
        val (form, reviewing) = arrive(biscuit, FoodReview(null, fatChange, null, emptyList()))
        assertThat(reviewing.putBack(form, FormBox.FAT_UNIT)!!.second.cancel()).isNull()
    }

    // --- What Accept changes and save takes (§12.7) ------------------------------------------------

    @Test
    fun `accepting takes each group with a box still pending, labelled by its weakest member`() {
        val (form, reviewing) = arrive(humus, humusAnswer)

        val accepted = reviewing.accepted(form)

        // Three label figures kept beside the one change: the label's source, which ranks above an
        // estimate, so an estimate.
        assertThat(accepted.groups[FactGroup.PER_100G]).isEqualTo(AcceptedGroup(Confidence.MEDIUM, Source.LABEL))
        assertThat(accepted.groups[FactGroup.PER_UNIT]).isEqualTo(AcceptedGroup(Confidence.MEDIUM, null))
        assertThat(accepted.weight).isEqualTo(WeightAccepted(Confidence.MEDIUM))
    }

    @Test
    fun `a group whose every suggestion was put back or typed over is not accepted`() {
        val (form, reviewing) = arrive(humus, humusAnswer)
        val (back, afterBack) = reviewing.putBack(form, FormBox.FAT_100G)!!
        val (typed, afterTyping) = afterBack.typed(back, back.copy(gramsPerUnit = "14"))

        val accepted = afterTyping.accepted(typed)

        assertThat(accepted.groups.keys).containsExactly(FactGroup.PER_UNIT)
        assertThat(accepted.weight).isNull()
    }

    @Test
    fun `a figure put back keeps the group's source as sent, for the weakest member`() {
        val repeated = fatChange.copy(
            changes = listOf(
                FigureChange(Figure.KCAL, 90.0, 95.0, "Why."),
                FigureChange(Figure.FAT, 1.0, 4.0, "Why."),
            ),
            heldSource = Source.REPEATED,
        )
        val allFour = repeated.copy(
            changes = Figure.values().map { FigureChange(it, 1.0, 2.0, "Why.") },
        )
        val (form, reviewing) = arrive(biscuit, FoodReview(null, allFour, null, emptyList()))
        assertThat(reviewing.accepted(form).groups[FactGroup.PER_UNIT]!!.keptFrom).isNull()

        val (back, left) = reviewing.putBack(form, FormBox.KCAL_UNIT)!!
        assertThat(left.accepted(back).groups[FactGroup.PER_UNIT]).isEqualTo(AcceptedGroup(Confidence.MEDIUM, Source.REPEATED))
    }

    @Test
    fun `a weight kept under a unit he accepts is part of the bundle`() {
        val respelled = FoodReview(
            null, null, null, emptyList(),
            unit = UnitSuggestion("cracker", "The usual word.", Confidence.HIGH, Source.TYPED),
        )
        val (form, reviewing) = arrive(biscuit, respelled)

        val accepted = reviewing.accepted(form)

        assertThat(accepted.groups[FactGroup.PER_UNIT]).isEqualTo(AcceptedGroup(Confidence.HIGH, Source.TYPED))
        assertThat(accepted.weight).isEqualTo(WeightAccepted(Confidence.HIGH, echoed = true))

        val (typedWeight, afterTyping) = reviewing.typed(form, form.copy(gramsPerUnit = "20"))
        assertThat(afterTyping.accepted(typedWeight).weight).isNull()
    }

    /** §12.4: a weight held per ml must not become one per glass; the bundle clears it, visibly. */
    @Test
    fun `renaming away from ml with no weight proposed clears a held weight, in the bundle`() {
        val drink = FoodForm(
            name = "Oat drink", unitName = "ml",
            kcalPerUnit = "57", proteinPerUnit = "2.9", carbsPerUnit = "4.7", fatPerUnit = "3.6",
            gramsPerUnit = "1.03",
        )
        val glass = FoodReview(
            per100g = null,
            perUnit = Suggestion(Nutrients(140.0, 7.0, 12.0, 6.0), Confidence.MEDIUM, true, emptyList(), "A glass.", heldSource = null),
            note = null, setAside = emptyList(),
            unit = UnitSuggestion("glass", "A glass.", Confidence.MEDIUM, null),
        )

        val (form, reviewing) = arrive(drink, glass)

        assertThat(form.gramsPerUnit).isEmpty()
        assertThat(reviewing.pending[FormBox.WEIGHT])
            .isEqualTo(PendingBox("1.03", "", "A weight per millilitre is not a weight per glass.", bundled = true))
        assertThat(reviewing.accepted(form).weight).isNull()
        assertThat(reviewing.putBack(form, FormBox.UNIT)!!.first.gramsPerUnit).isEqualTo("1.03")
    }

    @Test
    fun `an accepted weight is saved as an estimate, and nothing works one out`() {
        val (form, reviewing) = arrive(humus, humusAnswer)
        val accepted = reviewing.accepted(form)

        val facts = form.toFacts(5, estimated = accepted.groups, weight = accepted.weight)!!

        assertThat(facts.gramsPerUnit).isEqualTo(GramsPerUnit(15.0, Provenance(Source.AI_ESTIMATE, Confidence.MEDIUM, 5)))
        assertThat(facts.per100g!!.provenance.source).isEqualTo(Source.AI_ESTIMATE)
        assertThat(facts.perUnit!!.unitName).isEqualTo("tablespoon")
    }

    // --- What else is said -------------------------------------------------------------------------

    @Test
    fun `a failure is held to be said under the button, until dismissed or asked again`() {
        val failed = FormReview().asked(biscuit).failed(EstimateResult.CeilingReached)

        assertThat(failed.review).isEqualTo(Review.Failed(EstimateResult.CeilingReached))
        assertThat(failed.asking).isFalse()
        assertThat(failed.typed(biscuit, biscuit.copy(fatPer100g = "21")).second).isEqualTo(failed)
        assertThat(failed.dismissed().review).isNull()
    }

    @Test
    fun `an answer whose every suggestion was set aside is shown as unusable, with what went`() {
        val answer = FoodReview(null, null, "A note.", listOf(ReviewItem.PER_100G))

        val shown = FormReview().asked(biscuit).unusable(answer, "raw")

        assertThat(shown.review).isEqualTo(Review.Shown(answer, unusable = true))
        assertThat(shown.modelAnswer).isEqualTo("raw")
        assertThat(shown.hasPending).isFalse()
    }

    @Test
    fun `the model's answer is kept to show only when the review did not simply work`() {
        val asked = FormReview().asked(biscuit)

        assertThat(asked.answered(FoodReview(null, fatChange, null, emptyList()), "raw", biscuit).second.modelAnswer).isNull()
        assertThat(asked.answered(FoodReview(null, null, null, emptyList()), "raw", biscuit).second.modelAnswer).isEqualTo("raw")
        assertThat(asked.answered(FoodReview(null, fatChange, null, listOf(ReviewItem.NAME)), "raw", biscuit).second.modelAnswer)
            .isEqualTo("raw")
    }

    @Test
    fun `an answer that changed nothing is shown as that until dismissed`() {
        val (_, nothing) = FormReview().asked(biscuit).answered(FoodReview(null, null, "Fine.", emptyList()), null, biscuit)

        assertThat((nothing.review as Review.Shown).nothingSuggested).isTrue()
        assertThat(nothing.dismissed()).isEqualTo(FormReview())
    }

    @Test
    fun `Dismiss leaves suggestions that still wait`() {
        val (_, reviewing) = arrive(biscuit, FoodReview(null, fatChange, null, emptyList()))

        assertThat(reviewing.dismissed()).isEqualTo(reviewing)
    }
}
