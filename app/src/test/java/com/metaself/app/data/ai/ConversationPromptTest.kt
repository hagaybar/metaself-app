package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.Asked
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * What a conversation about a meal sends (D58 §3, §4). Every meal and answer here is invented.
 */
class ConversationPromptTest {

    private val description = "pasta with a mushroom sauce from a takeaway counter"
    private val twoAnswered = listOf(
        Asked("How big was the container?", "Standard takeaway box"),
        Asked("What was the sauce like?", "Not sure"),
    )

    @Test
    fun `the first request sends the instructions and the words, nothing more`() {
        val messages = messages(ConversationPrompt.opening("a-model", description, RequestProfile.DETERMINISTIC))

        assertThat(messages.map { it.first }).containsExactly("system", "user").inOrder()
        assertThat(messages[1].second).isEqualTo(description)
    }

    @Test
    fun `the first request carries the everyday estimate rules for a meal that needs no question`() {
        val system = messages(ConversationPrompt.opening("a-model", description))[0].second

        assertThat(system).contains(EstimatePrompt.INSTRUCTIONS)
        assertThat(system).contains("at most 5 questions")
    }

    @Test
    fun `a step sends every question with its answer, in order, then the app's own count`() {
        val messages = messages(ConversationPrompt.step("a-model", description, twoAnswered, cap = 3))

        assertThat(messages.map { it.first })
            .containsExactly("system", "user", "assistant", "user", "assistant", "user", "system")
            .inOrder()
        assertThat(messages[2].second).isEqualTo("How big was the container?")
        assertThat(messages[3].second).isEqualTo("Standard takeaway box")
        assertThat(messages[5].second).isEqualTo("Not sure")
        assertThat(messages[6].second).isEqualTo("Questions asked so far: 2. You may ask at most 1 more.")
    }

    @Test
    fun `the question rules ask about the biggest uncertainty first and never what was said`() {
        val system = messages(ConversationPrompt.step("a-model", description, twoAnswered, cap = 3))[0].second

        assertThat(system).contains("Added fats")
        assertThat(system).contains("Portion size")
        assertThat(system).contains("Never ask what the description or an earlier answer already says")
        assertThat(system).contains("\"Not sure\"")
        assertThat(system).doesNotContain(EstimatePrompt.INSTRUCTIONS)
    }

    @Test
    fun `the final analysis sends the pairs, and his added sentence as his own words`() {
        val messages = messages(
            ConversationPrompt.final("a-model", description, twoAnswered, moreDetail = "no cheese after all"),
        )

        assertThat(messages.map { it.first })
            .containsExactly("system", "user", "assistant", "user", "assistant", "user", "user")
            .inOrder()
        assertThat(messages.last().second).isEqualTo("More detail: no cheese after all")
    }

    @Test
    fun `the final analysis asked again names the items without an amount, as the app`() {
        val messages = messages(
            ConversationPrompt.final("a-model", description, emptyList(), missingAmounts = listOf("Pasta")),
        )

        assertThat(messages.last().first).isEqualTo("system")
        assertThat(messages.last().second).contains("Pasta")
    }

    /** D58 §3.2 as amended: grams for what was scooped or plated, a piece for what is counted. */
    @Test
    fun `the final instructions reconstruct the plate and allow grams worked out`() {
        val system = messages(ConversationPrompt.final("a-model", description, emptyList()))[0].second

        assertThat(system).contains("Reconstruct the plate")
        assertThat(system).contains("hidden calories")
        assertThat(system).contains("in grams")
        assertThat(system).contains("For a counted piece")
        assertThat(system).contains("No grams in the detail")
        assertThat(system).contains("A drink is one item")
    }

    @Test
    fun `the step schema is strict and every property is required`() {
        val schema = schema(ConversationPrompt.step("a-model", description, twoAnswered, cap = 3))

        assertThat(schema["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(required(schema)).containsExactly("needs_questions", "total_planned", "question")
        // Never nullable: a model refusing that schema would refuse every description (§4.1).
        val question = schema["properties"]!!.jsonObject["question"]!!.jsonObject
        assertThat(question.keys).doesNotContain("anyOf")
        assertThat(required(question)).containsExactly("text", "options")
    }

    @Test
    fun `the first request's schema is the step's with the estimate's items and note`() {
        val schema = schema(ConversationPrompt.opening("a-model", description))

        assertThat(required(schema))
            .containsExactly("needs_questions", "total_planned", "question", "items", "note")
        assertThat(schema["properties"]!!.jsonObject["items"]!!.jsonObject["items"])
            .isEqualTo(EstimatePrompt.ITEM_SCHEMA)
    }

    @Test
    fun `the final schema writes the plate first, and its items are the estimate's exactly`() {
        val schema = schema(ConversationPrompt.final("a-model", description, emptyList()))

        assertThat(schema["properties"]!!.jsonObject.keys.first()).isEqualTo("plate")
        assertThat(required(schema)).containsExactly("plate", "items", "note")
        assertThat(schema["properties"]!!.jsonObject["items"]!!.jsonObject["items"])
            .isEqualTo(EstimatePrompt.ITEM_SCHEMA)
    }

    @Test
    fun `without a strict format the schema goes into the first instructions`() {
        val body = ConversationPrompt.step(
            "a-model", description, twoAnswered, cap = 3,
            profile = RequestProfile(temperature = true, reasoningEffort = null, strictFormat = false),
        )

        assertThat(messages(body)[0].second).contains("needs_questions")
        assertThat(Json.parseToJsonElement(body).jsonObject["response_format"]!!.jsonObject["type"]!!
            .jsonPrimitive.content).isEqualTo("json_object")
    }

    /** D16 as amended by D58 §10: the words, the questions and the answers, and nothing about him. */
    @Test
    fun `NOTHING about the person using the app is sent, at any step`() {
        val bodies = listOf(
            ConversationPrompt.opening("a-model", description),
            ConversationPrompt.step("a-model", description, twoAnswered, cap = 3),
            ConversationPrompt.final("a-model", description, twoAnswered, moreDetail = "more sauce"),
        ).map { it.lowercase() }

        // If this fails because the PROMPT's own wording used one of these words, rephrase the
        // prompt. Do not weaken the assertion.
        bodies.forEach { body ->
            listOf(
                "weight", "kg", "target", "profile", "age", "height", "male", "female",
                "trend", "history", "goal", "deficit", "bmi", "kilograms",
            ).forEach { forbidden ->
                val asAWord = Regex("\\b" + Regex.escape(forbidden) + "\\b")
                assertThat(asAWord.containsMatchIn(body)).isFalse()
            }
        }
    }

    private fun messages(body: String): List<Pair<String, String>> =
        (Json.parseToJsonElement(body).jsonObject["messages"] as JsonArray).map {
            it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content
        }

    private fun schema(body: String): JsonObject =
        Json.parseToJsonElement(body).jsonObject["response_format"]!!.jsonObject["json_schema"]!!
            .jsonObject["schema"]!!.jsonObject

    private fun required(schema: JsonObject): List<String> =
        schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
}
