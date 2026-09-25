package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.StepResult
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/** How a conversation's replies are read (D58 §4, §12). Every meal and answer here is invented. */
class ConversationResponseTest {

    @Test
    fun `a first reply with no question is today's estimate`() {
        val result = ConversationResponse.opening(reply(opening(needs = "false", items = "[$PASTA]")))

        val estimate = (result as StepResult.Estimate).result as EstimateResult.Proposed
        assertThat(estimate.proposal.items.single().name).isEqualTo("Pasta")
    }

    @Test
    fun `a first reply with no question and an item without an amount is D34's case`() {
        val result = ConversationResponse.opening(reply(opening(needs = "false", items = "[$PASTA_NO_AMOUNT]")))

        assertThat((result as StepResult.Estimate).result).isInstanceOf(EstimateResult.AmountMissing::class.java)
    }

    @Test
    fun `a first reply that asks is a question, its items ignored`() {
        val result = ConversationResponse.opening(
            reply(opening(needs = "true", planned = 3, question = QUESTION, items = "[$PASTA]")),
        )

        val ask = result as StepResult.Ask
        assertThat(ask.question.text).isEqualTo("How big was the container?")
        assertThat(ask.question.options)
            .containsExactly("Small box", "Standard box", "Large box", "Not sure").inOrder()
        assertThat(ask.planned).isEqualTo(3)
    }

    @Test
    fun `answers are trimmed, and blanks and repeats are dropped`() {
        val question = """{"text":" Which sauce? ","options":[" Tomato ","","tomato","Cream","Not sure"]}"""

        val ask = ConversationResponse.step(reply(step("true", 2, question))) as StepResult.Ask

        assertThat(ask.question.text).isEqualTo("Which sauce?")
        assertThat(ask.question.options).containsExactly("Tomato", "Cream", "Not sure").inOrder()
    }

    @Test
    fun `without an answer meaning not sure, the phone adds one`() {
        val question = """{"text":"Which sauce?","options":["Tomato","Cream"]}"""

        val ask = ConversationResponse.step(reply(step("true", 2, question))) as StepResult.Ask

        assertThat(ask.question.options).containsExactly("Tomato", "Cream", "Not sure").inOrder()
    }

    @Test
    fun `an answer meaning not sure in another language is kept, not doubled`() {
        val question = """{"text":"איזה רוטב?","options":["עגבניות","שמנת","לא בטוח"]}"""

        val ask = ConversationResponse.step(reply(step("true", 2, question))) as StepResult.Ask

        assertThat(ask.question.options).containsExactly("עגבניות", "שמנת", "לא בטוח").inOrder()
    }

    /** One real answer and the phone's *Not sure* make a question — never a crash (§4.1). */
    @Test
    fun `one ready-made answer is enough, with the phone's not sure beside it`() {
        val question = """{"text":"Was there dressing?","options":["Yes"]}"""

        val ask = ConversationResponse.step(reply(step("true", 1, question))) as StepResult.Ask

        assertThat(ask.question.options).containsExactly("Yes", "Not sure").inOrder()
    }

    @Test
    fun `more than five answers keep the first five and not sure`() {
        val question = """{"text":"How much oil?","options":["A","B","C","D","E","F","G","Not sure"]}"""

        val ask = ConversationResponse.step(reply(step("true", 2, question))) as StepResult.Ask

        assertThat(ask.question.options).containsExactly("A", "B", "C", "D", "E", "Not sure").inOrder()
    }

    @Test
    fun `the plan is clamped to one to five`() {
        assertThat((ConversationResponse.step(reply(step("true", 0, QUESTION))) as StepResult.Ask).planned)
            .isEqualTo(1)
        assertThat((ConversationResponse.step(reply(step("true", 9, QUESTION))) as StepResult.Ask).planned)
            .isEqualTo(5)
    }

    @Test
    fun `a step with no more questions is enough, whatever its empty question says`() {
        assertThat(ConversationResponse.step(reply(step("false", 0, EMPTY_QUESTION))))
            .isEqualTo(StepResult.Enough)
    }

