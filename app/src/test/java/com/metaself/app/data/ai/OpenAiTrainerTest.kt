package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.BeyondPlan
import com.metaself.app.domain.trainer.Feeling
import com.metaself.app.domain.trainer.Felt
import com.metaself.app.domain.trainer.FoodContext
import com.metaself.app.domain.trainer.PlanActivity
import com.metaself.app.domain.trainer.PlanAnswers
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.OUTLINE_15
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.STEADY_OUTLINE
import com.metaself.app.domain.trainer.Recovery
import com.metaself.app.domain.trainer.Rhythm
import com.metaself.app.domain.trainer.SessionFacts
import com.metaself.app.domain.trainer.TimeAvailable
import com.metaself.app.domain.trainer.TrainerQuestion
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.TrainerRequest
import com.metaself.app.domain.trainer.WeekLedger
import com.metaself.app.domain.trainer.WeeksPlan
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

    @Test
    fun `an evaluation is one call, checked against the form`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_EVALUATION)))
        server.enqueue(MockResponse().setBody(reply(GOOD_EVALUATION)))

        val fits = trainer().evaluate(EVALUATE_REQUEST)
        val tooFewWeeks = trainer().evaluate(
            request(TrainerQuestion.Evaluate(ProgrammeAsk(4, 2), TEST_EPOCH_DAY - 3, null)),
        )

        assertThat(fits).isInstanceOf(TrainerReply.Answered::class.java)
        assertThat((tooFewWeeks as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(server.takeRequest().body.readUtf8()).contains("evaluation_and_plan")
    }

    @Test
    fun `an adjustment is one call, checked against the weeks left`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_REST)))

        val reply = trainer().adjust(ADJUST_REQUEST)

        assertThat((reply as TrainerReply.Answered).value.plan.weeks).hasSize(2)
        assertThat(server.takeRequest().body.readUtf8()).contains("weeks_plan")
    }

    /**
     * The weeks left come from the plan's own remaining weeks, not `ask.weeks - weekIndex`: a plan of
     * four weeks, two weeks in, has two weeks left even though the ask (six weeks) minus the week index
     * (two) would also read four. A two-week reply is accepted; a four-week reply is refused.
     */
    @Test
    fun `the weeks left for an adjustment come from the plan's own weeks, not the ask`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_REST)))
        server.enqueue(MockResponse().setBody(reply(FOUR_WEEK_REST)))

        val twoWeekReply = trainer().adjust(MID_PLAN_ADJUST_REQUEST)
        val fourWeekReply = trainer().adjust(MID_PLAN_ADJUST_REQUEST)

        assertThat(twoWeekReply).isInstanceOf(TrainerReply.Answered::class.java)
        assertThat((fourWeekReply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    /** D121: filling in a week is one call under its own schema, checked against sessions a week. */
    @Test
    fun `a fill is one call, checked against sessions a week`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_FILL)))
        server.enqueue(MockResponse().setBody(reply(GOOD_FILL.replace("\"longest_minutes\":40", "\"longest_minutes\":400"))))

        val good = trainer().fill(FILL_REQUEST)
        val bad = trainer().fill(FILL_REQUEST)

        assertThat((good as TrainerReply.Answered).value.week.sessions).hasSize(1)
        assertThat(server.takeRequest().body.readUtf8()).contains("week_filled")
        assertThat((bad as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    /** D126: suggesting goals is one call under its own schema, each suggestion checked against today. */
    @Test
    fun `suggesting goals is one call under its own schema`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_SUGGESTIONS)))

        val reply = trainer().suggestGoals(SUGGEST_REQUEST)

        assertThat((reply as TrainerReply.Answered).value.single().goal).isEqualTo(PlanGoal.Steady)
        assertThat(server.takeRequest().body.readUtf8()).contains("goal_suggestions")
    }

    @Test
    fun `the next block is one call under its own schema`() = runTest {
        server.enqueue(MockResponse().setBody(reply(GOOD_BLOCK)))

        val reply = trainer().nextBlock(BLOCK_REQUEST)

        assertThat((reply as TrainerReply.Answered).value.block.weeks).hasSize(4)
        assertThat(server.takeRequest().body.readUtf8()).contains("next_block")
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

        val GOOD_FEEDBACK = """{"headline":"A steady session","note":"Invented note.","plan_followed":"yes"}"""

        /** An empty record: six weeks of nothing, no weight, no profile. */
        private fun request(question: TrainerQuestion) = TrainerRequest(
            question = question,
            today = TEST_EPOCH_DAY,
            aboutMe = null,
            sessions = emptyList(),
            months = emptyList(),
            weeks = (0 until 6).map { back ->
                WeekLedger(
                    monday = TEST_EPOCH_DAY - 3 - 7L * back, current = back == 0, practice = null,
                    beyond = BeyondPlan(emptyList(), null, null), food = FoodContext(0, null, null, null),
                    recovery = Recovery(null, null, null, null, null, null),
                )
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

        val GOOD_EVALUATION = """{"evaluation":{"headline":"Invented","going_well":"Invented.","to_work_on":"Invented.","since_last":""},
            "plan":{"title":"Invented","weeks":[
            {"focus":"a","sessions":[{"kind":"walk","minutes":30,"effort":"easy","what":"Walk"}]},
            {"focus":"b","sessions":[{"kind":"walk","minutes":40,"effort":"steady","what":"Walk"}]}],"why":"Invented."}}"""

        val GOOD_REST = """{"title":"Invented","weeks":[
            {"focus":"now","sessions":[]},
            {"focus":"next","sessions":[{"kind":"run","minutes":20,"effort":"push","what":"Run"}]}],"why":"Invented."}"""

        val EVALUATE_REQUEST = request(TrainerQuestion.Evaluate(ProgrammeAsk(2, 2), TEST_EPOCH_DAY - 3, null))

        private val SOME_PLAN = WeeksPlan(
            "Invented",
            List(2) { PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))) },
            "Invented.",
        )

        val ADJUST_REQUEST = request(
            TrainerQuestion.Adjust(ProgrammeAsk(2, 2), TEST_EPOCH_DAY - 3, SOME_PLAN, 0, emptyList(), emptyList(), 1, "Invented.", emptyList()),
        )

        /** A four-week plan, two weeks in: two weeks are left, though the six-week ask minus two also reads four. */
        private val FOUR_WEEK_PLAN = WeeksPlan(
            "Invented",
            List(4) { PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))) },
            "Invented.",
        )

        val MID_PLAN_ADJUST_REQUEST = request(
            TrainerQuestion.Adjust(ProgrammeAsk(6, 2), TEST_EPOCH_DAY - 3, FOUR_WEEK_PLAN, 2, emptyList(), emptyList(), 1, "Invented.", emptyList()),
        )

        /** The goal fixture's plan, its first week filled. */
        private val OUTLINED = WeeksPlan(
            "Invented",
            List(15) { if (it == 0) PlanWeek("w", listOf(PlannedSession(WorkoutKind.WALK, 30, PlannedEffort.EASY, "Walk"))) else PlanWeek("", emptyList()) },
            "Invented.",
            OUTLINE_15,
        )

        val SUGGEST_REQUEST = request(TrainerQuestion.SuggestGoals(3, "", TEST_EPOCH_DAY + 4, null, null))

        const val GOOD_SUGGESTIONS =
            """{"suggestions":[{"title":"Invented title","goal":{"kind":"steady"},"per_week":3,"why":"Invented why."}],"week_note":""}"""

        val FILL_REQUEST = request(
            TrainerQuestion.FillWeek(ProgrammeAsk(15, 2, goal = GOAL_15K), TEST_EPOCH_DAY - 3, OUTLINED, week = 1, weekIndex = 0, doneByWeek = emptyList(), howItWent = emptyList()),
        )

        val BLOCK_REQUEST = request(
            TrainerQuestion.NextBlock(
                ProgrammeAsk(4, 2, goal = PlanGoal.Steady), TEST_EPOCH_DAY - 24,
                OUTLINED.copy(weeks = OUTLINED.weeks.take(4), outline = STEADY_OUTLINE), weekIndex = 3, doneByWeek = emptyList(), howItWent = emptyList(),
            ),
        )

        val GOOD_FILL = """{"focus":"Invented","sessions":[{"kind":"walk","minutes":30,"effort":"easy","what":"Walk"}],
            "target":{"minutes":60,"longest_minutes":40,"easier":false},"target_why":""}"""

        val GOOD_BLOCK = """{"block":{"phase":"steady","focus":"Invented","weeks":[
            {"minutes":60,"longest_minutes":30,"easier":false},{"minutes":70,"longest_minutes":30,"easier":false},
            {"minutes":80,"longest_minutes":40,"easier":false},{"minutes":50,"longest_minutes":30,"easier":true}]},"why":"Invented."}"""

        val FOUR_WEEK_REST = """{"title":"Invented","weeks":[
            {"focus":"a","sessions":[{"kind":"run","minutes":20,"effort":"push","what":"Run"}]},
            {"focus":"b","sessions":[{"kind":"run","minutes":20,"effort":"push","what":"Run"}]},
            {"focus":"c","sessions":[{"kind":"run","minutes":20,"effort":"push","what":"Run"}]},
            {"focus":"d","sessions":[{"kind":"run","minutes":20,"effort":"push","what":"Run"}]}],"why":"Invented."}"""
    }
}
