package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.Block
import com.metaself.app.domain.trainer.GOAL_15K
import com.metaself.app.domain.trainer.GOAL_150_MIN
import com.metaself.app.domain.trainer.GoalSport
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.OUTLINE_15
import com.metaself.app.domain.trainer.Outline
import com.metaself.app.domain.trainer.Phase
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.STEADY_OUTLINE
import com.metaself.app.domain.trainer.target
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

    /** D116: every path's reply may carry a week_note, read beside the answer. */
    @Test
    fun `every path's reply carries its week_note`() {
        val note = "\"week_note\":\"  Invented week note.  \""
        val plan = TrainerResponse.parsePlan(reply(withNote(goodPlan, note)), "a-model") as TrainerReply.Answered
        val feedback = TrainerResponse.parseFeedback(reply(withNote(goodFeedback, note)), "a-model") as TrainerReply.Answered
        val evaluated = TrainerResponse.parseEvaluation(reply(withNote(evaluated(twoWeeks), note)), "a-model", ProgrammeAsk(2, 2), hadLast = true)
            as TrainerReply.Answered
        val adjusted = TrainerResponse.parseAdjusted(
            reply(withNote(weeksPlan(week(session()), week(session())), note)), "a-model", weeksLeft = 2, perWeek = 3, thisWeekMax = 1,
        ) as TrainerReply.Answered

        listOf(plan, feedback, evaluated, adjusted).forEach { assertThat(it.weekNote).isEqualTo("Invented week note.") }
    }

    /** D116: a model that leaves week_note out, or gives it in the wrong shape, loses only the note. */
    @Test
    fun `a reply without week_note is still a reply`() {
        listOf(goodFeedback, withNote(goodFeedback, "\"week_note\":7"), withNote(goodFeedback, "\"week_note\":\"  \""), withNote(goodFeedback, "\"week_note\":null"))
            .forEach { content ->
                val reply = TrainerResponse.parseFeedback(reply(content), "a-model") as TrainerReply.Answered
                assertThat(reply.weekNote).isNull()
                assertThat(reply.value.note).isEqualTo("Invented note.")
            }
        assertThat((TrainerResponse.parsePlan(reply(goodPlan), "a-model") as TrainerReply.Answered).weekNote).isNull()
        assertThat(TrainerResponse.readWeekNote(null)).isNull()
        assertThat(TrainerResponse.readWeekNote("not json")).isNull()
    }

    /** [content], a JSON object, with [member] added as its last member. */
    private fun withNote(content: String, member: String): String = content.trimEnd().removeSuffix("}") + ",$member}"

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

    // --- D119–D121: goals, outlines, filling in. The goal fixture's outline; every figure is invented. ---------

    private fun outlined(plan: String, outline: Outline) = """{"evaluation":$evaluation,"plan":$plan,"outline":${TrainerPrompt.outlineJson(outline)}}"""

    private val goalAsk = ProgrammeAsk(15, 3, goal = GOAL_15K)

    @Test
    fun `a goal's evaluation fills two weeks and keeps the outline for every week`() {
        val reply = TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, OUTLINE_15)), "a-model", goalAsk, hadLast = false)
            as TrainerReply.Answered

        val plan = reply.value.plan
        assertThat(plan.weeks).hasSize(15)
        assertThat(plan.weeks.take(2).map { it.sessions.size }).containsExactly(2, 1).inOrder()
        assertThat(plan.weeks.drop(2).all { it.sessions.isEmpty() && it.focus.isEmpty() }).isTrue()
        assertThat(plan.outline).isEqualTo(OUTLINE_15)
        assertThat(TrainerResponse.readWeeksPlan(TrainerResponse.encodeWeeksPlan(plan))).isEqualTo(plan)
    }

    @Test
    fun `an outline that misses or adds a week, a target out of bounds, or a third filled week is unreadable`() {
        val short = Outline(OUTLINE_15.blocks.dropLast(1) + Block(Phase.TAPER, "Fresh legs", listOf(target(90, 40))))
        val long = OUTLINE_15 + Block(Phase.TAPER, "Extra", listOf(target(90, 40)))
        val tooLong = OUTLINE_15.withTarget(3, target(600, 60))

        assertUnreadable(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, short)), "a-model", goalAsk, hadLast = false))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, long)), "a-model", goalAsk, hadLast = false))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, tooLong)), "a-model", goalAsk, hadLast = false))
        val threeWeeks = weeksPlan(week(session()), week(session()), week(session()))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(outlined(threeWeeks, OUTLINE_15)), "a-model", goalAsk, hadLast = false))
        assertUnreadable(TrainerResponse.parseEvaluation(reply(evaluated(twoWeeks)), "a-model", goalAsk, hadLast = false))
    }

    @Test
    fun `a steady rhythm's outline is exactly one block of four weeks`() {
        val steady = ProgrammeAsk(4, 3, goal = PlanGoal.Steady)

        assertThat(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, STEADY_OUTLINE)), "a-model", steady, hadLast = false))
            .isInstanceOf(TrainerReply.Answered::class.java)
        assertUnreadable(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, OUTLINE_15)), "a-model", steady, hadLast = false))
    }

    @Test
    fun `a filled week has one to sessions-a-week sessions and a target in bounds, with its sentence`() {
        fun filled(sessions: String, aim: String, why: String = "Invented reason.") =
            """{"focus":"Invented focus","sessions":[$sessions],"target":$aim,"target_why":"$why"}"""
        val inBounds = """{"minutes":120,"longest_minutes":40,"easier":false}"""

        val reply = TrainerResponse.parseFill(reply(filled(session() + "," + session(minutes = 40), inBounds)), "a-model", perWeek = 3)
            as TrainerReply.Answered
        assertThat(reply.value.week.sessions).hasSize(2)
        assertThat(reply.value.target).isEqualTo(target(120, 40))
        assertThat(reply.value.why).isEqualTo("Invented reason.")
        assertThat((TrainerResponse.parseFill(reply(filled(session(), inBounds, "")), "a-model", 3) as TrainerReply.Answered).value.why).isEmpty()
        assertUnreadable(TrainerResponse.parseFill(reply(filled("", inBounds)), "a-model", 3))
        assertUnreadable(TrainerResponse.parseFill(reply(filled(List(4) { session() }.joinToString(","), inBounds)), "a-model", 3))
        assertUnreadable(TrainerResponse.parseFill(reply(filled(session(), """{"minutes":120,"longest_minutes":200,"easier":false}""")), "a-model", 3))
        assertUnreadable(TrainerResponse.parseFill(reply(filled(session(), """{"minutes":120,"longest_minutes":40,"easier":"no"}""")), "a-model", 3))
    }

    @Test
    fun `the next block is four weeks of targets in bounds`() {
        fun next(block: Block) = """{"block":${TrainerPrompt.blockJson(block)},"why":"Invented reason."}"""
        val block = STEADY_OUTLINE.blocks.single()

        val reply = TrainerResponse.parseNextBlock(reply(next(block)), "a-model", perWeek = 3) as TrainerReply.Answered
        assertThat(reply.value.block).isEqualTo(block)
        assertThat(reply.value.why).isEqualTo("Invented reason.")
        assertUnreadable(TrainerResponse.parseNextBlock(reply(next(block.copy(weeks = block.weeks.take(3)))), "a-model", 3))
        assertUnreadable(TrainerResponse.parseNextBlock(reply(next(block.copy(weeks = block.weeks.dropLast(1) + target(10, 20)))), "a-model", 3))
    }

    @Test
    fun `an adjusted rest of an outlined plan is the filled weeks and a target for every week left`() {
        fun rest(weeks: String, targets: Int) =
            weeksPlan(*weeks.split("|").toTypedArray()).removeSuffix("}") +
                ""","targets":[${List(targets) { TrainerPrompt.targetJson(target(100, 30)) }.joinToString(",")}]}"""
        val two = week(session()) + "|" + week(session())

        val reply = TrainerResponse.parseAdjusted(reply(rest(two, 5)), "a-model", weeksLeft = 5, perWeek = 3, thisWeekMax = 1, outlined = true)
            as TrainerReply.Answered
        assertThat(reply.value.plan.weeks).hasSize(2)
        assertThat(reply.value.targets).hasSize(5)
        assertUnreadable(TrainerResponse.parseAdjusted(reply(rest(two, 4)), "a-model", 5, 3, 1, outlined = true))
        assertUnreadable(TrainerResponse.parseAdjusted(reply(rest(two + "|" + week(session()), 5)), "a-model", 5, 3, 1, outlined = true))
        // In the plan's last week only this week is filled.
        assertThat(TrainerResponse.parseAdjusted(reply(rest(week(session()), 1)), "a-model", 1, 3, 1, outlined = true))
            .isInstanceOf(TrainerReply.Answered::class.java)
        // A plan without an outline is read as it always was; targets sent anyway are ignored.
        val legacy = TrainerResponse.parseAdjusted(reply(rest(two, 3)), "a-model", weeksLeft = 2, perWeek = 3, thisWeekMax = 1) as TrainerReply.Answered
        assertThat(legacy.value.targets).isEmpty()
    }

    /** D127: a time goal is stored beside its plan and read back; the goals stored before it read as they did. */
    @Test
    fun `a time goal survives storage, and older goals still read`() {
        val plan = TrainerResponse.readWeeksPlan(twoWeeks)!!
        listOf(GOAL_150_MIN, GOAL_15K, PlanGoal.Steady).forEach { goal ->
            assertThat(TrainerResponse.readGoal(TrainerResponse.encodeStoredWeeksPlan(plan, goal))).isEqualTo(TrainerResponse.StoredGoal.Read(goal))
        }
        assertThat(TrainerResponse.readGoal(TrainerResponse.encodeStoredWeeksPlan(plan, null))).isEqualTo(TrainerResponse.StoredGoal.None)
        val stored = """{"title":"Invented plan","weeks":[],"why":"Invented.","goal":{"kind":"distance","sport":"walk","km":10.0,"by":"2026-12-13"}}"""
        assertThat(TrainerResponse.readGoal(stored)).isEqualTo(TrainerResponse.StoredGoal.Read(PlanGoal.Distance(GoalSport.WALK, 10.0, GOAL_15K.byEpochDay)))
        val tooFew = """{"title":"Invented plan","weeks":[],"why":"Invented.","goal":{"kind":"weekly_minutes","minutes":10,"by":"2026-12-13"}}"""
        assertThat(TrainerResponse.readGoal(tooFew)).isEqualTo(TrainerResponse.StoredGoal.Unreadable)
    }

    /** D120, D127: a time goal's outline covers every week to the goal's week, as a distance's does. */
    @Test
    fun `a time goal's evaluation is checked for every week to the goal`() {
        val ask = ProgrammeAsk(15, 3, goal = GOAL_150_MIN)

        assertThat(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, OUTLINE_15)), "a-model", ask, hadLast = false))
            .isInstanceOf(TrainerReply.Answered::class.java)
        assertUnreadable(TrainerResponse.parseEvaluation(reply(outlined(twoWeeks, STEADY_OUTLINE)), "a-model", ask, hadLast = false))
    }

    // --- D126: suggested goals. Today is Thursday 3 September 2026; every figure is invented. -------------------

    private fun suggestion(goal: String, perWeek: Int = 3, title: String = "Invented title") =
        """{"title":"$title","goal":$goal,"per_week":$perWeek,"why":"Invented why."}"""

    private fun suggestions(vararg items: String, note: String? = null) =
        """{"suggestions":[${items.joinToString(",")}]""" + (note?.let { ""","week_note":"$it"""" } ?: "") + "}"

    @Test
    fun `suggestions are read with their dates moved to the Sunday`() {
        val reply = TrainerResponse.parseSuggestions(
            reply(
                suggestions(
                    suggestion("""{"kind":"steady"}"""),
                    suggestion("""{"kind":"weekly_minutes","minutes":150,"by":"2026-12-09"}""", perWeek = 2),
                    suggestion("""{"kind":"distance","sport":"run","km":15.0,"by":"2026-12-13"}"""),
                    note = "Invented note.",
                ),
            ),
            "a-model", TEST_EPOCH_DAY,
        ) as TrainerReply.Answered

        assertThat(reply.value.map { it.goal }).containsExactly(PlanGoal.Steady, GOAL_150_MIN, GOAL_15K).inOrder()
        assertThat(reply.value[1].perWeek).isEqualTo(2)
        assertThat(reply.value[0].title).isEqualTo("Invented title")
        assertThat(reply.weekNote).isEqualTo("Invented note.")
    }

    @Test
    fun `a suggestion that does not fit is dropped, and a reply with none left is refused`() {
        val good = suggestion("""{"kind":"steady"}""")
        val tooSoon = suggestion("""{"kind":"weekly_minutes","minutes":150,"by":"2026-09-20"}""")
        val tooMany = suggestion("""{"kind":"weekly_minutes","minutes":1000,"by":"2026-12-13"}""")
        val oneSession = suggestion("""{"kind":"steady"}""", perWeek = 1)
        val twoDecimals = suggestion("""{"kind":"distance","sport":"run","km":12.25,"by":"2026-12-13"}""")
        val unknown = suggestion("""{"kind":"pace","by":"2026-12-13"}""")

        val reply = TrainerResponse.parseSuggestions(reply(suggestions(tooSoon, good, tooMany, oneSession, twoDecimals, unknown)), "a-model", TEST_EPOCH_DAY)
            as TrainerReply.Answered
        assertThat(reply.value).hasSize(1)
        assertThat(reply.weekNote).isNull()
        assertUnreadable(TrainerResponse.parseSuggestions(reply(suggestions(tooSoon, tooMany)), "a-model", TEST_EPOCH_DAY))
        assertUnreadable(TrainerResponse.parseSuggestions(reply(suggestions()), "a-model", TEST_EPOCH_DAY))
        assertUnreadable(TrainerResponse.parseSuggestions(reply("""{"week_note":"Invented."}"""), "a-model", TEST_EPOCH_DAY))
        val four = TrainerResponse.parseSuggestions(reply(suggestions(good, good, good, good)), "a-model", TEST_EPOCH_DAY) as TrainerReply.Answered
        assertThat(four.value).hasSize(3)
    }
}
