package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.HeldWeight
import com.metaself.app.domain.ai.NameSuggestion
import com.metaself.app.domain.ai.ReviewItem
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.ai.UnitSuggestion
import com.metaself.app.domain.ai.WeightSuggestion
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Nutrients
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/**
 * D54 §12.4: a review may propose the name, a unit and what one weighs, beside the two groups, and
 * each is read as its own item, used whole or set aside whole. Every food, unit and figure here is
 * invented; the first is the spec's own invented example.
 */
class ReviewSuggestsAnythingTest {

    // --- The spec's invented example (§12.4) ------------------------------------------------------

    @Test
    fun `the spec's example reads as a name, one change, a unit with its figures, and a weight`() {
        val review = proposed(
            reply(
                name = text("Hummus", "The usual spelling."),
                per100g = group(166, 7.9, 14.3, 9.6, fatReason = "At 19.6 g of fat the macros come to 265 kcal."),
                unitName = text("tablespoon", "A dip is usually counted by the spoon."),
                perUnit = group(24.9, 1.2, 2.1, 1.4, kcalReason = "One tablespoon of it."),
                weight = weight(15, "A level tablespoon holds about 15 g."),
            ),
            HUMUS,
        )

        assertThat(review.name).isEqualTo(NameSuggestion("Hummus", "The usual spelling."))
        assertThat(review.per100g!!.changes.map { it.to }).containsExactly(9.6)
        assertThat(review.per100g!!.heldSource).isEqualTo(Source.LABEL)
        assertThat(review.unit).isEqualTo(UnitSuggestion("tablespoon", "A dip is usually counted by the spoon."))
        assertThat(review.perUnit!!.filled).isTrue()
        assertThat(review.perUnit!!.heldSource).isNull()
        assertThat(review.perUnit!!.nutrients).isEqualTo(Nutrients(24.9, 1.2, 2.1, 1.4))
        assertThat(review.weight)
            .isEqualTo(WeightSuggestion(15.0, Confidence.MEDIUM, "A level tablespoon holds about 15 g.", null))
        assertThat(review.weightInBundle).isTrue()
        assertThat(review.setAside).isEmpty()
    }

    // --- The name ---------------------------------------------------------------------------------

    @Test
    fun `a name echoed back is kept, however it is spaced`() {
        val review = proposed(reply(name = text(" Humus ", "")), HUMUS)

        assertThat(review.name).isNull()
        assertThat(review.setAside).isEmpty()
    }

    @Test
    fun `a new name with no reason, or one the app cannot hold, is set aside`() {
        assertThat(unusable(reply(name = text("Hummus", " ")), HUMUS).setAside)
            .containsExactly(ReviewItem.NAME)
        assertThat(unusable(reply(name = text("!!!", "Tidier.")), HUMUS).setAside)
            .containsExactly(ReviewItem.NAME)
    }

    // --- The unit and per one ---------------------------------------------------------------------

    @Test
    fun `a unit echoed back is the unit kept, and its figures are judged against what is held`() {
        val review = proposed(
            reply(unitName = text("biscuit", ""), perUnit = group(90, 1, 12, 4, fatReason = "A reason.")),
            BISCUIT,
        )

        assertThat(review.unit).isNull()
        assertThat(review.perUnit!!.changes.single().from).isEqualTo(1.0)
    }

    @Test
    fun `a food counted in ml keeps its unit whichever way the millilitre is written back`() {
        listOf("ml", "millilitres", "100 ml").forEach { spelling ->
            val review = proposed(reply(unitName = text(spelling, "")), DRINK)
            assertThat(review.unit).isNull()
            assertThat(review.setAside).isEmpty()
        }
    }

    @Test
    fun `a unit named without its figures is set aside`() {
        val review = unusable(reply(unitName = text("tablespoon", "A spoon.")), HUMUS)

        assertThat(review.setAside).containsExactly(ReviewItem.PER_UNIT)
    }

    @Test
    fun `a mass, an amount of a mass, or portion is never a unit`() {
        listOf("g", "100 g", "kg", "oz", "portion").forEach { unit ->
            val review = unusable(
                reply(unitName = text(unit, "A reason."), perUnit = group(166, 7.9, 14.3, 9.6, kcalReason = "Why.")),
                HUMUS,
            )
            assertThat(review.setAside).containsExactly(ReviewItem.PER_UNIT)
        }
    }

