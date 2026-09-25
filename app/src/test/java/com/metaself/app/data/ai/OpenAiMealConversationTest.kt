package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.ai.Asked
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.StepResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A conversation's requests against a server running in this process (D58 §3, §7, §9). **No test in
 * this project makes a real network call.** Every meal, question and answer here is invented.
 */
class OpenAiMealConversationTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    /** The worked example's shape: three questions, four requests, each counted. */
    @Test
    fun `a three-question conversation is four requests, each carrying everything said so far`() = runTest {
        val settings = FakeSettings(AiSettings(model = "gpt-6-luna"))
        val profiles = FakeRequestProfileStore()
        val asker = asker(settings, profiles)
        server.enqueue(answer(opening(asking(Q1))))
        server.enqueue(answer(step(asking(Q2))))
        server.enqueue(answer(step(asking(Q3))))
        server.enqueue(answer(FINAL))

        val first = asker.open(MEAL) as StepResult.Ask
        val one = listOf(Asked(first.question.text, "Standard box"))
        val second = asker.next(MEAL, one, cap = first.planned) as StepResult.Ask
        val two = one + Asked(second.question.text, "Thick and creamy")
        val third = asker.next(MEAL, two, cap = first.planned) as StepResult.Ask
        val three = two + Asked(third.question.text, "Not sure")
        val result = asker.finish(MEAL, three)

        assertThat((result as EstimateResult.Proposed).proposal.items.map { it.name })
            .containsExactly("Pasta", "Bread roll").inOrder()
        assertThat(server.requestCount).isEqualTo(4)
        assertThat(settings.calls).isEqualTo(4)

        val bodies = (1..4).map { sent() }
        assertThat(bodies.map { messages(it).size }).containsExactly(2, 5, 7, 8).inOrder()
        assertThat(messages(bodies[2]).map { it.second }).containsAtLeast(
            "How big was the container?", "Standard box", "What was the sauce like?", "Thick and creamy",
        ).inOrder()
        // Everyday requests are sent as the profile says; only the final analysis asks for more.
        assertThat(bodies.take(3).map { it["reasoning_effort"]!!.jsonPrimitive.content })
            .containsExactly("low", "low", "low")
        assertThat(bodies[3]["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("high")
        // What the steps taught is everyday; what the final taught is only its deep level.
        assertThat(profiles.remembered.value).containsKey("gpt-6-luna")
        assertThat(profiles.deep.value["gpt-6-luna"]).isEqualTo(DeepLevel("high"))
    }

    @Test
    fun `a meal that needs no question is one request, as today`() = runTest {
        val settings = FakeSettings()
        server.enqueue(answer(opening(NO_QUESTION, items = "[$APPLE]")))

        val result = asker(settings).open("an apple")

        assertThat(((result as StepResult.Estimate).result as EstimateResult.Proposed).proposal.items.single().name)
            .isEqualTo("Apple")
        assertThat(settings.calls).isEqualTo(1)
    }

    @Test
    fun `best guess from the offer is the first request and the final analysis`() = runTest {
        val settings = FakeSettings()
        val asker = asker(settings)
        server.enqueue(answer(opening(asking(Q1))))
        server.enqueue(answer(FINAL))

        asker.open(MEAL)
        val result = asker.finish(MEAL, emptyList())

        assertThat(result).isInstanceOf(EstimateResult.Proposed::class.java)
        assertThat(settings.calls).isEqualTo(2)
        sent()
        assertThat(messages(sent()).map { it.first }).containsExactly("system", "user").inOrder()
    }

    @Test
    fun `a step with no more questions is enough`() = runTest {
        server.enqueue(answer(step(NO_QUESTION)))

        assertThat(asker().next(MEAL, listOf(Asked("How big?", "Large")), cap = 3)).isEqualTo(StepResult.Enough)
    }

    @Test
    fun `an unreadable step is a failure with the answer kept, and trying again is one more request`() = runTest {
        val settings = FakeSettings()
        val asker = asker(settings)
        server.enqueue(answer("not the shape"))
        server.enqueue(answer(step(asking(Q2))))

        val failed = asker.next(MEAL, listOf(Asked("How big?", "Large")), cap = 3) as StepResult.Failed
        assertThat((failed.failure as EstimateResult.Unreadable).answer).isEqualTo("not the shape")

        assertThat(asker.next(MEAL, listOf(Asked("How big?", "Large")), cap = 3))
            .isInstanceOf(StepResult.Ask::class.java)
        assertThat(settings.calls).isEqualTo(2)
    }

    @Test
    fun `a refusal mid-conversation carries the provider's words`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"Rate limit reached"}}"""))

        val failed = asker().next(MEAL, listOf(Asked("How big?", "Large")), cap = 3) as StepResult.Failed

        assertThat(failed.failure).isEqualTo(EstimateResult.Refused("Rate limit reached"))
    }

    @Test
    fun `at the ceiling nothing is sent`() = runTest {
        val settings = FakeSettings(AiSettings(dailyCeiling = 2, usedToday = 2))

        val failed = asker(settings).next(MEAL, emptyList(), cap = 3) as StepResult.Failed

        assertThat(failed.failure).isEqualTo(EstimateResult.CeilingReached)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `the remaining allowance is read from the day's count`() = runTest {
        assertThat(asker(FakeSettings(AiSettings(dailyCeiling = 5, usedToday = 3))).remainingToday()).isEqualTo(2)
    }

    /** D34 on the final analysis: asked once more, naming the items, with the same effort. */
    @Test
    fun `a final analysis without an amount is asked once more, naming the item`() = runTest {
        val settings = FakeSettings(AiSettings(model = "gpt-6-luna"))
        server.enqueue(answer(FINAL_NO_AMOUNT))
        server.enqueue(answer(FINAL))

        val result = asker(settings).finish(MEAL, emptyList())

        assertThat(result).isInstanceOf(EstimateResult.Proposed::class.java)
        sent()
        val again = sent()
        assertThat(messages(again).last().second).contains("Pasta")
        assertThat(again["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("high")
        assertThat(settings.calls).isEqualTo(2)
    }

    /** §12.4: D34's second ask is not reserved; with nothing left, D34's refusal as today. */
    @Test
    fun `with nothing left, a final analysis without an amount is not asked again`() = runTest {
        val settings = FakeSettings(AiSettings(dailyCeiling = 1, usedToday = 0))
        server.enqueue(answer(FINAL_NO_AMOUNT))

        val result = asker(settings).finish(MEAL, emptyList(), deep = false)

        assertThat(result).isEqualTo(EstimateResult.AmountMissing(listOf("Pasta")))
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `the day's last request is sent as an everyday one`() = runTest {
        server.enqueue(answer(FINAL))

        asker(FakeSettings(AiSettings(model = "gpt-6-luna"))).finish(MEAL, emptyList(), deep = false)

        assertThat(sent()["reasoning_effort"]!!.jsonPrimitive.content).isEqualTo("low")
    }

    @Test
    fun `his added sentence goes to the final analysis as his own words`() = runTest {
        server.enqueue(answer(FINAL))

        asker().finish(MEAL, emptyList(), moreDetail = "no cheese after all")

        assertThat(messages(sent()).last()).isEqualTo("user" to "More detail: no cheese after all")
    }

    /** §9: the log is written to be shared; a question quotes the meal. */
    @Test
    fun `the problem log records kinds, never the meal, a question or an answer`() = runTest {
        val log = RecordingLog()
        val asker = asker(problems = log)
        server.enqueue(answer("not the shape"))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"Server busy"}}"""))
        server.enqueue(answer("""{"plate":"","items":[],"note":""}"""))

        asker.open(MEAL)
        asker.next(MEAL, listOf(Asked("How big was the container?", "Standard box")), cap = 3)
        asker.finish(MEAL, listOf(Asked("How big was the container?", "Standard box")))

        assertThat(log.kinds).containsExactly(
            "conversation opening unreadable", "conversation step refused", "final analysis unreadable",
        ).inOrder()
        val everything = log.lines.joinToString(" ")
        listOf("pasta", "container", "Standard box", "mushroom").forEach {
            assertThat(everything.lowercase()).doesNotContain(it.lowercase())
        }
    }

    private fun asker(
        settings: FakeSettings = FakeSettings(),
        profiles: RequestProfileStore = FakeRequestProfileStore(),
        problems: ProblemLog = ProblemLog.NONE,
    ) = OpenAiMealConversation(
        keys = FakeKeys("a-key"),
        settings = settings,
        client = OkHttpClient(),
        profiles = profiles,
        problems = problems,
        baseUrl = server.url("/v1/chat/completions").toString(),
    )

    private fun answer(content: String) =
        MockResponse().setBody("""{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}""")

    private fun sent(): JsonObject = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    private fun messages(body: JsonObject): List<Pair<String, String>> = body["messages"]!!.jsonArray.map {
        it.jsonObject["role"]!!.jsonPrimitive.content to it.jsonObject["content"]!!.jsonPrimitive.content
    }

    private fun asking(question: String) = question

    private fun opening(question: String, items: String = "[]") =
        """{"needs_questions":${question != NO_QUESTION},"total_planned":${if (question == NO_QUESTION) 0 else 3},""" +
            """"question":$question,"items":$items,"note":""}"""

    private fun step(question: String) =
        """{"needs_questions":${question != NO_QUESTION},"total_planned":3,"question":$question}"""

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

    private class RecordingLog : ProblemLog {
        val lines = mutableListOf<String>()
        val kinds = mutableListOf<String>()
        override fun record(kind: String, detail: String) {
            kinds += kind
            lines += "$kind $detail"
        }
        override fun recent() = emptyList<com.metaself.app.data.diagnostics.Problem>()
        override fun clear() = Unit
    }

    private companion object {
        const val MEAL = "pasta with a mushroom sauce from a takeaway counter, and a bread roll"
        const val NO_QUESTION = """{"text":"","options":[]}"""
        const val Q1 = """{"text":"How big was the container?","options":["Small box","Standard box","Not sure"]}"""
        const val Q2 = """{"text":"What was the sauce like?","options":["Thin","Thick and creamy","Not sure"]}"""
        const val Q3 = """{"text":"How much cheese was on top?","options":["A sprinkle","A layer","Not sure"]}"""
        const val APPLE = """{"name":"Apple","detail":"","amount":1,"unit":"apple","figures_per":"1",""" +
            """"kcal":80,"protein_g":0,"carbs_g":20,"fat_g":0,"confidence":"MEDIUM"}"""
        const val PASTA = """{"name":"Pasta","detail":"two ladles","amount":350,"unit":"g","figures_per":"100",""" +
            """"kcal":220,"protein_g":6.3,"carbs_g":25.7,"fat_g":10.3,"confidence":"MEDIUM"}"""
        const val ROLL = """{"name":"Bread roll","detail":"plain","amount":1,"unit":"roll","figures_per":"1",""" +
            """"kcal":150,"protein_g":5,"carbs_g":28,"fat_g":2,"confidence":"MEDIUM"}"""
        const val FINAL = """{"plate":"a counter's box","items":[$PASTA,$ROLL],"note":""}"""
        val FINAL_NO_AMOUNT = FINAL.replace("\"amount\":350", "\"amount\":0")
    }
}
