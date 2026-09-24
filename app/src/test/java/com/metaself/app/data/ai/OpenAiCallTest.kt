package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The one call to the model, shared by everything that asks it something (D54 §6), against a server
 * running in this process.
 *
 * **No test in this project makes a real network call.** What is proved here is the counting rule —
 * a call that happened is counted, a refusal included; one that never left is not — and that every
 * failure comes back as one of the closed set, never as an exception.
 */
class OpenAiCallTest {

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
    fun `an answer is handed back as it came, and counted once`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))

        val outcome = call(settings = settings).send { model -> """{"model":"$model"}""" }

        assertThat(outcome).isEqualTo(OpenAiCall.Outcome.Body("""{"choices":[]}"""))
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `the body is built for the model in settings and the key goes only in the header`() =
        runTest {
            server.enqueue(MockResponse().setBody("{}"))

            call(settings = FakeSettings(AiSettings(model = "a-model")))
                .send { model -> """{"model":"$model"}""" }

            val sent = server.takeRequest()
            assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer a-key")
            assertThat(sent.method).isEqualTo("POST")
            val body = sent.body.readUtf8()
            assertThat(body).isEqualTo("""{"model":"a-model"}""")
            assertThat(body).doesNotContain("a-key")
        }

    @Test
    fun `with no key nothing is built, sent or counted`() = runTest {
        val settings = FakeSettings()
        var built = false

        val outcome = call(key = " ", settings = settings).send { built = true; "{}" }

        assertThat(outcome).isEqualTo(OpenAiCall.Outcome.Failed(EstimateResult.NoKey))
        assertThat(built).isFalse()
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(settings.calls).isEqualTo(0)
    }

    @Test
    fun `at the ceiling nothing is sent or counted`() = runTest {
        val settings = FakeSettings(AiSettings(dailyCeiling = 2, usedToday = 2))

        val outcome = call(settings = settings).send { "{}" }

        assertThat(outcome).isEqualTo(OpenAiCall.Outcome.Failed(EstimateResult.CeilingReached))
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(settings.calls).isEqualTo(0)
    }

    /** A refusal still cost money and still spends the day's allowance. */
    @Test
    fun `a refusal is counted and carries the provider's own words`() = runTest {
        val settings = FakeSettings()
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )

        val outcome = call(settings = settings).send { "{}" }

        assertThat(outcome)
            .isEqualTo(
                OpenAiCall.Outcome.Failed(EstimateResult.Refused("Incorrect API key provided"), status = 401),
            )
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `a refusal without words says the status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{}}"""))

        assertThat(call().send { "{}" })
            .isEqualTo(
                OpenAiCall.Outcome.Failed(EstimateResult.Refused("the provider answered 429"), status = 429),
            )
    }

    @Test
    fun `a call that never left is unreachable and not counted`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val outcome = call(settings = settings).send { "{}" }

        assertThat(outcome).isEqualTo(OpenAiCall.Outcome.Failed(EstimateResult.Unreachable))
        assertThat(settings.calls).isEqualTo(0)
    }

    /** D8: nothing may escape the seam, including an exception that is not about the network. */
    @Test
    fun `anything else thrown is a refusal, not a crash`() = runTest {
        val throwing = OkHttpClient.Builder()
            .addInterceptor { throw SecurityException("Permission denied (missing INTERNET?)") }
            .build()

        val outcome = call(client = throwing).send { "{}" }

        assertThat(outcome).isEqualTo(
            OpenAiCall.Outcome.Failed(EstimateResult.Refused("Permission denied (missing INTERNET?)")),
        )
    }

    /** Only the call's own failures travel as one: a proposal is never a failure. */
    @Test
    fun `a failure cannot carry an answer`() {
        assertThrows<IllegalArgumentException> {
            OpenAiCall.Outcome.Failed(EstimateResult.AmountMissing(emptyList()))
        }
    }

    private fun call(
        key: String? = "a-key",
        settings: FakeSettings = FakeSettings(),
        client: OkHttpClient = OkHttpClient(),
    ) = OpenAiCall(
        keys = FakeKeys(key),
        settings = settings,
        client = client,
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
}
