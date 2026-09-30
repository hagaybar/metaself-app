package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/** The trainer's replies, read strictly (D86, D87). Every title, step and sentence is invented. */
class TrainerResponseTest {

    private val goodPlan = """{"title":"Steady walk with two climbs","steps":[
        {"from_minute":0,"to_minute":10,"what":"Warm up","how":"easy pace"},
        {"from_minute":10,"to_minute":35,"what":"Two climbs","how":"zone 3"},
        {"from_minute":35,"to_minute":45,"what":"Cool down","how":""}],"why":"Invented reason."}"""

    private val goodFeedback = """{"headline":"A steady session","note":"Invented note.","plan_followed":"yes"}"""

    /** D87's four parts, as feedback stored before D108 holds them. */
    private val storedFourParts = """{"headline":"An old headline","against_plan":"As planned.","numbers":"Invented numbers.",
        "next_time":"Invented next.","this_week":"Invented week.","plan_followed":"yes"}"""

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
    fun `a feedback reply becomes a headline and a note`() {
        val reply = TrainerResponse.parseFeedback(reply(goodFeedback), "a-model") as TrainerReply.Answered

        assertThat(reply.value.headline).isEqualTo("A steady session")
        assertThat(reply.value.note).isEqualTo("Invented note.")
        assertThat(reply.value.followed).isEqualTo(PlanFollowed.YES)
        assertThat(listOf(reply.value.againstPlan, reply.value.numbers, reply.value.nextTime, reply.value.thisWeek)).containsExactly("", "", "", "")
    }

