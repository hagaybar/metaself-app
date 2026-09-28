package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.Feedback
import com.metaself.app.domain.trainer.PlanFollowed
import com.metaself.app.domain.trainer.PlanStep
import com.metaself.app.domain.trainer.SessionPlan
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.Json
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

    fun parseFeedback(body: String, model: String): TrainerReply<Feedback> =
        content(body)?.let(::readFeedback)?.let { TrainerReply.Answered(it, model) }
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

    /** Feedback in the reply's shape, or null for anything else. */
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
        val feedback = Feedback(part("headline"), part("against_plan"), part("numbers"), part("next_time"), part("this_week"), followed)
        feedback.takeIf(::usable)
    }.getOrNull()

    /** The same shape [TrainerPrompt] sends an earlier plan in, so stored and sent cannot drift. */
    fun encodePlan(plan: SessionPlan): String = TrainerPrompt.planJson(plan).toString()

    fun encodeFeedback(feedback: Feedback): String = TrainerPrompt.feedbackJson(feedback).toString()

    private fun content(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
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

    private fun usable(feedback: Feedback): Boolean =
        listOf(feedback.headline, feedback.numbers, feedback.nextTime, feedback.thisWeek).all { it.isNotEmpty() } &&
            (feedback.againstPlan.isNotEmpty() || feedback.followed == PlanFollowed.NO_PLAN)
}