    @Test
    fun `a respelled unit whose figures are all kept is a unit alone, the figures exactly as held`() {
        val review = proposed(
            reply(unitName = text("cracker", "The usual word."), perUnit = group(90, 1, 12, 1)),
            BISCUIT.copy(gramsPerUnit = null),
        )

        assertThat(review.unit).isEqualTo(UnitSuggestion("cracker", "The usual word."))
        assertThat(review.perUnit).isNull()
    }

    @Test
    fun `a rename on the same side of the millilitre judges its figures against what is held`() {
        val review = proposed(
            reply(
                unitName = text("cracker", "The usual word."),
                perUnit = group(90, 1, 12, 4, fatReason = "A reason."),
                weight = weight(18, ""),
            ),
            BISCUIT,
        )

        assertThat(review.perUnit!!.filled).isFalse()
        assertThat(review.perUnit!!.changes.map { it.figure.name }).containsExactly("FAT")
        assertThat(review.perUnit!!.heldSource).isEqualTo(Source.TYPED)
        // The weight echoed: it stands for the new unit.
        assertThat(review.weight).isNull()
        assertThat(review.setAside).isEmpty()
    }

    @Test
    fun `a rename to the millilitre makes every figure new, judged per 100`() {
        val review = proposed(
            reply(unitName = text("ml", "A drink."), perUnit = group(57, 2.9, 4.7, 3.6, kcalReason = "Per 100 ml.")),
            BISCUIT.copy(gramsPerUnit = null),
        )

        assertThat(review.unit!!.unitName).isEqualTo("ml")
        assertThat(review.perUnit!!.filled).isTrue()

        val tooMuch = unusable(
            reply(unitName = text("ml", "A drink."), perUnit = group(1200, 2.9, 4.7, 3.6, kcalReason = "Why.")),
            BISCUIT.copy(gramsPerUnit = null),
        )
        assertThat(tooMuch.setAside).containsExactly(ReviewItem.PER_UNIT)
    }

    @Test
    fun `a rename away from the millilitre is judged by the per-one ceilings`() {
        val review = proposed(
            reply(unitName = text("glass", "A glass."), perUnit = group(1200, 50, 60, 40, kcalReason = "Why.")),
            DRINK,
        )

        assertThat(review.unit!!.unitName).isEqualTo("glass")
        assertThat(review.perUnit!!.nutrients.kcal).isEqualTo(1200.0)
    }

    @Test
    fun `a rename that leaves a held weight unanswered is set aside`() {
        val review = unusable(
            reply(unitName = text("cracker", "A reason."), perUnit = group(90, 1, 12, 4, fatReason = "Why.")),
            BISCUIT,
        )

        assertThat(review.setAside).containsExactly(ReviewItem.PER_UNIT)
    }

    @Test
    fun `a rename to the millilitre sets any weight aside and leaves the held one alone`() {
        val review = proposed(
            reply(
                unitName = text("ml", "A drink."),
                perUnit = group(57, 2.9, 4.7, 3.6, kcalReason = "Per 100 ml."),
                weight = weight(250, "A glass."),
            ),
            BISCUIT,
        )

        assertThat(review.unit!!.unitName).isEqualTo("ml")
        assertThat(review.weight).isNull()
        assertThat(review.setAside).containsExactly(ReviewItem.WEIGHT)
    }

    // --- What one weighs --------------------------------------------------------------------------

    @Test
    fun `a weight is ignored, never shown, where the editor has no weight box`() {
        val review = proposed(reply(weight = weight(25, "A reason.")), BISCUIT.copy(weightAsked = false))

        assertThat(review.weight).isNull()
        assertThat(review.setAside).isEmpty()
    }

    @Test
    fun `a weight echoed at the model's own precision is kept`() {
        val review = proposed(
            reply(weight = weight("18.04", "")),
            BISCUIT.copy(gramsPerUnit = HeldWeight(18.04, Source.TYPED)),
        )

        assertThat(review.weight).isNull()
    }

    @Test
    fun `a changed weight is rounded to one decimal and carries its reason and confidence`() {
        val review = proposed(reply(weight = weight("21.26", "A thicker biscuit.", "HIGH")), BISCUIT)

        assertThat(review.weight)
            .isEqualTo(WeightSuggestion(21.3, Confidence.HIGH, "A thicker biscuit.", 18.0))
        assertThat(review.weightInBundle).isFalse()
    }

    @Test
    fun `a weight of nothing, below nothing, past the ceiling, or with no reason is set aside`() {
        listOf(weight(0, "Why."), weight(-3, "Why."), weight("5000.1", "Why."), weight(25, "")).forEach {
            assertThat(unusable(reply(weight = it), BISCUIT).setAside).containsExactly(ReviewItem.WEIGHT)
        }
    }

