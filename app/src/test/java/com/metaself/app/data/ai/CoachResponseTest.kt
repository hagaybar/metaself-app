package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/** D161: a coach's answer as the phone reads it. Every word is invented. */
class CoachResponseTest {

    private fun body(content: String) = """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    private fun turn(content: String) = (CoachResponse.parse(body(content), "a-model") as TrainerReply.Answered<CoachTurn>).value

    @Test
    fun `an answer is its words and what to remember, trimmed`() {
        val reply = CoachResponse.parse(
            body("""{"open":[],"reply":"  Invented reply.  ","remember":[{"group":"life","text":" Invented fact. "}]}"""),
            "a-model",
        ) as TrainerReply.Answered

        assertThat(reply.model).isEqualTo("a-model")
        assertThat(reply.value).isEqualTo(CoachTurn(emptyList(), "Invented reply.", listOf(RememberedFact("life", "Invented fact."))))
    }

    @Test
    fun `an opening is its drawers, as named`() {
        val read = turn("""{"open":[{"drawer":"meals","from":"2026-09-01","to":"2026-09-03","words":""}],"reply":"","remember":[]}""")

        assertThat(read.open).containsExactly(DrawerAsk("meals", "2026-09-01", "2026-09-03", ""))
        assertThat(read.reply).isEmpty()
    }

    /** It goes back to the model as a bad drawer; the phone does not fail for it. */
    @Test
    fun `a malformed drawer reads as a nameless one, and a malformed fact is dropped`() {
        val read = turn("""{"open":[3,{"drawer":4}],"reply":"Invented.","remember":[5,{"group":"life"}]}""")

        assertThat(read.open).containsExactly(DrawerAsk("", "", "", ""), DrawerAsk("", "", "", ""))
        assertThat(read.remember).containsExactly(RememberedFact("life", ""))
    }

    @Test
    fun `missing or mistyped parts read as empty, for the look to judge`() {
        assertThat(turn("{}")).isEqualTo(CoachTurn(emptyList(), "", emptyList()))
        assertThat(turn("""{"open":"meals","reply":3,"remember":false}""")).isEqualTo(CoachTurn(emptyList(), "", emptyList()))
    }

    @Test
    fun `what is no JSON object is unreadable`() {
        listOf(body("not json"), body("[1]"), "not a body").forEach { text ->
            val failed = CoachResponse.parse(text, "a-model") as TrainerReply.Failed
            assertThat(failed.failure).isInstanceOf(EstimateResult.Unreadable::class.java)
        }
    }
}
