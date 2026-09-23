package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
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
     * and is counted as one. What it contains is still said — the size in the note (D5).
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
}
