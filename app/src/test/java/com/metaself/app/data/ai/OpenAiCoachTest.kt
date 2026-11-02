package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.CoachRequest
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.FrontPage
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.RecordBegins
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * The coach, against a server running in this process (D161). **No test in this project makes a real network call.**
 * What is proved is this app's part: each look is one call counted by the shared rule, and the day's ceiling holds.
 */
class OpenAiCoachTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private val line = ConversationLine(LocalDateTime.of(2026, 9, 3, 8, 30), true, LineKind.MESSAGE, "Invented question.")
    private val request = CoachRequest(
        line,
        listOf(line),
        FrontPage(TEST_EPOCH_DAY, null, null, Rhythm(0, 3), null, null, null, null, null, null, RecordBegins(null, null, null, null)),
        emptyList(),
        1,
    )

    private fun coach(settings: AiSettingsStore) = OpenAiCoach(
        keys = object : ApiKeyStore {
            override val key: Flow<String?> = MutableStateFlow("a-key")
            override suspend fun save(key: String) = Unit
            override suspend fun clear() = Unit
        },
        settings = settings,
        client = OkHttpClient(),
        profiles = FakeRequestProfileStore(),
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private fun reply(content: String) = """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    @Test
    fun `a look is one call, counted once, read as its turn`() = runTest {
        val settings = Settings(AiSettings())
        server.enqueue(MockResponse().setBody(reply("""{"open":[{"drawer":"meals","from":"2026-09-01","to":"2026-09-02","words":""}],"reply":"","remember":[]}""")))

        val turn = coach(settings).look(request, null) as TrainerReply.Answered

        assertThat(turn.value.open.single().drawer).isEqualTo("meals")
        assertThat(turn.model).isEqualTo(AiSettings().model)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
        assertThat(server.takeRequest().body.readUtf8()).contains("coach_reply")
    }

    @Test
    fun `a day's allowance spent asks nothing`() = runTest {
        val settings = Settings(AiSettings(dailyCeiling = 2, usedToday = 2))

        val failed = coach(settings).look(request, null) as TrainerReply.Failed

        assertThat(failed.failure).isEqualTo(EstimateResult.CeilingReached)
        assertThat(server.requestCount).isEqualTo(0)
    }

    private class Settings(initial: AiSettings) : AiSettingsStore {
        private val state = MutableStateFlow(initial)
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
}
