package com.metaself.app.data.letter

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.ai.AiSettings
import com.metaself.app.data.ai.AiSettingsStore
import com.metaself.app.data.ai.ApiKeyStore
import com.metaself.app.data.ai.FakeRequestProfileStore
import com.metaself.app.data.ai.OpenAiLetterWriter
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.data.health.BackgroundHealthCopy
import com.metaself.app.data.health.BackgroundHealthRead
import com.metaself.app.data.health.FakeMovementRecord
import com.metaself.app.data.health.HealthRecordStatus
import com.metaself.app.data.profile.FakeProfileRepository
import com.metaself.app.data.trainer.FakeProgrammeStore
import com.metaself.app.data.trainer.FakeTrainerStore
import com.metaself.app.data.trainer.InMemoryAboutMeStore
import com.metaself.app.data.weight.InMemoryWeightRepository
import com.metaself.app.domain.day.DayTotals
import com.metaself.app.domain.letter.WeeklyLetter
import com.metaself.app.domain.profile.aProfile
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
import java.time.LocalDate

/**
 * D8 and D101 end to end: the Sunday run — the job, the real letter writer over a server in this process,
 * and the worker's decisions — leaves nothing in the problem log but kinds. Never the model's answer
 * (an unreadable one carries it), never the request's words: the owner's note, last week's line, the
 * figures. **No test in this project makes a real network call.** Every word and figure is invented.
 */
class LetterPrivacyTest {

    private lateinit var server: MockWebServer
    private val log = RecordingLog()
    private val sundayEvening = LocalDate.ofEpochDay(LETTER_MONDAY + 6).atTime(20, 0)

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun job(letters: LetterStore = FakeLetterStore(lastWeek())) = WriteWeeklyLetter(
        food = FakeFoodTotals(mapOf(LETTER_MONDAY to DayTotals(KCAL.toInt(), 123, 234, 67))),
        weights = InMemoryWeightRepository(),
        record = FakeMovementRecord(),
        reviews = FakeTrainerStore(),
        programmes = FakeProgrammeStore(),
        profiles = FakeProfileRepository(aProfile()),
        aboutMe = InMemoryAboutMeStore(NOTE),
        writer = OpenAiLetterWriter(
            keys = FakeKeys(),
            settings = FakeSettings(),
            client = OkHttpClient(),
            profiles = FakeRequestProfileStore(),
            problems = log,
            baseUrl = server.url("/v1/chat/completions").toString(),
        ),
        letters = letters,
        backgroundRead = BackgroundHealthRead.NONE,
        backgroundCopy = BackgroundHealthCopy { false },
        status = HealthRecordStatus.NONE,
        now = { 1_000 },
        year = { 2026 },
    )

    private suspend fun runOnce(job: WriteWeeklyLetter) =
        LetterRun.run(LETTER_MONDAY, sundayEvening, write = { job(it) }, wanted = { job.wanted(it) }, problems = log)

    @Test
    fun `an unreadable answer is retried, and the log holds its kind but not the answer or the request`() = runTest {
        server.enqueue(MockResponse().setBody(reply("""{"headline":"$ANSWER"}""")))

        val ran = runOnce(job())

        assertThat(ran.step).isEqualTo(LetterStep.RETRY)
        assertThat(log.problems.map { it.kind }).containsExactly("letter unreadable")
        assertNothingPrivateLogged(ANSWER)
    }

    @Test
    fun `a provider error whose body quotes the request is logged by status alone`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"$NOTE $ANSWER"}}"""))

        val ran = runOnce(job())

        assertThat(ran.step).isEqualTo(LetterStep.NOTIFY_FAILED)
        assertThat(log.problems.map { it.kind }).containsExactly("letter refused")
        assertNothingPrivateLogged(ANSWER)
    }

    @Test
    fun `a store that fails with the letter in its message is logged by the failure's class alone`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_LETTER)))
        val failing = object : LetterStore by FakeLetterStore(lastWeek()) {
            override suspend fun add(letter: WeeklyLetter): Long = throw IllegalStateException("$NOTE ${letter.texts} ${letter.figures}")
        }

        val ran = runOnce(job(failing))

        assertThat(ran.step).isEqualTo(LetterStep.RETRY)
        assertThat(log.problems).containsExactly(Problem(0, "letter", "not written: IllegalStateException"))
        assertNothingPrivateLogged("Invented a.")
    }

    /** [answered]: words of the model's answer this test's server gave. */
    private fun assertNothingPrivateLogged(answered: String) {
        val everything = log.problems.joinToString { "${it.kind} ${it.detail}" }
        for (word in listOf(NOTE, LAST_LINE, KCAL, answered)) {
            assertThat(everything).doesNotContain(word)
        }
        // The request really did carry them: the log's silence is not for want of anything to leak.
        val sent = server.takeRequest().body.readUtf8()
        for (asked in listOf(NOTE, LAST_LINE, KCAL)) {
            assertThat(sent).contains(asked)
        }
    }

    private fun lastWeek(): WeeklyLetter = aWeeklyLetter(LETTER_MONDAY - 7).let { it.copy(texts = it.texts.copy(nextWeek = LAST_LINE)) }

    private fun reply(content: String): String = """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private class RecordingLog : ProblemLog {
        val problems = mutableListOf<Problem>()
        override fun recent(): List<Problem> = problems.reversed()
        override fun record(kind: String, detail: String) {
            problems += Problem(0, kind, detail)
        }
        override fun clear() = problems.clear()
    }

    private class FakeKeys : ApiKeyStore {
        override val key: Flow<String?> = MutableStateFlow("a-key")
        override suspend fun save(key: String) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeSettings : AiSettingsStore {
        private val state = MutableStateFlow(AiSettings())
        override val settings: Flow<AiSettings> = state
        override suspend fun setModel(model: String) = Unit
        override suspend fun setDailyCeiling(ceiling: Int) = Unit
        override suspend fun recordCall() {
            state.value = state.value.copy(usedToday = state.value.usedToday + 1)
        }
    }

    private companion object {
        /** The week's calories a day, as the request writes them. */
        const val KCAL = "2345"
        const val NOTE = "Invented note that must not reach the log"
        const val ANSWER = "Invented answer that must not reach the log"
        const val LAST_LINE = "Invented last line that must not reach the log"
        const val GOOD_LETTER = """{"headline":"Invented","effort":"Invented a.","progress":"Invented b.",
            "look_at":"Invented c.","next_week":"Invented d.","close":"Invented e."}"""
    }
}
