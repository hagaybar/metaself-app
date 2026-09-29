package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.letter.FoodWeek
import com.metaself.app.domain.letter.LetterFigures
import com.metaself.app.domain.letter.LetterReply
import com.metaself.app.domain.letter.LetterRequest
import com.metaself.app.domain.letter.LetterTexts
import com.metaself.app.domain.letter.MovementFigures
import com.metaself.app.domain.letter.WeekFigures
import com.metaself.app.domain.movement.MovementWeek
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

/**
 * The letter's call (D101), against a server running in this process. **No test in this project makes a
 * real network call.** One call, counted by the shared rule, no retry, and a problem log that never
 * holds the owner's note or the model's answer. Every word and figure here is invented.
 */
class OpenAiLetterWriterTest {

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
    fun `an answered letter is one call, counted once, with the model's name`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply(GOOD_LETTER)))

        val reply = writer(settings = settings).write(REQUEST)

        assertThat(reply).isEqualTo(
            LetterReply.Written(LetterTexts("Invented", "Invented a.", "Invented b.", "Invented c.", "Invented d.", "Invented e."), AiSettings().model),
        )
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
        val sent = server.takeRequest()
        assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer a-key")
        assertThat(sent.body.readUtf8()).contains("weekly_letter")
    }

    @Test
    fun `a provider error is a refusal, logged by its status and never the request's words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"$NOTE"}}"""))

        val reply = writer(problems = log).write(REQUEST)

        assertThat((reply as LetterReply.Failed).failure).isInstanceOf(EstimateResult.Refused::class.java)
        assertThat(log.problems.single().kind).isEqualTo("letter refused")
        assertThat(log.problems.single().detail).isEqualTo("the provider answered 500")
        assertThat(log.problems.toString()).doesNotContain(NOTE)
        assertThat(log.problems.toString()).doesNotContain(LAST_LINE)
    }

    @Test
    fun `an unreadable answer is a failure, not asked again, and logged without its words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setBody(reply("""{"headline":"$NOTE"}""")))

        val reply = writer(problems = log).write(REQUEST)

        assertThat((reply as LetterReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(log.problems.single().kind).isEqualTo("letter unreadable")
        assertThat(log.problems.toString()).doesNotContain(NOTE)
    }

    @Test
    fun `with no key nothing is sent`() = runTest {
        assertThat(writer(key = null).write(REQUEST)).isEqualTo(LetterReply.Failed(EstimateResult.NoKey))
        assertThat(server.requestCount).isEqualTo(0)
    }

    private fun writer(
        key: String? = "a-key",
        settings: FakeSettings = FakeSettings(),
        problems: ProblemLog = ProblemLog.NONE,
    ) = OpenAiLetterWriter(
        keys = FakeKeys(key),
        settings = settings,
        client = OkHttpClient(),
        profiles = FakeRequestProfileStore(),
        problems = problems,
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private fun reply(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private class RecordingLog : ProblemLog {
        val problems = mutableListOf<Problem>()
        override fun recent(): List<Problem> = problems.reversed()
        override fun record(kind: String, detail: String) {
            problems += Problem(0, kind, detail)
        }
        override fun clear() = problems.clear()
    }

    private class FakeKeys(private val value: String?) : ApiKeyStore {
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
        const val NOTE = "Invented note that must not reach the log"
        const val LAST_LINE = "Invented line that must not reach the log"

        const val GOOD_LETTER = """{"headline":"Invented","effort":"Invented a.","progress":"Invented b.",
            "look_at":"Invented c.","next_week":"Invented d.","close":"Invented e."}"""

        private val MONDAY = MovementWeek.mondayOf(TEST_EPOCH_DAY)

        private fun week(monday: Long) = WeekFigures(
            monday, FoodWeek(5, 2_000, 100, 200, 70), -0.2, true, MovementFigures(3, 120, 9_000, 1, 1, 0, 300, 8_000), null,
        )

        val REQUEST = LetterRequest(
            figures = LetterFigures(week(MONDAY), List(4) { week(MONDAY - 7L * (it + 1)) }, 2_100),
            sessions = emptyList(), aboutMe = NOTE, goal = null, body = null,
            planTitle = null, planWeek = null, lastNextWeek = LAST_LINE,
        )
    }
}
