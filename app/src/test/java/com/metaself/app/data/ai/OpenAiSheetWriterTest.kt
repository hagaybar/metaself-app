package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.RewrittenFact
import com.metaself.app.domain.trainer.SheetRewriteRequest
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
 * The rewrite's call (D150), against a server in this process. **No test in this project makes a real network call.**
 * One call, counted once; a failure logged by kind, never with the conversation's words. Every word is invented.
 */
class OpenAiSheetWriterTest {

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
    fun `a rewritten sheet is one call, counted once, with the model's name`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply("""{"facts":[{"id":null,"group":"body","text":"Invented.","since":"2026-09-01","source":"they_said"}]}""")))

        val reply = writer(settings = settings).rewrite(REQUEST)

        assertThat(reply).isEqualTo(
            TrainerReply.Answered(listOf(RewrittenFact(null, FactGroup.BODY, "Invented.", TEST_EPOCH_DAY - 2, FactSource.SAID)), AiSettings().model),
        )
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
        assertThat(server.takeRequest().body.readUtf8()).contains("trainer_sheet")
    }

    @Test
    fun `an unreadable answer or a provider error is logged by kind, never with the words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setBody(reply("""{"facts":"$WORDS"}""")))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"$WORDS"}}"""))

        val unreadable = writer(problems = log).rewrite(REQUEST)
        val refused = writer(problems = log).rewrite(REQUEST)

        assertThat((unreadable as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat((refused as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Refused::class.java)
        assertThat(log.problems.map { it.kind }).containsExactly("sheet unreadable", "sheet refused").inOrder()
        assertThat(log.problems.joinToString { it.detail }).doesNotContain(WORDS)
    }

    @Test
    fun `with no key nothing is sent`() = runTest {
        val reply = writer(key = null).rewrite(REQUEST)

        assertThat(reply).isEqualTo(TrainerReply.Failed(EstimateResult.NoKey))
        assertThat(server.requestCount).isEqualTo(0)
    }

    private fun writer(key: String? = "a-key", settings: FakeSettings = FakeSettings(), problems: ProblemLog = ProblemLog.NONE) =
        OpenAiSheetWriter(
            keys = FakeKeys(key),
            settings = settings,
            client = OkHttpClient(),
            profiles = FakeRequestProfileStore(),
            problems = problems,
            baseUrl = server.url("/v1/chat/completions").toString(),
        )

    private fun reply(content: String): String = """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private class RecordingLog : ProblemLog {
        val problems = mutableListOf<Problem>()
        override fun recent(): List<Problem> = problems.reversed()
        override fun record(kind: String, detail: String) {
            problems += Problem(0, kind, detail)
        }
        override fun clear() = problems.clear()
    }

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

    private companion object {
        const val WORDS = "Invented words that must not reach the log"
        val REQUEST = SheetRewriteRequest(
            today = TEST_EPOCH_DAY,
            facts = emptyList(),
            aboutMe = null,
            since = null,
            conversation = listOf(ConversationLine(LocalDateTime.of(2026, 9, 1, 8, 30), true, LineKind.MESSAGE, WORDS)),
        )
    }
}
