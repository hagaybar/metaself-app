package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * What a request sends besides the question, by the model's name (a reasoning model refuses
 * `temperature`, and every call to it failed). Pure, so JUnit 5.
 */
class ModelParamsTest {

    private val older = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4-turbo", "gpt-3.5-turbo")
    private val reasoning = listOf("gpt-5", "gpt-5-mini", "gpt-6-luna", "GPT-6-astra", "o1", "o3-mini", "o4-mini", " gpt-5.1 ")
    private val unknown = listOf("a-model", "", "claude-something", "mistral-large")

    @Test
    fun `older models keep temperature 0 and are sent no reasoning effort`() {
        older.forEach { assertWithMessage(it).that(ModelParams.of(it)).isEqualTo(ModelParams(0, null)) }
    }

    @Test
    fun `reasoning models are sent no temperature and a low reasoning effort`() {
        reasoning.forEach { assertWithMessage(it).that(ModelParams.of(it)).isEqualTo(ModelParams(null, "low")) }
    }

    @Test
    fun `a name the rule does not know keeps what the app always sent`() {
        unknown.forEach { assertWithMessage(it).that(ModelParams.of(it)).isEqualTo(ModelParams(0, null)) }
    }

    @Test
    fun `the estimate request carries exactly the parameters for its model`() {
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
    }

    @Test
    fun `the review request carries exactly the parameters for its model`() {
        val request = ReviewRequest(ReviewProcess.NEW_FOOD, "Oat biscuit", "", null, "", null, null)
        older.forEach { model ->
            val body = parsed(ReviewPrompt.requestBody(model, request))
            assertWithMessage(model).that(body["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
            assertWithMessage(model).that(body.keys).doesNotContain("reasoning_effort")
        }
        reasoning.forEach { model ->
            val body = parsed(ReviewPrompt.requestBody(model, request))
            assertWithMessage(model).that(body.keys).doesNotContain("temperature")
            assertWithMessage(model).that(body["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("low")
        }
    }

    /** Structured outputs are unchanged for every family, and no token limit is sent to any. */
    @Test
    fun `every request keeps its strict schema and sends no token limit`() {
        val request = ReviewRequest(ReviewProcess.NEW_FOOD, "Oat biscuit", "", null, "", null, null)
        (older + reasoning + unknown).forEach { model ->
            listOf(
                parsed(EstimatePrompt.requestBody(model = model, description = "one apple")),
                parsed(ReviewPrompt.requestBody(model, request)),
            ).forEach { body ->
                val format = body["response_format"]!!.jsonObject
                assertWithMessage(model).that(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
                assertWithMessage(model)
                    .that(format["json_schema"]!!.jsonObject["strict"]!!.jsonPrimitive.content).isEqualTo("true")
                assertWithMessage(model).that(body.keys).containsNoneOf("max_tokens", "max_completion_tokens")
            }
        }
    }

    private fun parsed(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject
}
