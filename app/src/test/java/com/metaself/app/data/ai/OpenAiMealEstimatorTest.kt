package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.ui.RecordingProblemLog
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

/**
 * The client, against a server running in this process.
 *
 * **No test in this project makes a real network call.** What is being proved here is this app's
 * own behaviour — the header it sends, the body it sends, and what it makes of a 401 — never the
 * provider's.
 */
class OpenAiMealEstimatorTest {

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
    fun `a good reply becomes a proposal`() = runTest {
        server.enqueue(MockResponse().setBody(reply(TWO_ITEMS)))

        val result = estimator().estimate("risotto with mozzarella")

        val proposal = (result as EstimateResult.Proposed).proposal
        assertThat(proposal.items).hasSize(2)
    }

    @Test
    fun `the key is sent as a bearer token and never in the body`() = runTest {
        server.enqueue(MockResponse().setBody(reply(TWO_ITEMS)))

        estimator().estimate("risotto")

        val sent = server.takeRequest()
        assertThat(sent.getHeader("Authorization")).isEqualTo("Bearer a-key")
        assertThat(sent.body.readUtf8()).doesNotContain("a-key")
    }

    @Test
    fun `the description is what gets sent`() = runTest {
        server.enqueue(MockResponse().setBody(reply(TWO_ITEMS)))

        estimator().estimate("ריזוטו עם מוצרלה")

        assertThat(server.takeRequest().body.readUtf8()).contains("ריזוטו עם מוצרלה")
    }

    @Test
    fun `with no key nothing is sent at all`() = runTest {
        val result = estimator(key = null).estimate("risotto")

        assertThat(result).isEqualTo(EstimateResult.NoKey)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `at the ceiling nothing is sent at all`() = runTest {
        val settings = FakeSettings(AiSettings(dailyCeiling = 2, usedToday = 2))

        val result = estimator(settings = settings).estimate("risotto")

        assertThat(result).isEqualTo(EstimateResult.CeilingReached)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a refusal carries the provider's own words`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"error":{"message":"Incorrect API key provided"}}"""),
        )

        val result = estimator().estimate("risotto")

        assertThat((result as EstimateResult.Refused).detail).contains("Incorrect API key")
    }