    @Test
    fun `a step reply saying only that no more are needed is enough`() {
        assertThat(ConversationResponse.step(reply("""{"needs_questions":false}""")))
            .isEqualTo(StepResult.Enough)
    }

    @Test
    fun `a first reply without needs_questions but with items is an estimate`() {
        val result = ConversationResponse.opening(reply("""{"items":[$PASTA],"note":""}"""))

        assertThat(result).isInstanceOf(StepResult.Estimate::class.java)
    }

    @Test
    fun `asking with no question text, or no answer at all, is unreadable`() {
        listOf(
            step("true", 2, EMPTY_QUESTION),
            step("true", 2, """{"text":"Which?","options":["Not sure"]}"""),
            step("true", 2, """{"text":"Which?","options":[]}"""),
        ).forEach { content ->
            val failed = ConversationResponse.step(reply(content)) as StepResult.Failed
            assertThat(failed.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
            // The answer as it came, for Show the model's answer.
            assertThat((failed.failure as EstimateResult.Unreadable).answer).isEqualTo(content)
        }
    }

    @Test
    fun `a reply that is not the shape asked for is unreadable`() {
        listOf("not json", reply("not json"), reply("""{"total_planned":2}""")).forEach { body ->
            val failed = ConversationResponse.step(body) as StepResult.Failed
            assertThat(failed.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        }
    }

    @Test
    fun `the final analysis is read by today's reader, its working ignored and its answer kept`() {
        val content = """{"plate":"a counter's box, two ladles","items":[$PASTA],"note":"The sauce."}"""

        val result = ConversationResponse.final(reply(content)) as EstimateResult.Proposed

        assertThat(result.proposal.items.single().amount).isEqualTo(350.0)
        assertThat(result.proposal.items.single().unit).isEqualTo("g")
        assertThat(result.proposal.note).isEqualTo("The sauce.")
        // Always kept after a conversation, for Show the model's answer (D58 §5.1).
        assertThat(result.proposal.answer).isEqualTo(content)
    }

    @Test
    fun `a final item the reader cannot use is named, as today`() {
        val content = """{"plate":"","items":[$PASTA,$BROKEN],"note":""}"""

        val result = ConversationResponse.final(reply(content)) as EstimateResult.Proposed

        assertThat(result.proposal.dropped).containsExactly("Sauce")
    }

    @Test
    fun `a final item without an amount is D34's case`() {
        val content = """{"plate":"","items":[$PASTA_NO_AMOUNT],"note":""}"""

        assertThat(ConversationResponse.final(reply(content)))
            .isEqualTo(EstimateResult.AmountMissing(listOf("Pasta")))
    }

    private fun reply(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private fun opening(needs: String, planned: Int = 0, question: String = EMPTY_QUESTION, items: String = "[]") =
        """{"needs_questions":$needs,"total_planned":$planned,"question":$question,"items":$items,"note":""}"""

    private fun step(needs: String, planned: Int, question: String) =
        """{"needs_questions":$needs,"total_planned":$planned,"question":$question}"""

    private companion object {
        const val QUESTION =
            """{"text":"How big was the container?","options":["Small box","Standard box","Large box","Not sure"]}"""
        const val EMPTY_QUESTION = """{"text":"","options":[]}"""
        const val PASTA = """{"name":"Pasta","detail":"two ladles","amount":350,"unit":"g",""" +
            """"figures_per":"100","kcal":220,"protein_g":6.3,"carbs_g":25.7,"fat_g":10.3,"confidence":"MEDIUM"}"""
        const val PASTA_NO_AMOUNT = """{"name":"Pasta","detail":"","amount":0,"unit":"g",""" +
            """"figures_per":"100","kcal":220,"protein_g":6.3,"carbs_g":25.7,"fat_g":10.3,"confidence":"MEDIUM"}"""
        const val BROKEN = """{"name":"Sauce","detail":"","amount":2,"unit":"g",""" +
            """"figures_per":"1","kcal":90,"protein_g":0,"carbs_g":1,"fat_g":10,"confidence":"LOW"}"""
    }
}
