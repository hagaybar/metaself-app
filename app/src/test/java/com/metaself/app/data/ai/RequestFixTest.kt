package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/** What is sent instead after each refusal (D57 §3). Pure, so JUnit 5. */
class RequestFixTest {

    private val deterministic = RequestProfile.DETERMINISTIC
    private val reasoning = RequestProfile.REASONING

    @Test
    fun `a refused temperature is dropped for a low reasoning effort`() {
        assertThat(fix(deterministic, Refusals.TEMPERATURE).first())
            .isEqualTo(RequestProfile(temperature = false, reasoningEffort = "low"))
    }

    @Test
    fun `a refused temperature keeps a reasoning effort already sent`() {
        val sent = RequestProfile(temperature = true, reasoningEffort = "high")

        assertThat(fix(sent, Refusals.TEMPERATURE).first())
            .isEqualTo(RequestProfile(temperature = false, reasoningEffort = "high"))
    }

    @Test
    fun `a temperature refusal of a request that sent none has nothing to fix`() {
        assertThat(fix(reasoning, Refusals.TEMPERATURE)).isEmpty()
    }

    @Test
    fun `a refused reasoning value moves to the lowest accepted at or above low`() {
        assertThat(fix(reasoning, Refusals.EFFORT_LOW_WITH_LIST).map { it.reasoningEffort })
            .containsExactly("medium", "high").inOrder()
    }

    @Test
    fun `with nothing accepted at or above low, the highest below it`() {
        val body = Refusals.EFFORT_LOW_WITH_LIST.replace("'medium' and 'high'", "'none' and 'minimal'")

        assertThat(fix(reasoning, body).map { it.reasoningEffort }).containsExactly("minimal", "none").inOrder()
    }

    @Test
    fun `a refused reasoning value with no list steps up from the one sent`() {
        assertThat(fix(reasoning, Refusals.effortWithoutList("low")).map { it.reasoningEffort })
            .containsExactly("medium", "high").inOrder()
        val high = reasoning.copy(reasoningEffort = "high")
        assertThat(fix(high, Refusals.effortWithoutList("high"))).isEmpty()
    }

    @Test
    fun `a refused reasoning parameter is dropped and temperature 0 comes back, else both go`() {
        assertThat(fix(reasoning, Refusals.EFFORT_PARAMETER)).containsExactly(
            RequestProfile(temperature = true, reasoningEffort = null),
            RequestProfile(temperature = false, reasoningEffort = null),
        ).inOrder()
    }

    @Test
    fun `a refused strict format falls back to the schema in the instructions`() {
        assertThat(fix(reasoning, Refusals.RESPONSE_FORMAT))
            .containsExactly(reasoning.copy(strictFormat = false))
        assertThat(fix(reasoning.copy(strictFormat = false), Refusals.RESPONSE_FORMAT)).isEmpty()
    }

    @Test
    fun `a token limit, which is never sent, and an unknown refusal have nothing to fix`() {
        assertThat(fix(deterministic, Refusals.MAX_TOKENS)).isEmpty()
        assertThat(fix(reasoning, Refusals.UNKNOWN)).isEmpty()
    }

    @Test
    fun `no fix is ever the profile that was refused`() {
        listOf(
            Refusals.TEMPERATURE, Refusals.EFFORT_LOW_WITH_LIST, Refusals.EFFORT_PARAMETER,
            Refusals.RESPONSE_FORMAT, Refusals.effortWithoutList("low"),
        ).forEach { body ->
            listOf(deterministic, reasoning).forEach { sent ->
                assertThat(fix(sent, body)).doesNotContain(sent)
            }
        }
    }

    private fun fix(sent: RequestProfile, body: String): List<RequestProfile> =
        RequestFix.candidates(sent, ProviderRefusal.parse(body)!!)
}
