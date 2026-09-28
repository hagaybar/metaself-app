package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/** The trainer's replies, read strictly (D86, D87). Every title, step and sentence is invented. */
class TrainerResponseTest {

    private val goodPlan = """{"title":"Steady walk with two climbs","steps":[
        {"from_minute":0,"to_minute":10,"what":"Warm up","how":"easy pace"},
        {"from_minute":10,"to_minute":35,"what":"Two climbs","how":"zone 3"},
        {"from_minute":35,"to_minute":45,"what":"Cool down","how":""}],"why":"Invented reason."}"""

    private val goodFeedback = """{"headline":"A steady session","against_plan":"As planned.","numbers":"Invented.",
        "next_time":"Invented.","this_week":"Invented.","plan_followed":"yes"}"""

    @Test
    fun `a plan reply becomes a plan, with the model that gave it`() {
        val reply = TrainerResponse.parsePlan(reply(goodPlan), "a-model") as TrainerReply.Answered

        assertThat(reply.model).isEqualTo("a-model")
        assertThat(reply.value.title).isEqualTo("Steady walk with two climbs")
        assertThat(reply.value.steps.map { it.fromMinute to it.toMinute }).containsExactly(0 to 10, 10 to 35, 35 to 45).inOrder()
        assertThat(reply.value.steps.last().how).isEmpty()
    }

    @Test
    fun `fewer than three or more than six steps is unreadable`() {
        assertUnreadable(TrainerResponse.parsePlan(reply(planOf(steps = 2)), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(planOf(steps = 7)), "a-model"))
        assertThat(TrainerResponse.parsePlan(reply(planOf(steps = 3)), "a-model")).isInstanceOf(TrainerReply.Answered::class.java)
        assertThat(TrainerResponse.parsePlan(reply(planOf(steps = 6)), "a-model")).isInstanceOf(TrainerReply.Answered::class.java)
    }

    /** [steps] steps of five minutes each, back to back. Invented. */
    private fun planOf(steps: Int): String =
        """{"title":"t","steps":[""" +
            (0 until steps).joinToString(",") { """{"from_minute":${it * 5},"to_minute":${it * 5 + 5},"what":"w","how":""}""" } +
            """],"why":"y"}"""

    @Test
    fun `a step that ends before it starts, goes backwards, or says nothing is unreadable`() {
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"to_minute\":10", "\"to_minute\":0")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"from_minute\":35", "\"from_minute\":5")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Warm up\"", "\" \"")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Invented reason.\"", "\"\"")), "a-model"))
    }

    @Test
    fun `a feedback reply becomes feedback`() {
        val reply = TrainerResponse.parseFeedback(reply(goodFeedback), "a-model") as TrainerReply.Answered

        assertThat(reply.value.headline).isEqualTo("A steady session")
        assertThat(reply.value.followed).isEqualTo(PlanFollowed.YES)
    }

    @Test
    fun `feedback missing a part, or with an unknown judgement, is unreadable`() {
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"A steady session\"", "\"\"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"yes\"", "\"maybe\"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback("not json", "a-model"))
    }

    /** With no plan, "against the plan" may be empty. */
    @Test
    fun `with no plan the against-plan part may be empty`() {
        val noPlan = goodFeedback.replace("\"As planned.\"", "\"\"").replace("\"yes\"", "\"no_plan\"")

        assertThat(TrainerResponse.parseFeedback(reply(noPlan), "a-model")).isInstanceOf(TrainerReply.Answered::class.java)
    }

    /** Design question 3: what is stored reads back as exactly what was read. */
    @Test
    fun `a plan and feedback survive being written for storage`() {
        val plan = (TrainerResponse.parsePlan(reply(goodPlan), "m") as TrainerReply.Answered).value
        val feedback = (TrainerResponse.parseFeedback(reply(goodFeedback), "m") as TrainerReply.Answered).value

        assertThat(TrainerResponse.readPlan(TrainerResponse.encodePlan(plan))).isEqualTo(plan)
        assertThat(TrainerResponse.readFeedback(TrainerResponse.encodeFeedback(feedback))).isEqualTo(feedback)
        assertThat(TrainerResponse.readPlan("garbled")).isNull()
        assertThat(TrainerResponse.readFeedback(null)).isNull()
    }

    private fun assertUnreadable(reply: TrainerReply<*>) {
        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    private fun reply(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""
}
