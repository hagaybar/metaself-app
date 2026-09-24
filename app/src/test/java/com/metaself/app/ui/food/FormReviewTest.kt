package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.Nutrients
import org.junit.jupiter.api.Test

/**
 * What typing, accepting and dismissing do to a review in a food editor (D54 §4). Every figure is
 * invented.
 */
class FormReviewTest {

    private val form = FoodForm(
        name = "Oat biscuit",
        kcalPer100g = "480", proteinPer100g = "7", carbsPer100g = "62", fatPer100g = "22",
        unitName = "biscuit",
        kcalPerUnit = "90", proteinPerUnit = "1", carbsPerUnit = "12", fatPerUnit = "1",
        gramsPerUnit = "18",
    )

    private val fatChange = Suggestion(
        nutrients = Nutrients(90.0, 1.0, 12.0, 4.0),
        confidence = Confidence.MEDIUM,
        filled = false,
        changes = listOf(FigureChange(Figure.FAT, 1.0, 4.0, "A reason.")),
        reason = null,
    )

    /** D54 §9.4: never silent — an answer that arrived is said, even with nothing of it left. */
    @Test
    fun `an answer whose every suggestion he has typed over while it was out is still said`() {
        val reviewing = FormReview().asked().typed(form, form.copy(unitName = "cookie"))

        val answered = reviewing.answered(FoodReview(null, fatChange, null, emptyList()))

        assertThat(answered.review).isEqualTo(Review.Shown(FoodReview(null, null, null, emptyList())))
    }

    /**
     * D54 §9.4: a failure is said under the button it answers, in its own words, until a new
     * request or Dismiss — or, with no failure given, as a review that threw.
     */
    @Test
    fun `a failure is held to be said under the button, until dismissed or asked again`() {
        val failed = FormReview().asked().failed(EstimateResult.CeilingReached)

        assertThat(failed.review).isEqualTo(Review.Failed(EstimateResult.CeilingReached))
        assertThat(failed.asking).isFalse()
        assertThat(failed.typed(form, form.copy(fatPer100g = "21"))).isEqualTo(failed)
        assertThat(failed.dismissed().review).isNull()
        assertThat(failed.asked().review).isEqualTo(Review.Asking())
        assertThat(FormReview().asked().failed(null).review).isEqualTo(Review.Failed(null))
    }

    /** As [FormReview.accept] and typing treat a note: it keeps the answer up until dismissed. */
    @Test
    fun `an answer whose suggestions were all typed over while it was out still shows its note`() {
        val reviewing = FormReview().asked().typed(form, form.copy(fatPerUnit = "2"))

        val answered = reviewing.answered(FoodReview(null, fatChange, "A note.", emptyList()))

        assertThat(answered.review).isEqualTo(Review.Shown(FoodReview(null, null, "A note.", emptyList())))
    }

    @Test
    fun `an answer whose every suggestion was set aside is shown as unusable, with what went`() {
        val answer = FoodReview(null, null, null, listOf(FactGroup.PER_100G))

        val reviewing = FormReview().asked().unusable(answer)

        assertThat(reviewing.review).isEqualTo(Review.Shown(answer, unusable = true))
        assertThat(reviewing.asking).isFalse()
    }

    /**
     * D54 §8.4: the model's answer is offered when there is something to explain — an answer that
     * could not be read or used, that proposed nothing, or that set a group aside — and not when
     * the suggestions are on screen to speak for themselves. A new request or Dismiss takes it down.
     */
    @Test
    fun `the model's answer is kept to show only when the review did not simply work`() {
        val raw = """{"per_100g":null}"""
        val asked = FormReview().asked()

        assertThat(asked.failed(EstimateResult.Unreadable("x"), raw).modelAnswer).isEqualTo(raw)
        assertThat(asked.unusable(FoodReview(null, null, null, listOf(FactGroup.PER_100G)), raw).modelAnswer)
            .isEqualTo(raw)
        assertThat(asked.answered(FoodReview(null, null, null, emptyList()), raw).modelAnswer).isEqualTo(raw)
        assertThat(asked.answered(FoodReview(null, fatChange, null, listOf(FactGroup.PER_100G)), raw).modelAnswer)
            .isEqualTo(raw)
        assertThat(asked.answered(FoodReview(null, fatChange, null, emptyList()), raw).modelAnswer).isNull()

        val offered = asked.failed(EstimateResult.Unreadable("x"), raw)
        assertThat(offered.asked().modelAnswer).isNull()
        assertThat(offered.dismissed().modelAnswer).isNull()
    }

    @Test
    fun `an answer that changed nothing is shown as that until dismissed`() {
        val answered = FormReview().asked().answered(FoodReview(null, null, null, emptyList()))

        assertThat(answered.review).isEqualTo(Review.Shown(FoodReview(null, null, null, emptyList()), true))
        assertThat(answered.dismissed().review).isNull()
    }

    @Test
    fun `accepting the last suggestion takes the review down, and the note keeps it up`() {
        val bare = FormReview().asked().answered(FoodReview(null, fatChange, null, emptyList()))
        val noted = FormReview().asked().answered(FoodReview(null, fatChange, "A note.", emptyList()))

        assertThat(bare.accept(FactGroup.PER_UNIT, form)!!.second.review).isNull()
        assertThat(noted.accept(FactGroup.PER_UNIT, form)!!.second.review).isNotNull()
    }

    /** Save labels the group by its weakest member (D54 §5 as amended 2026-09-24), so it has to know. */
    @Test
    fun `accepting records the review's confidence and where the figures it kept came from`() {
        val shown = FormReview().asked()
            .answered(FoodReview(null, fatChange.copy(keptFrom = Source.REPEATED), null, emptyList()))

        val accepted = shown.accept(FactGroup.PER_UNIT, form)!!.second

        assertThat(accepted.accepted)
            .containsExactly(FactGroup.PER_UNIT, AcceptedGroup(Confidence.MEDIUM, keptFrom = Source.REPEATED))
    }

    /** A review of one food is no answer for another: changing what it is withdraws both groups. */
    @Test
    fun `changing the name or brand withdraws both groups, shown or still out`() {
        val both = FoodReview(fatChange, fatChange, null, emptyList())

        val renamedWhileOut = FormReview().asked()
            .typed(form, form.copy(name = "Rye biscuit"))
            .answered(both)
        val rebrandedWhileShown = FormReview().asked().answered(both)
            .typed(form, form.copy(brand = "A brand"))

        assertThat(renamedWhileOut.review).isEqualTo(Review.Shown(FoodReview(null, null, null, emptyList())))
        assertThat(rebrandedWhileShown.review).isNull()
    }

    @Test
    fun `typing the weight withdraws nothing`() {
        val shown = FormReview().asked().answered(FoodReview(null, fatChange, null, emptyList()))

        assertThat(shown.typed(form, form.copy(gramsPerUnit = "20"))).isEqualTo(shown)
    }

    @Test
    fun `a set-aside line goes when he types in its group`() {
        val shown = FormReview().asked()
            .answered(FoodReview(null, fatChange, null, listOf(FactGroup.PER_100G)))

        val typed = shown.typed(form, form.copy(fatPer100g = "21"))

        assertThat((typed.review as Review.Shown).review.setAside).isEmpty()
    }
}
