package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekFacts
import com.metaself.app.domain.trainer.WorkbenchReply
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** D106 over a local server; no real network call. Every word is invented. */
class OpenAiWorkbenchTest {

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
    fun `the reply is the model's text as written, and the body sent is handed back verbatim`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":${JsonPrimitive(PROSE)}}}]}"""))
        val settings = FakeSettings()
        val profiles = FakeRequestProfileStore()

        val reply = workbench(settings = settings, profiles = profiles).send(SYSTEM, REQUEST) as WorkbenchReply.Answered

        val body = server.takeRequest().body.readUtf8()
        assertThat(reply.text).isEqualTo(PROSE)
        assertThat(reply.sent).isEqualTo(body)
        assertThat(Json.parseToJsonElement(body).jsonObject.keys).doesNotContain("response_format")
        assertThat(body).isEqualTo(TrainerPrompt.workbenchBody(AiSettings().model, SYSTEM, REQUEST, RequestProfile.guess(AiSettings().model)))
        assertThat(settings.calls).isEqualTo(1)
        assertThat(profiles.writes).isEqualTo(1)
    }

    @Test
    fun `a refusal a different profile can answer is retried, and the second body and profile are what is kept`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody(Refusals.TEMPERATURE))
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":${JsonPrimitive(PROSE)}}}]}"""))
        val settings = FakeSettings()
        val profiles = FakeRequestProfileStore()

        val reply = workbench(settings = settings, profiles = profiles).send(SYSTEM, REQUEST) as WorkbenchReply.Answered

        val first = server.takeRequest().body.readUtf8()
        val second = server.takeRequest().body.readUtf8()
        assertThat(reply.sent).isEqualTo(second)
        assertThat(reply.sent).isNotEqualTo(first)
        assertThat(settings.calls).isEqualTo(2)
        assertThat(profiles.remembered.value).containsExactly(
            AiSettings().model,
            RequestProfile(temperature = false, reasoningEffort = "low"),
        )
    }

    @Test
    fun `with no key nothing is sent, and the failure is the call's own`() = runTest {
        val reply = workbench(key = null).send(SYSTEM, REQUEST)

        assertThat(reply).isEqualTo(WorkbenchReply.Failed(EstimateResult.NoKey, sent = null))
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a refusal is the provider's words, with what was sent`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"Invented refusal."}}"""))

        val reply = workbench().send(SYSTEM, REQUEST) as WorkbenchReply.Failed

        assertThat(reply.failure).isEqualTo(EstimateResult.Refused("Invented refusal."))
        assertThat(reply.sent).isNotNull()
    }

    @Test
    fun `an answer with no message in it is unreadable, not a crash`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[]}"""))
        val profiles = FakeRequestProfileStore()

        val reply = workbench(profiles = profiles).send(SYSTEM, REQUEST) as WorkbenchReply.Failed

        assertThat(reply.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(reply.sent).isNotNull()
        assertThat(profiles.writes).isEqualTo(0)
    }

    @Test
    fun `a message whose content is JSON null is unreadable, not the literal word null`() = runTest {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":null}}]}"""))

        val reply = workbench().send(SYSTEM, REQUEST) as WorkbenchReply.Failed

        assertThat(reply.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    private fun workbench(
        key: String? = "a-key",
        settings: FakeSettings = FakeSettings(),
        profiles: FakeRequestProfileStore = FakeRequestProfileStore(),
    ) = OpenAiWorkbench(
        keys = FakeKeys(key),
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

    private class FakeSettings : AiSettingsStore {
        private val state = MutableStateFlow(AiSettings())
        var calls = 0
            private set
        override val settings: Flow<AiSettings> = state
        override suspend fun setModel(model: String) = Unit
        override suspend fun setDailyCeiling(ceiling: Int) = Unit
        override suspend fun recordCall() {
            calls++
            state.value = state.value.copy(usedToday = state.value.usedToday + 1)
        }
    }

    private companion object {
        const val SYSTEM = "Invented instructions."
        const val PROSE = "Invented reply, in prose, not JSON."
        val REQUEST = TrainerRequest(
            question = TrainerQuestion.Plan(PlanAnswers(PlanActivity.TREADMILL_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)),
            today = TEST_EPOCH_DAY,
            aboutMe = null,
            sessions = emptyList(),
            weeks = (0 until 6).map { back ->
                WeekFacts(monday = TEST_EPOCH_DAY - 3 - 7L * back, distanceM = null, averageActiveKcal = null, sessions = 0, current = back == 0)
            },
            months = emptyList(),
            weight = null,
            goal = null,
            body = null,
            thisWeek = Rhythm(sessionsSoFar = 0, daysLeft = 3),
            earlierFeedback = emptyList(),
        )
    }
}
