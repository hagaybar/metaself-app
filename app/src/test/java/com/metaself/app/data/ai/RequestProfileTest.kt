package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * What a request sends besides the question (D57 §1, §2): the first guess by the model's name, and
 * the one builder every request goes through. Pure, so JUnit 5.
 */
class RequestProfileTest {

    private val older = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4-turbo", "gpt-3.5-turbo")
    private val reasoning = listOf("gpt-5", "gpt-5-mini", "gpt-6-luna", "GPT-6-astra", "o1", "o3-mini", "o4-mini", " gpt-5.1 ")
    private val unknown = listOf("a-model", "", "claude-something", "mistral-large")
    private val review = ReviewRequest(ReviewProcess.NEW_FOOD, "Oat biscuit", "", null, "", null, null)

    @Test
    fun `older models are first sent temperature 0 and no reasoning effort`() {
        older.forEach {
            assertWithMessage(it).that(RequestProfile.guess(it)).isEqualTo(RequestProfile(true, null))
        }
    }

    @Test
    fun `reasoning models are first sent no temperature and a low reasoning effort`() {
        reasoning.forEach {
            assertWithMessage(it).that(RequestProfile.guess(it)).isEqualTo(RequestProfile(false, "low"))
        }
    }

    @Test
    fun `a name the guess does not know keeps what the app always sent`() {
        unknown.forEach {
            assertWithMessage(it).that(RequestProfile.guess(it)).isEqualTo(RequestProfile(true, null))
        }
    }

    @Test
    fun `every first guess pins the reply with the strict schema`() {
        (older + reasoning + unknown).forEach {
            assertWithMessage(it).that(RequestProfile.guess(it).strictFormat).isTrue()
        }
    }

    @Test
    fun `the estimate request carries exactly the parameters of its profile`() {
        older.forEach { model ->
            val body = parsed(EstimatePrompt.requestBody(model = model, description = "one apple"))
            assertWithMessage(model).that(body["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
            assertWithMessage(model).that(body.keys).doesNotContain("reasoning_effort")
        }
        reasoning.forEach { model ->
            val body = parsed(EstimatePrompt.requestBody(model = model, description = "one apple"))
            assertWithMessage(model).that(body.keys).doesNotContain("temperature")
            assertWithMessage(model).that(body["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("low")
        }
        val learned = RequestProfile(temperature = false, reasoningEffort = "medium")
        val body = parsed(EstimatePrompt.requestBody("gpt-4o", "one apple", profile = learned))
        assertThat(body.keys).doesNotContain("temperature")
        assertThat(body["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("medium")
    }

    @Test
    fun `the review request carries exactly the parameters of its profile`() {
        older.forEach { model ->
            val body = parsed(ReviewPrompt.requestBody(model, review))
            assertWithMessage(model).that(body["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
            assertWithMessage(model).that(body.keys).doesNotContain("reasoning_effort")
        }
        reasoning.forEach { model ->
            val body = parsed(ReviewPrompt.requestBody(model, review))
            assertWithMessage(model).that(body.keys).doesNotContain("temperature")
            assertWithMessage(model).that(body["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("low")
        }
        val learned = RequestProfile(temperature = true, reasoningEffort = "none")
        val body = parsed(ReviewPrompt.requestBody("gpt-6-luna", review, learned))
        assertThat(body["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
        assertThat(body["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("none")
    }

    /** Structured outputs are unchanged for every first guess, and no token limit is sent to any. */
    @Test
    fun `every first guess keeps its strict schema and sends no token limit`() {
        (older + reasoning + unknown).forEach { model ->
            listOf(
                parsed(EstimatePrompt.requestBody(model = model, description = "one apple")),
                parsed(ReviewPrompt.requestBody(model, review)),
            ).forEach { body ->
                val format = body["response_format"]!!.jsonObject
                assertWithMessage(model).that(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
                assertWithMessage(model)
                    .that(format["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content).isEqualTo("true")
                assertWithMessage(model).that(body.keys).containsNoneOf("max_tokens", "max_completion_tokens")
            }
        }
    }

    /** D57 §3: the same schema, moved into the instructions; the words asked about unchanged. */
    @Test
    fun `without the strict format the schema is written into the first instructions`() {
        val loose = RequestProfile.REASONING.copy(strictFormat = false)
        listOf(
            parsed(EstimatePrompt.requestBody("gpt-6-luna", "one apple")) to
                parsed(EstimatePrompt.requestBody("gpt-6-luna", "one apple", profile = loose)),
            parsed(ReviewPrompt.requestBody("gpt-6-luna", review)) to
                parsed(ReviewPrompt.requestBody("gpt-6-luna", review, loose)),
        ).forEach { (strict, sent) ->
            val schema = strict["response_format"]!!.jsonObject["json_schema"]!!.jsonObject["schema"]!!
            assertThat(sent["response_format"]).isEqualTo(Json.parseToJsonElement("""{"type":"json_object"}"""))

            val strictMessages = strict["messages"]!!.jsonArray.map { it.jsonObject }
            val messages = sent["messages"]!!.jsonArray.map { it.jsonObject }
            assertThat(messages.size).isEqualTo(strictMessages.size)
            val instructions = messages[0]["content"]!!.jsonPrimitive.content
            assertThat(instructions).startsWith(strictMessages[0]["content"]!!.jsonPrimitive.content)
            assertThat(instructions).contains("JSON")
            assertThat(instructions).endsWith(schema.toString())
            assertThat(messages.drop(1)).isEqualTo(strictMessages.drop(1))
        }
    }

    @Test
    fun `a profile is remembered and read back whole`() {
        listOf(
            RequestProfile.DETERMINISTIC,
            RequestProfile.REASONING,
            RequestProfile(temperature = false, reasoningEffort = null, strictFormat = false),
        ).forEach { assertThat(RequestProfile.fromJson(it.toJson())).isEqualTo(it) }
    }

    @Test
    fun `a remembered profile that cannot be read is none`() {
        listOf(
            """{}""",
            """{"temperature":"yes","format":"strict"}""",
            """{"temperature":true,"format":"loose"}""",
            """{"temperature":true,"reasoning_effort":3,"format":"strict"}""",
        ).forEach {
            assertWithMessage(it).that(RequestProfile.fromJson(Json.parseToJsonElement(it).jsonObject)).isNull()
        }
    }

    @Test
    fun `a profile is described in plain words`() {
        assertThat(RequestProfile.REASONING.describe()).isEqualTo("no temperature, low thinking, strict format")
        assertThat(RequestProfile.DETERMINISTIC.describe()).isEqualTo("temperature 0, strict format")
        assertThat(RequestProfile(true, "none", strictFormat = false).describe())
            .isEqualTo("temperature 0, thinking off, format in the instructions")
    }

    private fun parsed(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject
}