    @Test
    fun `a weight with no unit anywhere, or for a food counted in ml, is set aside`() {
        assertThat(unusable(reply(weight = weight(25, "Why.")), HUMUS).setAside)
            .containsExactly(ReviewItem.WEIGHT)
        assertThat(unusable(reply(weight = weight(250, "Why.")), DRINK).setAside)
            .containsExactly(ReviewItem.WEIGHT)
    }

    // --- Usable, or not ---------------------------------------------------------------------------

    @Test
    fun `one usable item beside one set aside is an answer, not unusable`() {
        val result = ReviewResponse.parse(
            reply(name = text("Hummus", "Spelling."), weight = weight(25, "Why.")),
            HUMUS,
        )

        val review = (result as ReviewResult.Proposed).review
        assertThat(review.name).isNotNull()
        assertThat(review.setAside).containsExactly(ReviewItem.WEIGHT)
    }

    @Test
    fun `an answer in the shape before the name, unit and weight were added still reads`() {
        val content = """{"per_100g":null,"per_unit":null,"note":"Fine.","verdict":"consistent"}"""
        val result = ReviewResponse.parse(envelope(content), HUMUS)

        assertThat((result as ReviewResult.Proposed).review.suggestsAnything).isFalse()
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun proposed(body: String, request: ReviewRequest): FoodReview =
        (ReviewResponse.parse(body, request) as ReviewResult.Proposed).review

    private fun unusable(body: String, request: ReviewRequest): FoodReview =
        (ReviewResponse.parse(body, request) as ReviewResult.Unusable).review

    private fun text(value: String, reason: String) =
        """{"value":${JsonPrimitive(value)},"reason":${JsonPrimitive(reason)}}"""

    private fun weight(grams: Any, reason: String, confidence: String = "MEDIUM") =
        """{"grams":$grams,"reason":${JsonPrimitive(reason)},"confidence":"$confidence"}"""

    private fun group(
        kcal: Any, protein: Any, carbs: Any, fat: Any,
        kcalReason: String = "", fatReason: String = "",
    ): String =
        """{"kcal":$kcal,"protein_g":$protein,"carbs_g":$carbs,"fat_g":$fat,""" +
            """"kcal_reason":${JsonPrimitive(kcalReason)},"protein_reason":"",""" +
            """"carbs_reason":"","fat_reason":${JsonPrimitive(fatReason)},"confidence":"MEDIUM"}"""

    private fun reply(
        name: String? = null,
        per100g: String? = null,
        unitName: String? = null,
        perUnit: String? = null,
        weight: String? = null,
    ): String = envelope(
        """{"name":${name ?: "null"},"unit_name":${unitName ?: "null"},""" +
            """"per_100g":${per100g ?: "null"},"per_unit":${perUnit ?: "null"},""" +
            """"grams_per_unit":${weight ?: "null"},"note":"A note.","verdict":"problem_found"}""",
    )

    private fun envelope(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private companion object {
        // Invented, as the spec's §12.4 example: a label whose fat is impossible, no unit, no weight.
        val HUMUS = ReviewRequest(
            process = ReviewProcess.EXISTING_FOOD,
            name = "Humus",
            brand = "",
            per100g = HeldGroup(Nutrients(166.0, 7.9, 14.3, 19.6), Source.LABEL, null),
            unitName = "",
            perUnit = null,
            gramsPerUnit = null,
            weightAsked = true,
        )

        // Invented: the spec's oat biscuit (D54 §2), with a weight box.
        val BISCUIT = ReviewRequest(
            process = ReviewProcess.EXISTING_FOOD,
            name = "Oat biscuit",
            brand = "",
            per100g = HeldGroup(Nutrients(480.0, 7.0, 62.0, 22.0), Source.LABEL, null),
            unitName = "biscuit",
            perUnit = HeldGroup(Nutrients(90.0, 1.0, 12.0, 1.0), Source.TYPED, null),
            gramsPerUnit = HeldWeight(18.0, Source.TYPED),
            weightAsked = true,
        )

        // Invented: a carton counted in ml, sent per 100 ml (D56), with no weight.
        val DRINK = ReviewRequest(
            process = ReviewProcess.EXISTING_FOOD,
            name = "Oat drink",
            brand = "",
            per100g = null,
            unitName = "100 ml",
            perUnit = HeldGroup(Nutrients(57.0, 2.9, 4.7, 3.6), Source.LABEL, null),
            gramsPerUnit = null,
            weightAsked = true,
        )
    }
}
