package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

class EstimatePromptTest {

    @Test
    fun `the words that were typed are sent`() {
        val body = EstimatePrompt.requestBody(
            model = "a-model",
            description = "grilled chicken with rice and a side salad",
        )

        assertThat(body).contains("grilled chicken with rice and a side salad")
    }

    @Test
    fun `Hebrew survives being sent`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "ריזוטו עם מוצרלה")

        assertThat(body).contains("ריזוטו עם מוצרלה")
    }

    @Test
    fun `the extra sentence is sent alongside the original, not instead of it`() {
        val body = EstimatePrompt.requestBody(
            model = "a-model",
            description = "risotto with mozzarella",
            moreDetail = "small bowl, half the cheese",
        )

        assertThat(body).contains("risotto with mozzarella")
        assertThat(body).contains("small bowl, half the cheese")
    }

    @Test
    fun `it asks for components and forbids a single total`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "anything")

        assertThat(body).contains("separate item")
    }

    /**
     * Every item comes with a positive amount and a unit, and zero is no longer offered (D34).
     *
     * The prompt used to say "if you genuinely cannot put a number to it, use 0 and \"\"", which
     * the model took as permission, and one of the shapes it produced could not join a meal (#23).
     */
    @Test
    fun `it asks for a positive amount and a unit for every item, and never offers zero`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "anything")

        assertThat(body).doesNotContain("use 0")
        assertThat(body.lowercase()).contains("greater than zero")
        assertThat(body.lowercase()).contains("best estimate")
    }

    /**
     * Asked again, it is told what was missing — the request is otherwise identical, and at
     * temperature 0 the same question gets the same answer.
     */
    @Test
    fun `asked again, it is told which items came back without an amount`() {
        val body = EstimatePrompt.requestBody(
            model = "a-model",
            description = "stew with rice",
            missingAmounts = listOf("Stew"),
        )

        assertThat(body).contains("Stew")
        assertThat(body.lowercase()).contains("no amount")
        // An instruction from the app, never words put in the owner's mouth.
        assertThat(body).doesNotContain("More detail:")
    }

    /**
     * A drink is one item, counted in its usual serving (D35).
     *
     * For example, a cappuccino can come back as espresso and milk, in millilitres, and
     * keeping it would mean making a meal of the two and typing a weight for the milk. A drink is one thing
     * and is counted as one. What it contains is still said — the size in the item's detail (D5, as
     * D53 amended it).
     */
    @Test
    fun `a drink is one item, counted in servings, and milk in cereal is still an ingredient`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "cappuccino")

        assertThat(body).contains("A drink is one item")
        assertThat(body).contains("cappuccino")
        assertThat(body).contains("1 cup")
        assertThat(body.lowercase()).contains("do not split it into its ingredients")
        assertThat(body.lowercase()).contains("cereal")
    }

    @Test
    fun `it asks for one number per field, not a range`() {
        // Both models answered in ranges — "700-950 kcal" — which cannot be logged.
        val body = EstimatePrompt.requestBody(model = "a-model", description = "anything")

        assertThat(body.lowercase()).contains("single number")
    }

    @Test
    fun `it asks the model to answer in the language it was asked in`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "anything")

        assertThat(body.lowercase()).contains("same language")
    }

    // --- The reply's shape: worth and amount apart (D53 §2) --------------------------------------

    @Test
    fun `every item field is required`() {
        val item = itemSchema()

        val required = item["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        val properties = item["properties"]!!.jsonObject.keys
        assertThat(required).containsExactly(
            "name", "detail", "amount", "unit", "figures_per",
            "kcal", "protein_g", "carbs_g", "fat_g", "confidence",
        )
        assertThat(properties).containsExactlyElementsIn(required)
        assertThat(item["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
    }

    @Test
    fun `figures_per is 100 or 1`() {
        val figuresPer = itemSchema()["properties"]!!.jsonObject["figures_per"]!!.jsonObject

        assertThat(figuresPer["enum"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("100", "1")
    }

    /** A worth keeps decimals — 0.5 g of fat in 100 g is a figure, not a rounding error (D53 §1). */
    @Test
    fun `the worth is a number, not an integer`() {
        val properties = itemSchema()["properties"]!!.jsonObject

        listOf("kcal", "protein_g", "carbs_g", "fat_g", "amount").forEach { field ->
            assertThat(properties[field]!!.jsonObject["type"]!!.jsonPrimitive.content)
                .isEqualTo("number")
        }
    }

    /**
     * "A bun" is 1 bun at per-bun figures, never grams the model made up (D53 §2, amending D5). The
     * sentence is asserted as written, so rewording it is a decision and not an accident.
     */
    @Test
    fun `the instructions forbid inventing a weight for an unstated amount`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "a bun")

        assertThat(body).contains("Never convert an amount that was stated.")
        assertThat(body).contains("Never make up grams or millilitres for an amount that was not")
        assertThat(body).contains("never the total")
        assertThat(body).contains("in the singular")
    }

    /**
     * Grams and millilitres come back as "g" and "ml" in every language, so the phone never has to
     * guess at a spelling; a piece keeps the description's own word (issue #1). The sentence is
     * asserted as written, so rewording it is a decision and not an accident.
     */
    @Test
    fun `grams and millilitres are always written g and ml, and a piece keeps its own word`() {
        val body = EstimatePrompt.requestBody(model = "a-model", description = "סלט")

        val instructions = systemText(body)

        assertThat(instructions)
            .contains("When the unit is grams or millilitres, write it exactly \"g\" or \"ml\"")
        assertThat(instructions)
            .contains("never a translation, a plural or an abbreviation of them")
        assertThat(instructions).contains("the word for a piece")
    }

    /**
     * D16, from the other side: the only inputs the request can be built from are the model's name,
     * his words, his added sentence and the app's own retry list. The assignment below compiles only
     * while that is the signature, and the count shows there is no second way in.
     */
    @Test
    fun `the request is built from the words alone`() {
        val build: (String, String, String?, List<String>) -> String = EstimatePrompt::requestBody

        val ways = EstimatePrompt::class.java.declaredMethods
            .filter { it.name == "requestBody" }
        assertThat(ways).hasSize(1)
        assertThat(ways.single().parameterTypes.toList()).containsExactly(
            String::class.java, String::class.java, String::class.java, List::class.java,
        ).inOrder()
        assertThat(build("a-model", "soup", null, emptyList())).contains("soup")
    }

    @Test
    fun `NOTHING about the person using the app is sent`() {
        // Decision D16, as a test rather than a sentiment. It would be easy and tempting to send
        // the target "to help"; this is what stops somebody doing it later without noticing.
        //
        // If this fails because the PROMPT's own wording used one of these words, rephrase the
        // prompt. Do not weaken the assertion.
        val body = EstimatePrompt.requestBody(
            model = "a-model",
            description = "grilled chicken with rice",
        ).lowercase()

        // Whole words, not substrings: "age" lives inside "language", and the prompt legitimately
        // asks the model to reply in the same language. Matching letters rather than words would
        // fail on a sentence that leaks nothing, and a test that cries wolf gets relaxed.
        listOf(
            "weight", "kg", "target", "profile", "age", "height", "male", "female",
            "trend", "history", "goal", "deficit", "bmi", "kilograms",
        ).forEach { forbidden ->
            val asAWord = Regex("\\b" + Regex.escape(forbidden) + "\\b")
            assertThat(asAWord.containsMatchIn(body)).isFalse()
        }
    }

    /** The app's own instructions, as the first message carries them. */
    private fun systemText(body: String): String =
        Json.parseToJsonElement(body).jsonObject["messages"]!!.jsonArray.first()
            .jsonObject["content"]!!.jsonPrimitive.content

    private fun itemSchema(): JsonObject {
        val body = Json.parseToJsonElement(
            EstimatePrompt.requestBody(model = "a-model", description = "anything"),
        ).jsonObject
        val schema = body["response_format"]!!.jsonObject["json_schema"]!!.jsonObject["schema"]!!
        return schema.jsonObject["properties"]!!.jsonObject["items"]!!.jsonObject["items"]!!
            .jsonObject
    }
}