    /** D108: a new reply needs its headline and its note. */
    @Test
    fun `feedback missing its headline or note, or with an unknown judgement, is unreadable`() {
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"A steady session\"", "\"\"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"Invented note.\"", "\" \"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"yes\"", "\"maybe\"")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback("not json", "a-model"))
    }

    /** D108: a reply in the four-part shape was not asked for; only stored feedback is read in it. */
    @Test
    fun `a four-part reply is unreadable, but four-part feedback already stored still reads`() {
        assertUnreadable(TrainerResponse.parseFeedback(reply(storedFourParts), "a-model"))

        val stored = TrainerResponse.readFeedback(storedFourParts)!!
        assertThat(stored).isEqualTo(
            Feedback("An old headline", "As planned.", "Invented numbers.", "Invented next.", "Invented week.", PlanFollowed.YES),
        )
        assertThat(stored.note).isNull()
    }

    /** A text part must be a JSON string: null, a number or a boolean is not "null", "7" or "true". */
    @Test
    fun `a text part that is not a string is unreadable`() {
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"A steady session\"", "null")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"Invented note.\"", "7")), "a-model"))
        assertUnreadable(TrainerResponse.parseFeedback(reply(goodFeedback.replace("\"Invented note.\"", "true")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Steady walk with two climbs\"", "42")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Invented reason.\"", "null")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"Warm up\"", "false")), "a-model"))
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"zone 3\"", "3")), "a-model"))
        assertThat(TrainerResponse.readFeedback(goodFeedback.replace("\"yes\"", "null"))).isNull()
        assertThat(TrainerResponse.readFeedback(storedFourParts.replace("\"Invented numbers.\"", "7"))).isNull()
    }

    /** A minute is a JSON number: "10" in quotes is not one. */
    @Test
    fun `a minute given as text is unreadable`() {
        assertUnreadable(TrainerResponse.parsePlan(reply(goodPlan.replace("\"to_minute\":10", "\"to_minute\":\"10\"")), "a-model"))
    }

    /** Stored four-part feedback with no plan may have an empty "against the plan", as it always could. */
    @Test
    fun `stored four-part feedback with no plan may have an empty against-plan part`() {
        val noPlan = storedFourParts.replace("\"As planned.\"", "\"\"").replace("\"yes\"", "\"no_plan\"")

        assertThat(TrainerResponse.readFeedback(noPlan)).isNotNull()
    }

    /** Design question 3: what is stored reads back as exactly what was read. */
    @Test
    fun `a plan and feedback survive being written for storage`() {
        val plan = (TrainerResponse.parsePlan(reply(goodPlan), "m") as TrainerReply.Answered).value
        val feedback = (TrainerResponse.parseFeedback(reply(goodFeedback), "m") as TrainerReply.Answered).value

        assertThat(TrainerResponse.readPlan(TrainerResponse.encodePlan(plan))).isEqualTo(plan)
        assertThat(TrainerResponse.readFeedback(TrainerResponse.encodeFeedback(feedback))).isEqualTo(feedback)
        assertThat(TrainerResponse.encodeFeedback(feedback)).isEqualTo(
            """{"headline":"A steady session","note":"Invented note.","plan_followed":"yes"}""",
        )
        val old = TrainerResponse.readFeedback(storedFourParts)!!
        assertThat(TrainerResponse.readFeedback(TrainerResponse.encodeFeedback(old))).isEqualTo(old)
        assertThat(TrainerResponse.readPlan("garbled")).isNull()
        assertThat(TrainerResponse.readFeedback(null)).isNull()
    }

    private fun assertUnreadable(reply: TrainerReply<*>) {
        assertThat((reply as TrainerReply.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
    }

    private fun reply(content: String): String =
        """{"choices":[{"message":{"content":${JsonPrimitive(content)}}}]}"""

    // --- D94, D97. Every title, focus and line is invented. -----------------------------------------

    private fun session(kind: String = "walk", minutes: Int = 30, effort: String = "easy") =
        """{"kind":"$kind","minutes":$minutes,"effort":"$effort","what":"Invented line"}"""

    private fun week(vararg sessions: String) = """{"focus":"Invented focus","sessions":[${sessions.joinToString(",")}]}"""

    private fun weeksPlan(vararg weeks: String) = """{"title":"Invented plan","weeks":[${weeks.joinToString(",")}],"why":"Invented reason."}"""

    private val evaluation = """{"headline":"Invented headline","going_well":"Invented.","to_work_on":"Invented.","since_last":""}"""

    private fun evaluated(plan: String) = """{"evaluation":$evaluation,"plan":$plan}"""

    private val twoWeeks = weeksPlan(week(session(), session("run", 20, "push")), week(session(minutes = 40, effort = "steady")))

    @Test
    fun `an evaluation reply becomes an evaluation and a plan`() {
        val reply = TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", ProgrammeAsk(2, 2), hadLast = true) as TrainerReply.Answered

        assertThat(reply.value.evaluation.headline).isEqualTo("Invented headline")
        assertThat(reply.value.evaluation.sinceLast).isEmpty()
        assertThat(reply.value.plan.weeks.map { it.sessions.size }).containsExactly(2, 1).inOrder()
        assertThat(reply.value.plan.weeks.first().sessions.last())
            .isEqualTo(PlannedSession(WorkoutKind.RUN, 20, PlannedEffort.PUSH, "Invented line"))
    }

    @Test
    fun `a plan with the wrong weeks, too many or no sessions in a week, is unreadable`() {
        val threeInOneWeek = weeksPlan(week(session(), session(), session()), week(session()))
        val anEmptyWeek = weeksPlan(week(), week(session()))

        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", ProgrammeAsk(4, 2), hadLast = true))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(threeInOneWeek)), "a-model", ProgrammeAsk(2, 2), hadLast = true))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(anEmptyWeek)), "a-model", ProgrammeAsk(2, 2), hadLast = true))
    }

    @Test
    fun `an unknown kind or effort, minutes out of range or as text, or an empty line is unreadable`() {
        val ask = ProgrammeAsk(2, 2)
        listOf(
            session(kind = "dance"), session(effort = "hard"), session(minutes = 4), session(minutes = 181),
            session().replace("30", "\"30\""), session().replace("Invented line", " "),
        ).forEach { bad ->
            assertUnreadable(
                TrainerResponse.parseEvaluation(reply(evaluated(weeksPlan(week(bad), week(session())))), "a-model", ask, hadLast = true),
            )
        }
        assertUnreadable(
            TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks).replace("Invented headline", "")), "a-model", ask, hadLast = true),
        )
    }

    @Test
    fun `since_last is blanked when no last evaluation was sent, even if the model wrote one`() {
        val withSinceLast = evaluated(twoWeeks).replace("\"since_last\":\"\"", "\"since_last\":\"Invented change.\"")

        val reply = TrainerResponse.parseEvaluation(reply(withSinceLast), "a-model", ProgrammeAsk(2, 2), hadLast = false) as TrainerReply.Answered

        assertThat(reply.value.evaluation.sinceLast).isEmpty()
    }

    @Test
    fun `since_last is kept when a last evaluation was sent`() {
        val withSinceLast = evaluated(twoWeeks).replace("\"since_last\":\"\"", "\"since_last\":\"Invented change.\"")

        val reply = TrainerResponse.parseEvaluation(reply(withSinceLast), "a-model", ProgrammeAsk(2, 2), hadLast = true) as TrainerReply.Answered

        assertThat(reply.value.evaluation.sinceLast).isEqualTo("Invented change.")
    }

    @Test
    fun `an adjusted rest may leave this week empty, but not a later one, and has the weeks left`() {
        val rest = weeksPlan(week(), week(session()))

        assertThat(TrainerResponse.parseAdjusted(reply(rest), "a-model", weeksLeft = 2, perWeek = 3, thisWeekMax = 1))
            .isInstanceOf(TrainerReply.Answered::class.java)
        assertUnreadable(TrainerResponse.parseAdjusted(reply(rest), "a-model", weeksLeft = 3, perWeek = 3, thisWeekMax = 1))
        assertUnreadable(TrainerResponse.parseAdjusted(reply(weeksPlan(week(session()), week())), "a-model", 2, 3, 1))
        assertUnreadable(TrainerResponse.parseAdjusted(reply(weeksPlan(week(session(), session()), week(session()))), "a-model", 2, 3, 1))
    }

    @Test
    fun `an evaluation and a plan survive being written for storage`() {
        val reply = TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", ProgrammeAsk(2, 2), hadLast = true) as TrainerReply.Answered

        assertThat(TrainerResponse.readEvaluation(TrainerResponse.encodeEvaluation(reply.value.evaluation))).isEqualTo(reply.value.evaluation)
        assertThat(TrainerResponse.readWeeksPlan(TrainerResponse.encodeWeeksPlan(reply.value.plan))).isEqualTo(reply.value.plan)
        assertThat(TrainerResponse.readWeeksPlan("not json")).isNull()
    }
}
