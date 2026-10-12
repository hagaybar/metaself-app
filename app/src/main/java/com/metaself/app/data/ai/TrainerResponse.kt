package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.movement.WorkoutKind
import com.metaself.app.domain.trainer.Evaluation
import com.metaself.app.domain.trainer.EvaluationAndPlan
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.PlanWeek
import com.metaself.app.domain.trainer.PlannedEffort
import com.metaself.app.domain.trainer.PlannedSession
import com.metaself.app.domain.trainer.ProgrammeAsk
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.SessionSlot
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.WeeksPlan
import com.metaself.app.domain.trainer.AdjustedRest
import com.metaself.app.domain.trainer.Block
import com.metaself.app.domain.trainer.BlockAnswer
import com.metaself.app.domain.trainer.FILLED_AT_FIRST
import com.metaself.app.domain.trainer.FilledWeek
import com.metaself.app.domain.trainer.PlanAheadAsk
import com.metaself.app.domain.trainer.withOutline
import com.metaself.app.domain.trainer.GoalSport
import com.metaself.app.domain.trainer.GoalBounds
import com.metaself.app.domain.trainer.GoalSuggestion
import com.metaself.app.domain.trainer.GoalVerdict
import com.metaself.app.domain.trainer.GoalView
import com.metaself.app.domain.trainer.Outline
import com.metaself.app.domain.trainer.Phase
import com.metaself.app.domain.trainer.PlanGoal
import com.metaself.app.domain.trainer.WeekTarget
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The trainer's replies, read strictly (D86, D87) — [ReviewResponse]'s twin. An answer that is not in
 * the shape asked for is [EstimateResult.Unreadable], one of the call's failures; never an exception.
 *
 * Also where a plan or feedback is written back into that same shape for storage (D88, design question
 * 3) — through [TrainerPrompt]'s own builders — so what is stored reads back with the same rules.
 */
object TrainerResponse {

    const val MIN_STEPS = 3
    const val MAX_STEPS = 6

    private val json = Json { ignoreUnknownKeys = true }
    private const val NOT_THE_SHAPE = "the reply was not in the shape this app asked for"