    @Test
    fun `a rate limit is a refusal, not a crash`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{}}"""))

        assertThat(estimator().estimate("risotto")).isInstanceOf(EstimateResult.Refused::class.java)
    }

    @Test
    fun `a dropped connection is unreachable, and the owner keeps his words`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        assertThat(estimator().estimate("risotto")).isEqualTo(EstimateResult.Unreachable)
    }

    /** D34: a reply that did not give amounts is asked again, once, naming what was missing. */
    @Test
    fun `a reply without amounts is asked again once, and the second answer is used`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setBody(reply(STEW_WITHOUT_AMOUNT)))
        server.enqueue(MockResponse().setBody(reply(TWO_ITEMS)))

        val result = estimator(settings = settings).estimate("stew")

        assertThat(result).isInstanceOf(EstimateResult.Proposed::class.java)
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(settings.calls).isEqualTo(2)
        server.takeRequest()
        assertThat(server.takeRequest().body.readUtf8()).contains("Stew")
    }

    @Test
    fun `asked twice and still without amounts, it fails in words the owner can act on`() = runTest {
        server.enqueue(MockResponse().setBody(reply(STEW_WITHOUT_AMOUNT)))
        server.enqueue(MockResponse().setBody(reply(STEW_WITHOUT_AMOUNT)))

        val result = estimator().estimate("stew")

        assertThat(result).isEqualTo(EstimateResult.AmountMissing(listOf("Stew")))
        assertThat(server.requestCount).isEqualTo(2)
    }

    /** The second ask is a second call, and is not made if it would pass the day's ceiling. */
    @Test
    fun `it is not asked again past the day's ceiling`() = runTest {
        val settings = FakeSettings(AiSettings(dailyCeiling = 1))
        server.enqueue(MockResponse().setBody(reply(STEW_WITHOUT_AMOUNT)))

        val result = estimator(settings = settings).estimate("stew")

        assertThat(result).isEqualTo(EstimateResult.AmountMissing(listOf("Stew")))
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `a call that happened is counted, even when it was refused`() = runTest {
        // A refusal still cost money and still spends the day's allowance.
        val settings = FakeSettings()
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{}}"""))

        estimator(settings = settings).estimate("risotto")

        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `a call that never left is not counted`() = runTest {
        val settings = FakeSettings()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        estimator(settings = settings).estimate("risotto")

        assertThat(settings.calls).isEqualTo(0)
    }

    /**
     * The answer kept for *Show the model's answer* is shown only (issue #1): the problem log gets
     * the one fixed line it always did, and neither the answer nor the names in it.
     */
    @Test
    fun `an unreadable answer is logged without the answer or its names`() = runTest {
        val content = """{"note":"","items":[{"name":"Quinoa","detail":"","amount":1,
            "unit":"cup","figures_per":"100","kcal":120,"protein_g":4,"carbs_g":21,"fat_g":2,
            "confidence":"LOW"}]}"""
        server.enqueue(MockResponse().setBody(reply(content)))
        val problems = RecordingProblemLog()

        val result = estimator(problems = problems).estimate("quinoa")

        assertThat((result as EstimateResult.Unreadable).answer).isEqualTo(content)
        assertThat(problems.recorded.map { it.kind to it.detail })
            .containsExactly("estimate unreadable" to result.why)
        assertThat(problems.recorded.single().detail).doesNotContain("Quinoa")
    }

    @Test
    fun `nothing unexpected escapes the seam and crashes the app`() = runTest {
        // The first Test button press crashed: no INTERNET permission, so Android threw a
        // SecurityException, which is not an IOException and so was not caught. The permission is
        // the fix for that bug. This is the fix for the class of bug — decision D8 says the app
        // must never refuse to work, and a crash is the loudest refusal there is.
        val throwing = OkHttpClient.Builder()
            .addInterceptor { throw SecurityException("Permission denied (missing INTERNET?)") }
            .build()

        val result = OpenAiMealEstimator(
            keys = FakeKeys("a-key"),
            settings = FakeSettings(),
            client = throwing,
            profiles = FakeRequestProfileStore(),
            baseUrl = server.url("/v1/chat/completions").toString(),
        ).estimate("risotto")

        assertThat(result).isInstanceOf(EstimateResult.Refused::class.java)
        assertThat((result as EstimateResult.Refused).detail).contains("Permission denied")
    }

    private fun estimator(
        key: String? = "a-key",
        settings: FakeSettings = FakeSettings(),
        problems: ProblemLog = ProblemLog.NONE,
    ) = OpenAiMealEstimator(
        keys = FakeKeys(key),
        settings = settings,
        client = OkHttpClient(),
        profiles = FakeRequestProfileStore(),
        problems = problems,
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private fun reply(content: String): String =
        """{"choices":[{"message":{"content":${kotlinx.serialization.json.JsonPrimitive(content)}}}]}"""

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
        const val STEW_WITHOUT_AMOUNT = """{"note":"","items":[
            {"name":"Stew","detail":"","amount":0,"unit":"g","figures_per":"100","kcal":130,
             "protein_g":7,"carbs_g":10,"fat_g":5,"confidence":"LOW"}]}"""

        const val TWO_ITEMS = """{"note":"","items":[
            {"name":"Beef burger","detail":"","amount":200,"unit":"g","figures_per":"100",
             "kcal":250,"protein_g":18,"carbs_g":0,"fat_g":20,"confidence":"MEDIUM"},
            {"name":"Hamburger bun","detail":"","amount":1,"unit":"bun","figures_per":"1",
             "kcal":150,"protein_g":5,"carbs_g":28,"fat_g":2,"confidence":"HIGH"}]}"""
    }
}
