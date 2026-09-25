package com.metaself.app.data.ai

import com.metaself.app.domain.ai.ReviewItem
import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.GramsPerUnit
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredGrams
import com.metaself.app.domain.food.PerUnit
import com.metaself.app.domain.food.Provenance
import com.metaself.app.ui.food.FormReview
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * D56: a food counted in millilitres is reviewed per 100 ml — asked, answered, shown and accepted at
 * the scale its boxes show — and saved per one ml, as it is stored. Were it asked per one ml, a
 * change rounded to one decimal (D54 §3) would turn 0.57 kcal into 0.6, and 0.029 g of protein
 * into nothing.
 *
 * Every figure is invented: a carton's 57 kcal · P 2.9 · C 4.7 · F 3.6 per 100 ml, off the label.
 */
class ReviewInMillilitresTest {

    private val drink = Food(
        name = "Oat drink",
        facts = FoodFacts(
            // Per 100 g too, so that with a weight the cross-check would be asked for. Invented.
            per100g = PerHundredGrams(Nutrients(55.0, 2.8, 4.6, 3.5), Provenance(Source.LABEL, null, 0)),
            perUnit = PerUnit("ml", Nutrients(0.57, 0.029, 0.047, 0.036), Provenance(Source.LABEL, null, 0)),
            // What one ml weighs, typed before D56 stopped asking. Invented.
            gramsPerUnit = GramsPerUnit(1.03, Provenance(Source.TYPED, null, 0)),
        ),
    )

    private fun request(form: FoodForm = FoodForm.of(drink)) =
        ReviewRequest.of(ReviewProcess.EXISTING_FOOD, form, drink.facts, weightBox = true)

    @Test
    fun `a food counted in ml is sent per 100 ml, with the carton's figures`() {
        val sent = userMessage(ReviewPrompt.requestBody("a-model", request()))

        assertThat(sent["unit_name"]!!.jsonPrimitive.content).isEqualTo("100 ml")
        val perUnit = sent["per_unit"]!!.jsonObject
        assertThat(
            listOf("kcal", "protein_g", "carbs_g", "fat_g").map { perUnit[it]!!.jsonPrimitive.content },
        ).containsExactly("57.0", "2.9", "4.7", "3.6").inOrder()
        assertThat(perUnit["source"]!!.jsonPrimitive.content).isEqualTo("LABEL")
    }

    /** A millilitre's weight is a density; the cross-check has no meaning for it, so it is not sent. */
    @Test
    fun `what one ml weighs is not sent, and there is no cross-check`() {
        val body = ReviewPrompt.requestBody("a-model", request())

        assertThat(userMessage(body)["grams_per_unit"]).isEqualTo(JsonNull)
        assertThat(systemMessage(body)).doesNotContain("Cross-check")
        assertThat(systemMessage(body)).contains("\"per 100 ml\"")
    }

    @Test
    fun `a food not counted in ml is sent as it always was`() {
        val glass = drink.copy(
            facts = FoodFacts(perUnit = PerUnit("glass", Nutrients(140.0, 7.0, 12.0, 6.0), Provenance(Source.LABEL, null, 0))),
        )
        val sent = userMessage(
            ReviewPrompt.requestBody(
                "a-model",
                ReviewRequest.of(ReviewProcess.EXISTING_FOOD, FoodForm.of(glass), glass.facts, weightBox = true),
            ),
        )

        assertThat(sent["unit_name"]!!.jsonPrimitive.content).isEqualTo("glass")
        assertThat(sent["per_unit"]!!.jsonObject["kcal"]!!.jsonPrimitive.content).isEqualTo("140.0")
    }

    /** Judged by the per-100 ceilings, as the boxes are: 1200 kcal in 100 ml is not a figure. */
    @Test
    fun `an answer per 100 ml is judged by the per 100 ceilings`() {
        val result = ReviewResponse.parse(
            reply(perUnit = group(1200, 2.9, 4.7, 3.6, kcalReason = "A reason.")),
            request(),
        )

        assertThat(result).isInstanceOf(ReviewResult.Unusable::class.java)
        assertThat((result as ReviewResult.Unusable).review.setAside).containsExactly(ReviewItem.PER_UNIT)
    }

    /**
     * The whole path: asked per 100 ml, a change to the calories answered per 100 ml and shown so,
     * written into the boxes as shown, and saved per ml — the figures it kept exactly as stored.
     */
    @Test
    fun `a suggestion per 100 ml is applied as shown and saved per ml, kept figures untouched`() {
        val form = FoodForm.of(drink)
        val answer = ReviewResponse.parse(
            reply(perUnit = group(60, 2.9, 4.7, 3.6, kcalReason = "A reason.")),
            request(form),
        ) as ReviewResult.Proposed

        val suggestion = answer.review.perUnit!!
        assertThat(suggestion.nutrients).isEqualTo(Nutrients(60.0, 2.9, 4.7, 3.6))
        assertThat(suggestion.changes.single().from).isEqualTo(57.0)

        val (applied, reviewing) = FormReview().asked(form).answered(answer.review, null, form)
        assertThat(applied.kcalPerUnit).isEqualTo("60")

        val saved = applied.toFacts(
            setAtMillis = 1,
            estimated = reviewing.accepted(applied).groups,
            stored = drink.facts,
        )!!.perUnit!!
        assertThat(saved.unitName).isEqualTo("ml")
        assertThat(saved.nutrients).isEqualTo(Nutrients(0.6, 0.029, 0.047, 0.036))
        assertThat(saved.provenance.source).isEqualTo(Source.AI_ESTIMATE)
    }

    // --- Helpers ---------------------------------------------------------------------------------

    private fun group(kcal: Any, protein: Any, carbs: Any, fat: Any, kcalReason: String = ""): String =
        """{"kcal":$kcal,"protein_g":$protein,"carbs_g":$carbs,"fat_g":$fat,""" +
            """"kcal_reason":${JsonPrimitive(kcalReason)},"protein_reason":"",""" +
            """"carbs_reason":"","fat_reason":"","confidence":"MEDIUM"}"""

    private fun reply(perUnit: String?): String {
        val content = """{"per_100g":null,"per_unit":${perUnit ?: "null"},"note":"A note.",""" +
            """"verdict":"problem_found"}"""
        return """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""
    }

    private fun userMessage(body: String): JsonObject {
        val messages = Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray
        return Json.parseToJsonElement(messages[1].jsonObject["content"]!!.jsonPrimitive.content).jsonObject
    }

    private fun systemMessage(body: String): String =
        Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray[0].jsonObject["content"]!!
            .jsonPrimitive.content
}
