package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekFacts
import com.metaself.app.domain.trainer.Wish
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The trainer, against a server running in this process (D84).
 *
 * **No test in this project makes a real network call.** What is proved is this app's own part: one
 * call, counted by the shared rule, no retry, the closed set of failures, and a problem log that never
 * holds the owner's words or the model's answer. Every word and figure here is invented.
 */
class OpenAiTrainerTest {

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
    fun `a good plan reply is one call, counted once, with the model's name`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply(GOOD_PLAN)))

        val reply = trainer(settings = settings).suggest(PLAN_REQUEST) as TrainerReply.Answered

        assertThat(reply.value.steps).hasSize(3)
        assertThat(reply.model).isEqualTo(AiSettings().model)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `the key goes only in the header`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_FEEDBACK)))

        trainer().feedback(REVIEW_REQUEST)

        val sent = server.takeRequest()
        assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer a-key")
        assertThat(sent.body.readUtf8()).doesNotContain("a-key")
    }

    @Test
    fun `with no key or at the ceiling nothing is sent`() = runTest {
        assertThat(trainer(key = null).suggest(PLAN_REQUEST)).isEqualTo(TrainerReply.Failed(EstimateResult.NoKey))
        val spent = FakeSettings(AiSettings(dailyCeiling = 2, usedToday = 2))
        assertThat(trainer(settings = spent).feedback(REVIEW_REQUEST)).isEqualTo(TrainerReply.Failed(EstimateResult.CeilingReached))
        assertThat(server.requestCount).isEqualTo(0)
    }

    /** One call, no retry. */
    @Test
    fun `an unreadable answer is a failure, not asked again, and logged without its words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setBody(reply("{\"title\": \"$WORDS\"}")))

        val reply = trainer(problems = log).feedback(REVIEW_REQUEST)

        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(log.problems.single().kind).isEqualTo("trainer unreadable")
        assertThat(log.problems.toString()).doesNotContain(WORDS)
    }

    @Test
    fun `a refusal is logged by its status, never the provider's words`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"$WORDS"}}"""))

        trainer(problems = log).suggest(PLAN_REQUEST)

        assertThat(log.problems.single().kind).isEqualTo("trainer refused")
        assertThat(log.problems.single().detail).isEqualTo("the provider answered 401")
    }

    @Test
    fun `no network is unreachable`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val reply = trainer().suggest(PLAN_REQUEST)

        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreachable::class.java)
    }

    private fun trainer(
        key: String? = "a-key",
        settings: FakeSettings = FakeSettings(),
        problems: ProblemLog = ProblemLog.NONE,
    ) = OpenAiTrainer(
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
        const val WORDS = "Invented words that must not reach the log"

        val GOOD_PLAN = """{"title":"Steady walk with two climbs","steps":[
            {"from_minute":0,"to_minute":10,"what":"Warm up","how":"easy pace"},
            {"from_minute":10,"to_minute":35,"what":"Two climbs","how":"zone 3"},
            {"from_minute":35,"to_minute":45,"what":"Cool down","how":""}],"why":"Invented reason."}"""

        val GOOD_FEEDBACK = """{"headline":"A steady session","against_plan":"As planned.","numbers":"Invented.",
            "next_time":"Invented.","this_week":"Invented.","plan_followed":"yes"}"""

        /** An empty record: six weeks of nothing, no weight, no profile. */
        private fun request(question: TrainerQuestion) = TrainerRequest(
            question = question,
            today = TEST_EPOCH_DAY,
            aboutMe = null,
            sessions = emptyList(),
            months = emptyList(),
            weeks = (0 until 6).map { back ->
                WeekFacts(monday = TEST_EPOCH_DAY - 3 - 7L * back, distanceM = null, averageActiveKcal = null, sessions = 0, current = back == 0)
            },
            weight = null,
            goal = null,
            body = null,
            thisWeek = Rhythm(sessionsSoFar = 0, daysLeft = 3),
            earlierFeedback = emptyList(),
        )

        val PLAN_REQUEST = request(
            TrainerQuestion.Plan(PlanAnswers(PlanActivity.OUTDOOR_WALK, TimeAvailable.MIN_45, Feeling.NORMAL, Wish.NOT_SURE)),
        )

        val REVIEW_REQUEST = request(
            TrainerQuestion.Review(
                SessionFacts(
                    epochDay = TEST_EPOCH_DAY, kind = WorkoutKind.WALK, minutes = 40, distanceM = null, distanceFrom = null,
                    energyKcal = null, energyFrom = null, avgHeartRate = null, maxHeartRate = null, zoneMinutes = null,
                    zoneMaxEstimated = true, steps = null, stepsFrom = null, felt = Felt.RIGHT, words = WORDS, plan = null,
                ),
            ),
        )
    }
}
