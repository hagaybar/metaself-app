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
import com.metaself.app.domain.trainer.TrainerReply
import com.metaself.app.domain.trainer.WeeksPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
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
        content(body)?.let(::readPlan)?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** D108: a reply is a headline and a note; the four parts are only ever read from storage. */
    fun parseFeedback(body: String, model: String): TrainerReply<Feedback> =
        content(body)?.let(::readFeedback)?.takeIf { it.note != null }?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /**
     * D94: the evaluation and a plan that [fits][WeeksPlan.fits] [ask]; anything else is unreadable.
     * [hadLast] is whether a last evaluation was sent; when it was not, since_last is blanked even if
     * the model wrote one — D94 says it is empty when there is none, and the answer is not failed for it.
     */
    fun parseEvaluation(body: String, model: String, ask: ProgrammeAsk, hadLast: Boolean): TrainerReply<EvaluationAndPlan> =
        content(body)?.let(::readEvaluationAndPlan)?.takeIf { it.plan.fits(ask) }
            ?.let { if (hadLast) it else it.copy(evaluation = it.evaluation.copy(sinceLast = "")) }
            ?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** D97: this week and the weeks after, [fitting the rest][WeeksPlan.fitsRest]. */
    fun parseAdjusted(body: String, model: String, weeksLeft: Int, perWeek: Int, thisWeekMax: Int): TrainerReply<WeeksPlan> =
        content(body)?.let(::readWeeksPlan)?.takeIf { it.fitsRest(weeksLeft, perWeek, thisWeekMax) }
            ?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

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
        return WeeksPlan(payload.text("title"), weeks, payload.text("why"))
            .takeIf { it.title.isNotEmpty() && it.why.isNotEmpty() && it.weeks.isNotEmpty() }
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
