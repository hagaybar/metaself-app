package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.Problem
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.HeldWeight
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.Nutrients
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
 * The reviewer, against a server running in this process (D54 §6).
 *
 * **No test in this project makes a real network call.** What is proved is this app's own part: one
 * call, counted by the shared rule, no retry, the closed set of failures, and a problem log that
 * never holds the food's name. The food is invented, and so are its figures.
 */
class OpenAiFoodReviewerTest {

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
    fun `a good reply becomes a review, from one call, counted once`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply(ONE_CHANGE)))

        val result = reviewer(settings = settings).review(REQUEST)

        val review = (result as ReviewResult.Proposed).review
        assertThat(review.perUnit!!.changes.single().figure).isEqualTo(Figure.FAT)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `the one food is what gets sent, and the key only in the header`() = runTest {
        server.enqueue(MockResponse().setBody(reply(ONE_CHANGE)))

        reviewer().review(REQUEST)

        val sent = server.takeRequest()
        assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer a-key")
        val body = sent.body.readUtf8()
        assertThat(body).contains(NAME)
        assertThat(body).contains("food_review")
        assertThat(body).doesNotContain("a-key")
    }

    @Test
    fun `with no key nothing is sent at all`() = runTest {
        val result = reviewer(key = null).review(REQUEST)

        assertThat(result).isEqualTo(ReviewResult.Failed(EstimateResult.NoKey))
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `at the ceiling nothing is sent at all`() = runTest {
        val settings = FakeSettings(AiSettings(dailyCeiling = 2, usedToday = 2))

        val result = reviewer(settings = settings).review(REQUEST)

        assertThat(result).isEqualTo(ReviewResult.Failed(EstimateResult.CeilingReached))
        assertThat(server.requestCount).isEqualTo(0)
        assertThat(settings.calls).isEqualTo(0)
    }

    /** No retry: D34's second ask is for missing amounts, and a review has none. */
    @Test
    fun `an unreadable answer is not asked again`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply("not the shape")))
        server.enqueue(MockResponse().setBody(reply(ONE_CHANGE)))

        val result = reviewer(settings = settings).review(REQUEST)

        assertThat((result as ReviewResult.Failed).failure)
            .isInstanceOf(EstimateResult.Unreadable::class.java)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `a refusal carries the provider's own words, and is counted`() = runTest {
        val settings = FakeSettings()
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )

        val result = reviewer(settings = settings).review(REQUEST)

        val failure = (result as ReviewResult.Failed).failure
        assertThat((failure as EstimateResult.Refused).detail).contains("Incorrect API key")
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `a dropped connection is unreachable, and not counted`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = reviewer(settings = settings).review(REQUEST)

        assertThat(result).isEqualTo(ReviewResult.Failed(EstimateResult.Unreachable))
        assertThat(settings.calls).isEqualTo(0)
    }

    /**
     * The log is written to be shared, and a food's name is what he eats (D54 §6). Every failure
     * that is logged is logged under its own kind, and the name is in none of them.
     */
    @Test
    fun `the problem log records each failure and never the food's name`() = runTest {
        val log = RecordingLog()
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{}}"""))
        server.enqueue(MockResponse().setBody(reply("""{"per_100g":"$NAME"}""")))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        repeat(3) { reviewer(problems = log).review(REQUEST) }

        assertThat(log.problems.map { it.kind })
            .containsExactly("review refused", "review unreadable", "review unreachable").inOrder()
        log.problems.forEach { problem ->
            assertThat(problem.kind).doesNotContain(NAME)
            assertThat(problem.detail).doesNotContain(NAME)
        }
    }

    @Test
    fun `a review that came back, and the failures with no fault to report, are not logged`() =
        runTest {
            val log = RecordingLog()
            server.enqueue(MockResponse().setBody(reply(ONE_CHANGE)))

            reviewer(problems = log).review(REQUEST)
            reviewer(key = null, problems = log).review(REQUEST)
            reviewer(
                settings = FakeSettings(AiSettings(dailyCeiling = 1, usedToday = 1)),
                problems = log,
            ).review(REQUEST)

            assertThat(log.problems).isEmpty()
        }

    @Test
    fun `nothing unexpected escapes the seam and crashes the app`() = runTest {
        val throwing = OkHttpClient.Builder()
            .addInterceptor { throw SecurityException("Permission denied (missing INTERNET?)") }
            .build()

        val result = OpenAiFoodReviewer(
            keys = FakeKeys("a-key"),
            settings = FakeSettings(),
            client = throwing,
            baseUrl = server.url("/v1/chat/completions").toString(),
        ).review(REQUEST)

        val failure = (result as ReviewResult.Failed).failure
        assertThat((failure as EstimateResult.Refused).detail).contains("Permission denied")
    }

    private fun reviewer(
        key: String? = "a-key",
        settings: FakeSettings = FakeSettings(),
        problems: ProblemLog = ProblemLog.NONE,
    ) = OpenAiFoodReviewer(
        keys = FakeKeys(key),
        settings = settings,
        client = OkHttpClient(),
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
        /** Invented, and distinctive, so that finding it anywhere in the log means it leaked. */
        const val NAME = "Quillberry crumble"

        val REQUEST = ReviewRequest(
            process = ReviewProcess.EXISTING_FOOD,
            name = NAME,
            brand = "",
            per100g = HeldGroup(Nutrients(480.0, 7.0, 62.0, 22.0), Source.LABEL, null),
            unitName = "slice",
            perUnit = HeldGroup(Nutrients(90.0, 1.0, 12.0, 1.0), Source.TYPED, null),
            gramsPerUnit = HeldWeight(18.0, Source.TYPED),
        )

        const val ONE_CHANGE = """{"per_100g":null,"note":"","per_unit":{"kcal":90,"protein_g":1,
            "carbs_g":12,"fat_g":4,"kcal_reason":"","protein_reason":"","carbs_reason":"",
            "fat_reason":"18 g at 22 g of fat per 100 g holds about 4 g","confidence":"MEDIUM"}}"""
    }
}
