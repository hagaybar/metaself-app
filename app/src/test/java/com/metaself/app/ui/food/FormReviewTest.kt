package com.metaself.app.ui.food

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.day.Confidence
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

    @Test
    fun `an answer whose every suggestion he has typed over while it was out shows nothing`() {
        val reviewing = FormReview().asked().typed(form, form.copy(unitName = "cookie"))

        val answered = reviewing.answered(FoodReview(null, fatChange, null, emptyList()))

        assertThat(answered.review).isNull()
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