    fun parsePlan(body: String, model: String): TrainerReply<SessionPlan> =
        content(body)?.let(::readPlan)?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** D147: a reply in the conversation — its words, trimmed; a blank or missing one is unreadable. */
    fun parseTalk(body: String, model: String): TrainerReply<String> =
        content(body)?.let { content ->
            runCatching { json.parseToJsonElement(content).jsonObject.text("reply").trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
            ?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** D108: a reply is a headline and a note; the four parts are only ever read from storage. */
    fun parseFeedback(body: String, model: String): TrainerReply<Feedback> =
        content(body)?.let(::readFeedback)?.takeIf { it.note != null }?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /**
     * D94: the evaluation and a plan that [fits][WeeksPlan.fits] [ask]; anything else is unreadable.
     * [hadLast] is whether a last evaluation was sent; when it was not, since_last is blanked even if
     * the model wrote one — D94 says it is empty when there is none, and the answer is not failed for it.
     * D129: its goal_view is read leniently ([readGoalView], against [today]) — one that does not read is dropped and
     * the reply stands.
     */
    fun parseEvaluation(body: String, model: String, ask: ProgrammeAsk, hadLast: Boolean, today: Long): TrainerReply<EvaluationAndPlan> =
        content(body)?.let { content -> if (ask.goal == null) readEvaluationAndPlan(content)?.takeIf { it.plan.fits(ask) } else readOutlined(content, ask) }
            ?.let { if (hadLast) it else it.copy(evaluation = it.evaluation.copy(sinceLast = "")) }
            ?.let { it.copy(goalView = readGoalView(content(body), today)) }
            ?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /**
     * D120, D121: a goal's evaluation — sessions for the plan's first two weeks (one, for a one-week plan), each with
     * one to sessions-a-week; an outline covering every week of [ask] within the bounds — composed into the whole plan,
     * its later weeks empty. Null for anything else.
     */
    private fun readOutlined(content: String, ask: ProgrammeAsk): EvaluationAndPlan? = runCatching {
        val reply = readEvaluationAndPlan(content)!!
        val outline = outline(json.parseToJsonElement(content).jsonObject.getValue("outline").jsonObject)
        val first = reply.plan.weeks
        val fits = first.size == minOf(FILLED_AT_FIRST, ask.weeks) && first.all { it.sessions.size in 1..ask.perWeek } &&
            outline.covers(ask.weeks) && outline.fits(ask.perWeek)
        if (fits) reply.copy(plan = reply.plan.copy(outline = null).withOutline(outline)) else null
    }.getOrNull()

    /**
     * D97: this week and the weeks after, [fitting the rest][WeeksPlan.fitsRest]. For a plan with an outline
     * ([outlined], D121): this week and next week only (this week alone when it is the last), and a target in bounds
     * for every week left.
     */
    fun parseAdjusted(
        body: String,
        model: String,
        weeksLeft: Int,
        perWeek: Int,
        thisWeekMax: Int,
        outlined: Boolean = false,
    ): TrainerReply<AdjustedRest> =
        content(body)?.let { content -> if (outlined) readOutlinedRest(content, weeksLeft, perWeek, thisWeekMax) else readRest(content, weeksLeft, perWeek, thisWeekMax) }
            ?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    private fun readRest(content: String, weeksLeft: Int, perWeek: Int, thisWeekMax: Int): AdjustedRest? =
        readWeeksPlan(content)?.takeIf { it.fitsRest(weeksLeft, perWeek, thisWeekMax) }?.let { AdjustedRest(it.copy(outline = null)) }

    private fun readOutlinedRest(content: String, weeksLeft: Int, perWeek: Int, thisWeekMax: Int): AdjustedRest? = runCatching {
        val plan = readWeeksPlan(content)!!.copy(outline = null)
        val targets = json.parseToJsonElement(content).jsonObject.getValue("targets").jsonArray.map { target(it.jsonObject) }
        val fits = plan.fitsRest(minOf(FILLED_AT_FIRST, weeksLeft), perWeek, thisWeekMax) &&
            targets.size == weeksLeft && targets.all { it.fits(perWeek) }
        if (fits) AdjustedRest(plan, targets) else null
    }.getOrNull()

    /** D121: one week filled in — one to [perWeek] sessions, its target in bounds, and why ("" when unchanged). */
    fun parseFill(body: String, model: String, perWeek: Int): TrainerReply<FilledWeek> =
        content(body)?.let { content ->
            runCatching {
                val payload = json.parseToJsonElement(content).jsonObject
                val week = PlanWeek(payload.text("focus"), payload.getValue("sessions").jsonArray.map { planned(it.jsonObject) })
                val filled = FilledWeek(week, target(payload.getValue("target").jsonObject), payload.text("target_why"))
                filled.takeIf { week.sessions.size in 1..perWeek && it.target.fits(perWeek) }
            }.getOrNull()
        }
            ?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** D121: the next block — four weeks of targets in bounds — and why. */
    fun parseNextBlock(body: String, model: String, perWeek: Int): TrainerReply<BlockAnswer> =
        content(body)?.let { content ->
            runCatching {
                val payload = json.parseToJsonElement(content).jsonObject
                val answer = BlockAnswer(block(payload.getValue("block").jsonObject), payload.text("why"))
                answer.takeIf { it.block.weeks.size == PlanAheadAsk.BLOCK_WEEKS && it.block.weeks.all { week -> week.fits(perWeek) } && it.why.isNotEmpty() }
            }.getOrNull()
        }
            ?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /**
     * D126: the suggestions, each read and [checked][GoalBounds.checked] on its own against [today] — its date moved to
     * its Sunday; one that does not read or fit is dropped. At most [GoalBounds.SUGGESTIONS]; none left is unreadable.
     */
    fun parseSuggestions(body: String, model: String, today: Long): TrainerReply<List<GoalSuggestion>> =
        content(body)?.let { content ->
            runCatching {
                json.parseToJsonElement(content).jsonObject.getValue("suggestions").jsonArray.mapNotNull { element ->
                    runCatching { suggestion(element.jsonObject) }.getOrNull()?.let { GoalBounds.checked(it, today) }
                }.take(GoalBounds.SUGGESTIONS)
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
            ?.let { TrainerReply.Answered(it, model, readWeekNote(content(body))) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /**
     * D129: the goal_view in [content] (a reply, or a stored evaluation), read leniently: null when absent or when its
     * verdict or says does not read. Its instead is dropped alone when it does not read, does not fit the form against
     * [today] (a reply; a stored one was checked when it came), or comes with a goal within reach.
     */
    fun readGoalView(content: String?, today: Long? = null): GoalView? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject[GOAL_VIEW]?.jsonObject ?: return@runCatching null
        val verdict = GoalVerdict.entries.first { it.name.lowercase() == payload.text("verdict") }
        val says = payload.text("says").also { require(it.isNotEmpty()) { "says is empty" } }
        val instead = payload["instead"]?.takeUnless { it is JsonNull || verdict == GoalVerdict.WITHIN_REACH }?.let { element ->
            runCatching { goal(element.jsonObject) }.getOrNull()?.let { if (today == null) it else GoalBounds.checked(it, today) }
        }
        GoalView(verdict, says, instead)
    }.getOrNull()

    /** D129: an evaluation as stored — its own shape and, when the trainer took a stand, that under [GOAL_VIEW]. */
    fun encodeStoredEvaluation(evaluation: Evaluation, goalView: GoalView?): String {
        val shape = TrainerPrompt.evaluationJson(evaluation)
        if (goalView == null) return shape.toString()
        return JsonObject(shape + (GOAL_VIEW to TrainerPrompt.goalViewJson(goalView))).toString()
    }

    private const val GOAL_VIEW = "goal_view"

    private fun suggestion(payload: JsonObject): GoalSuggestion = GoalSuggestion(
        title = payload.text("title"),
        goal = goal(payload.getValue("goal").jsonObject),
        perWeek = payload.minute("per_week"),
        why = payload.text("why"),
    )

    /**
     * D116: a reply's week_note, trimmed — read leniently, unlike the answer: absent, not a string, or blank is
     * no note, and the reply it came in stands. Shared with the letter's reply.
     */
    fun readWeekNote(content: String?): String? = runCatching {
        json.parseToJsonElement(content!!).jsonObject["week_note"]?.jsonPrimitive
            ?.takeIf { it.isString }?.content?.trim()?.ifEmpty { null }
    }.getOrNull()

    /** A plan in the reply's shape, or null for anything else. */
    fun readPlan(content: String?): SessionPlan? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject
        val steps = payload.getValue("steps").jsonArray.map { element ->
            val step = element.jsonObject
            PlanStep(
                fromMinute = step.minute("from_minute"),
                toMinute = step.minute("to_minute"),
                what = step.text("what"),
                how = step.text("how"),
            )
        }
        val plan = SessionPlan(
            title = payload.text("title"),
            steps = steps,
            why = payload.text("why"),
        )
        plan.takeIf(::usable)
    }.getOrNull()

    /**
     * Feedback in either shape it is stored in, or null for anything else: with a note (D108), or — stored
     * before D108 — with four parts (D87). A payload is new-shaped when it has a note.
     */
    fun readFeedback(content: String?): Feedback? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject
        fun part(name: String) = payload.text(name)
        val followed = when (part("plan_followed")) {
            "yes" -> PlanFollowed.YES
            "partly" -> PlanFollowed.PARTLY
            "no" -> PlanFollowed.NO
            "no_plan" -> PlanFollowed.NO_PLAN
            else -> null
        }!!
        val feedback = if ("note" in payload) {
            Feedback(part("headline"), "", "", "", "", followed, note = part("note"))
        } else {
            Feedback(part("headline"), part("against_plan"), part("numbers"), part("next_time"), part("this_week"), followed)
        }
        feedback.takeIf(::usable)
    }.getOrNull()

    fun readEvaluationAndPlan(content: String?): EvaluationAndPlan? = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject
        EvaluationAndPlan(
            evaluation(payload.getValue("evaluation").jsonObject)!!,
            weeksPlan(payload.getValue("plan").jsonObject)!!,
        )
    }.getOrNull()

    /** An evaluation in the reply's shape, or null for anything else. */
    fun readEvaluation(content: String?): Evaluation? =
        runCatching { evaluation(json.parseToJsonElement(content!!).jsonObject) }.getOrNull()

    /** A plan of weeks in the reply's shape, or null for anything else. A week may be empty (D97). */
    fun readWeeksPlan(content: String?): WeeksPlan? =
        runCatching { weeksPlan(json.parseToJsonElement(content!!).jsonObject) }.getOrNull()

    /** The same shape [TrainerPrompt] sends an earlier plan in, so stored and sent cannot drift. */
    fun encodePlan(plan: SessionPlan): String = TrainerPrompt.planJson(plan).toString()

    /**
     * D122: a suggestion as stored — the plan's own shape, and, when it was kept for a planned session, that
     * session's place under [FOR_PLANNED]. [readPlan] ignores the extra key, so the plan reads the same either way.
     */
    fun encodeStoredPlan(plan: SessionPlan, slot: SessionSlot?): String {
        val shape = TrainerPrompt.planJson(plan)
        if (slot == null) return shape.toString()
        val place = buildJsonObject {
            put("chain", slot.chainId)
            put("week", slot.week)
            put("session", slot.session)
        }
        return JsonObject(shape + (FOR_PLANNED to place)).toString()
    }

    /** D122: the planned session a stored suggestion was kept for; null when none, or unreadable. */
    fun readSlot(content: String?): SessionSlot? = runCatching {
        val place = json.parseToJsonElement(content!!).jsonObject[FOR_PLANNED]?.jsonObject ?: return@runCatching null
        SessionSlot(place.getValue("chain").jsonPrimitive.long, place.getValue("week").jsonPrimitive.int, place.getValue("session").jsonPrimitive.int)
    }.getOrNull()

    private const val FOR_PLANNED = "for_planned"

    fun encodeFeedback(feedback: Feedback): String = TrainerPrompt.feedbackJson(feedback).toString()

    fun encodeEvaluation(evaluation: Evaluation): String = TrainerPrompt.evaluationJson(evaluation).toString()

    fun encodeWeeksPlan(plan: WeeksPlan): String = TrainerPrompt.weeksPlanJson(plan).toString()

    private fun evaluation(payload: JsonObject): Evaluation? = Evaluation(
        headline = payload.text("headline"),
        goingWell = payload.text("going_well"),
        toWorkOn = payload.text("to_work_on"),
        sinceLast = payload.text("since_last"),
    ).takeIf { it.headline.isNotEmpty() && it.goingWell.isNotEmpty() && it.toWorkOn.isNotEmpty() }

    private fun weeksPlan(payload: JsonObject): WeeksPlan? {
        val weeks = payload.getValue("weeks").jsonArray.map { element ->
            val week = element.jsonObject
            PlanWeek(week.text("focus"), week.getValue("sessions").jsonArray.map { planned(it.jsonObject) })
        }
        // D120: an outline present must read; absent, the plan was made before outlines.
        val outline = payload["outline"]?.let { outline(it.jsonObject) }
        return WeeksPlan(payload.text("title"), weeks, payload.text("why"), outline)
            .takeIf { it.title.isNotEmpty() && it.why.isNotEmpty() && it.weeks.isNotEmpty() }
    }

    /** D120: an outline in the reply's shape; throws on anything else. Its coverage and bounds are the caller's. */
    private fun outline(payload: JsonObject): Outline = Outline(payload.getValue("blocks").jsonArray.map { block(it.jsonObject) })

    private fun block(payload: JsonObject): Block = Block(
        phase = Phase.entries.first { it.name.lowercase() == payload.text("phase") },
        focus = payload.text("focus"),
        weeks = payload.getValue("weeks").jsonArray.map { target(it.jsonObject) },
    )

    private fun target(payload: JsonObject): WeekTarget = WeekTarget(
        minutes = payload.minute("minutes"),
        longest = payload.minute("longest_minutes"),
        easier = payload.flag("easier"),
    )

    /** A JSON boolean: "true" in quotes is text. */
    private fun JsonObject.flag(name: String): Boolean {
        val value = getValue(name).jsonPrimitive
        require(!value.isString) { "$name is not a boolean" }
        return value.boolean
    }

    /**
     * D119: a plan's row as stored — the plan's own shape ([encodeWeeksPlan]) and, when it has a goal, the goal
     * under [GOAL]. [readWeeksPlan] ignores the extra key.
     */
    fun encodeStoredWeeksPlan(plan: WeeksPlan, goal: PlanGoal?): String {
        val shape = TrainerPrompt.weeksPlanJson(plan)
        if (goal == null) return shape.toString()
        return JsonObject(shape + (GOAL to TrainerPrompt.goalJson(goal))).toString()
    }

    /**
     * D119: the goal stored beside a plan — [StoredGoal.None] when there is none (a plan made before goals), and
     * [StoredGoal.Unreadable] when the key is there but does not read, which makes the row unreadable.
     */
    fun readGoal(content: String?): StoredGoal = runCatching {
        val payload = json.parseToJsonElement(content!!).jsonObject[GOAL]?.jsonObject ?: return@runCatching StoredGoal.None
        StoredGoal.Read(goal(payload))
    }.getOrDefault(StoredGoal.Unreadable)

    /** What [readGoal] found. */
    sealed interface StoredGoal {
        data object None : StoredGoal
        data object Unreadable : StoredGoal
        data class Read(val goal: PlanGoal) : StoredGoal
    }

    private const val GOAL = "goal"

    private fun goal(payload: JsonObject): PlanGoal = when (payload.text("kind")) {
        "steady" -> PlanGoal.Steady
        "distance" -> PlanGoal.Distance(
            sport = GoalSport.entries.first { it.name.lowercase() == payload.text("sport") },
            km = payload.getValue("km").jsonPrimitive.also { require(!it.isString) { "km is not a number" } }.double,
            byEpochDay = LocalDate.parse(payload.text("by")).toEpochDay(),
        )
        "weekly_minutes" -> PlanGoal.WeeklyMinutes(
            minutes = payload.minute("minutes"),
            byEpochDay = LocalDate.parse(payload.text("by")).toEpochDay(),
        )
        else -> throw IllegalArgumentException("not a goal")
    }

    /** Throws on anything unknown; [PlannedSession]'s own checks refuse minutes out of 5..180. */
    private fun planned(payload: JsonObject): PlannedSession = PlannedSession(
        kind = when (payload.text("kind")) {
            "walk" -> WorkoutKind.WALK
            "run" -> WorkoutKind.RUN
            "cycle" -> WorkoutKind.CYCLE
            "swim" -> WorkoutKind.SWIM
            "strength" -> WorkoutKind.STRENGTH
            "other" -> WorkoutKind.OTHER
            else -> null
        }!!,
        minutes = payload.minute("minutes"),
        effort = when (payload.text("effort")) {
            "easy" -> PlannedEffort.EASY
            "steady" -> PlannedEffort.STEADY
            "push" -> PlannedEffort.PUSH
            else -> null
        }!!,
        what = payload.text("what").also { require(it.isNotEmpty()) { "a planned session says what it is" } },
    )

    internal fun content(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive
            .takeUnless { it is JsonNull }?.content
    }.getOrNull()

    /** The chat completion's own refusal (`message.refusal`), when the provider sent one instead of content. */
    internal fun refusal(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["refusal"]!!.jsonPrimitive
            .takeUnless { it is JsonNull }?.content
    }.getOrNull()

    /**
     * A text part, which must be a JSON string: `jsonPrimitive.content` alone would read null as "null"
     * and a number or a boolean as its digits or word. Anything else throws, and so reads as unreadable.
     */
    private fun JsonObject.text(name: String): String {
        val value = getValue(name).jsonPrimitive
        require(value.isString) { "$name is not a string" }
        return value.content.trim()
    }

    /** A whole minute, which must be a JSON number: "10" in quotes is text, not a minute. */
    private fun JsonObject.minute(name: String): Int {
        val value = getValue(name).jsonPrimitive
        require(!value.isString) { "$name is not a number" }
        return value.int
    }

    /** Design question 18. */
    private fun usable(plan: SessionPlan): Boolean =
        plan.title.isNotEmpty() && plan.why.isNotEmpty() &&
            plan.steps.size in MIN_STEPS..MAX_STEPS &&
            plan.steps.all { it.fromMinute >= 0 && it.toMinute > it.fromMinute && it.what.isNotEmpty() } &&
            plan.steps.zipWithNext().all { (a, b) -> b.fromMinute >= a.fromMinute }

    /** D108: a note needs its headline and its text; four parts (D87) keep their old rule. */
    private fun usable(feedback: Feedback): Boolean = if (feedback.note != null) {
        feedback.headline.isNotEmpty() && feedback.note.isNotEmpty()
    } else {
        listOf(feedback.headline, feedback.numbers, feedback.nextTime, feedback.thisWeek).all { it.isNotEmpty() } &&
            (feedback.againstPlan.isNotEmpty() || feedback.followed == PlanFollowed.NO_PLAN)
    }
}
