package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Learning what a model accepts from its refusals (D57 §3–§5), against a server running in this
 * process. **No test in this project makes a real network call.** The refusal bodies are
 * [Refusals]; the request bodies are the real estimate request's.
 */
class OpenAiCallLearningTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `refused twice then answered, the working profile is remembered and every request counted`() =
        runTest {
            val settings = FakeSettings(AiSettings(model = "an-invented-model"))
            val profiles = FakeRequestProfileStore()
            server.enqueue(refusal(Refusals.TEMPERATURE))
            server.enqueue(refusal(Refusals.EFFORT_LOW_WITH_LIST))
            server.enqueue(MockResponse().setBody(ANSWER))

            val outcome = call(settings, profiles).asked()

            assertThat((outcome as OpenAiCall.Outcome.Body).text).isEqualTo(ANSWER)
            assertThat(outcome.profile).isEqualTo(RequestProfile(temperature = false, reasoningEffort = "medium"))
            assertThat(profiles.remembered.value).containsExactly(
                "an-invented-model",
                RequestProfile(temperature = false, reasoningEffort = "medium"),
            )
            assertThat(settings.calls).isEqualTo(3)

            val first = sent()
            assertThat(first["temperature"]!!.jsonPrimitive.content).isEqualTo("0")
            assertThat(first.keys).doesNotContain("reasoning_effort")
            val second = sent()
            assertThat(second.keys).doesNotContain("temperature")
            assertThat(second["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("low")
            val third = sent()
            assertThat(third.keys).doesNotContain("temperature")
            assertThat(third["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("medium")
        }

    @Test
    fun `a remembered profile is sent as remembered, and not written again`() = runTest {
        val learned = RequestProfile(temperature = false, reasoningEffort = "high")
        val profiles = FakeRequestProfileStore(mapOf("an-invented-model" to learned))
        server.enqueue(MockResponse().setBody(ANSWER))

        call(FakeSettings(AiSettings(model = "an-invented-model")), profiles).asked()

        assertThat(sent()["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("high")
        assertThat(profiles.writes).isEqualTo(0)
    }

    @Test
    fun `a first guess that works is remembered as it is`() = runTest {
        val profiles = FakeRequestProfileStore()
        server.enqueue(MockResponse().setBody(ANSWER))

        call(FakeSettings(AiSettings(model = "gpt-6-luna")), profiles).asked()

        assertThat(profiles.remembered.value).containsExactly("gpt-6-luna", RequestProfile.REASONING)
    }

    /** The provider changed the model: learning starts from what was remembered, which is replaced. */
    @Test
    fun `a remembered profile refused later is relearned from where it stands`() = runTest {
        val profiles = FakeRequestProfileStore(mapOf("gpt-6-luna" to RequestProfile.REASONING))
        server.enqueue(refusal(Refusals.RESPONSE_FORMAT))
        server.enqueue(MockResponse().setBody(ANSWER))

        call(FakeSettings(AiSettings(model = "gpt-6-luna")), profiles).asked()

        assertThat(profiles.remembered.value)
            .containsExactly("gpt-6-luna", RequestProfile.REASONING.copy(strictFormat = false))
        sent()
        val second = sent()
        assertThat(second["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            .isEqualTo("json_object")
        assertThat(second["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
            .contains("\"figures_per\"")
    }

    /** Temperature 0 was sent first; after its refusal and the reasoning setting's, neither is sent. */
    @Test
    fun `a profile already tried in the call is never sent again`() = runTest {
        server.enqueue(refusal(Refusals.TEMPERATURE))
        server.enqueue(refusal(Refusals.EFFORT_PARAMETER))
        server.enqueue(MockResponse().setBody(ANSWER))
        val profiles = FakeRequestProfileStore()

        call(FakeSettings(AiSettings(model = "an-invented-model")), profiles).asked()

        sent()
        sent()
        val third = sent()
        assertThat(third.keys).containsNoneOf("temperature", "reasoning_effort")
        assertThat(profiles.remembered.value.getValue("an-invented-model"))
            .isEqualTo(RequestProfile(temperature = false, reasoningEffort = null))
    }

    @Test
    fun `at most three retries follow the first request, and the last refusal is shown`() = runTest {
        val settings = FakeSettings(AiSettings(model = "an-invented-model"))
        val profiles = FakeRequestProfileStore()
        server.enqueue(refusal(Refusals.TEMPERATURE))
        server.enqueue(refusal(Refusals.effortWithoutList("low")))
        server.enqueue(refusal(Refusals.effortWithoutList("medium")))
        server.enqueue(refusal(Refusals.RESPONSE_FORMAT))
        server.enqueue(MockResponse().setBody(ANSWER))

        val outcome = call(settings, profiles).asked()

        assertThat(server.requestCount).isEqualTo(4)
        assertThat(settings.calls).isEqualTo(4)
        assertThat(outcome).isEqualTo(
            OpenAiCall.Outcome.Failed(
                EstimateResult.Refused(
                    "Invalid parameter: 'response_format' of type 'json_schema' is not supported with this model.",
                ),
                status = 400,
            ),
        )
        assertThat(profiles.writes).isEqualTo(0)
    }

    @Test
    fun `a refusal nothing can answer is shown as it always was, after one request`() = runTest {
        listOf(Refusals.UNKNOWN, Refusals.MAX_TOKENS).forEach { body ->
            val settings = FakeSettings(AiSettings(model = "gpt-6-luna"))
            server.enqueue(refusal(body))

            val outcome = call(settings, FakeRequestProfileStore()).asked()

            assertThat((outcome as OpenAiCall.Outcome.Failed).status).isEqualTo(400)
            assertThat((outcome.failure as EstimateResult.Refused).detail).startsWith(
                Json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!
                    .jsonPrimitive.content.take(40),
            )
            assertThat(settings.calls).isEqualTo(1)
        }
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `only a 400 is learned from`() = runTest {
        server.enqueue(refusal(Refusals.TEMPERATURE).setResponseCode(429))

        val outcome = call(FakeSettings(AiSettings(model = "an-invented-model")), FakeRequestProfileStore())
            .asked()

        assertThat((outcome as OpenAiCall.Outcome.Failed).status).isEqualTo(429)
        assertThat(server.requestCount).isEqualTo(1)
    }

    /** The ceiling bounds a runaway: a retry is not sent once the day's allowance is spent. */
    @Test
    fun `no retry is sent with the day's allowance spent`() = runTest {
        val settings = FakeSettings(AiSettings(model = "an-invented-model", dailyCeiling = 2, usedToday = 1))
        server.enqueue(refusal(Refusals.TEMPERATURE))
        server.enqueue(MockResponse().setBody(ANSWER))

        val outcome = call(settings, FakeRequestProfileStore()).asked()

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
        assertThat((outcome as OpenAiCall.Outcome.Failed).failure)
            .isInstanceOf(EstimateResult.Refused::class.java)
    }

    /** A caller that read the answer, and so remembers how it was asked for. */
    private suspend fun OpenAiCall.asked(): OpenAiCall.Outcome =
        send(::estimate).also { if (it is OpenAiCall.Outcome.Body) remember(it) }

    /** D57 §5: only an answer that was read teaches; one in the wrong shape leaves nothing behind. */
    @Test
    fun `an answer the estimate cannot read is not remembered`() = runTest {
        val profiles = FakeRequestProfileStore()
        server.enqueue(refusal(Refusals.TEMPERATURE))
        server.enqueue(MockResponse().setBody(reply("not the shape asked for")))

        val result = estimator(FakeSettings(AiSettings(model = "an-invented-model")), profiles)
            .estimate("one apple")

        assertThat(result).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(profiles.writes).isEqualTo(0)
    }

    /** D57 §6: the proposal says how the request that got it was sent — not a later read of the store. */
    @Test
    fun `a read answer is remembered and says how it was asked for`() = runTest {
        val profiles = FakeRequestProfileStore()
        server.enqueue(refusal(Refusals.TEMPERATURE))
        server.enqueue(MockResponse().setBody(reply(ONE_APPLE)))

        val result = estimator(FakeSettings(AiSettings(model = "an-invented-model")), profiles)
            .estimate("one apple")

        assertThat((result as EstimateResult.Proposed).sentAs)
            .isEqualTo("an-invented-model works: no temperature, low thinking, strict format.")
        assertThat(profiles.remembered.value)
            .containsExactly("an-invented-model", RequestProfile(temperature = false, reasoningEffort = "low"))
    }

    @Test
    fun `a first guess that is answered works as sent`() = runTest {
        server.enqueue(MockResponse().setBody(reply(ONE_APPLE)))

        val result = estimator(FakeSettings(AiSettings(model = "gpt-6-luna")), FakeRequestProfileStore())
            .estimate("one apple")

        assertThat((result as EstimateResult.Proposed).sentAs).isEqualTo("gpt-6-luna works as sent.")
    }

    /** A store that cannot be read or written costs the learning, never the answer. */
    @Test
    fun `a broken store is nothing remembered, and the answer still comes`() = runTest {
        server.enqueue(MockResponse().setBody(reply(ONE_APPLE)))

        val result = estimator(FakeSettings(AiSettings(model = "gpt-6-luna")), BrokenStore())
            .estimate("one apple")

        assertThat((result as EstimateResult.Proposed).sentAs).isEqualTo("gpt-6-luna works as sent.")
    }

    private fun estimate(model: String, profile: RequestProfile): String =
        EstimatePrompt.requestBody(model, "one apple", profile = profile)

    private fun refusal(body: String) = MockResponse().setResponseCode(400).setBody(body)

    private fun sent(): JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun call(settings: FakeSettings, profiles: RequestProfileStore) = OpenAiCall(
        keys = FakeKeys("a-key"),
        settings = settings,
        client = OkHttpClient(),
        profiles = profiles,
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private class FakeKeys(value: String?) : ApiKeyStore {
        override val key: Flow<String?> = MutableStateFlow(value)
        override suspend fun save(key: String) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeSettings(initial: AiSettings = AiSettings()) : AiSettingsStore {
        private val state = MutableStateFlow(initial)
        var calls: Int = 0
            private set

        override val settings: Flow<AiSettings> = state
        override suspend fun setModel(model: String) = Unit
        override suspend fun setDailyCeiling(ceiling: Int) = Unit
        override suspend fun recordCall() {
            calls++
            state.value = state.value.copy(usedToday = state.value.usedToday + 1)
        }
    }

    private fun estimator(settings: FakeSettings, profiles: RequestProfileStore) = OpenAiMealEstimator(
        keys = FakeKeys("a-key"),
        settings = settings,
        client = OkHttpClient(),
        profiles = profiles,
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private fun reply(content: String): String =
        """{"choices":[{"message":{"content":${kotlinx.serialization.json.JsonPrimitive(content)}}}]}"""

    /** Remembers nothing it is asked for, and cannot be read. */
    private class BrokenStore : RequestProfileStore {
        override fun profileFor(model: String): Flow<RequestProfile?> = flow { throw IOException("disk") }
        override suspend fun remember(model: String, profile: RequestProfile) = throw IOException("disk")
    }

    private companion object {
        const val ANSWER = """{"choices":[]}"""

        const val ONE_APPLE = """{"note":"","items":[
            {"name":"Apple","detail":"","amount":1,"unit":"apple","figures_per":"1",
             "kcal":80,"protein_g":0,"carbs_g":20,"fat_g":0,"confidence":"MEDIUM"}]}"""
    }
}
