package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Question
import com.metaself.app.domain.ai.StepResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What a conversation's replies are read as (D58 §4, §12). Pure, and strict where it matters: a
 * question the owner cannot answer is unreadable, never guessed at.
 *
 * The estimate inside a first reply, and the final analysis, are read by [EstimateResponse] with
 * every rule it has (D34, D42, D53 §2) — this reader only decides which of them a reply is.
 */
object ConversationResponse {

    private val json = Json { ignoreUnknownKeys = true }

    /** The most ready-made answers kept, *Not sure* aside (D58 §3.1). */
    private const val MOST_ANSWERS = 5

    /** What the phone adds when no answer means *Not sure* (D58 §4.1 as amended). */
    const val NOT_SURE = "Not sure"

    /**
     * Ways an answer says *not sure*, in the languages the app is described in. A miss only means
     * the phone adds its own *Not sure* beside the model's.
     */
    private val NOT_SURE_WORDS = listOf(
        "not sure", "unsure", "don't know", "dont know", "no idea",
        "לא בטוח", "לא בטוחה", "לא יודע", "לא יודעת",
    )

    /** The first reply: an estimate, or the first question. */
    fun opening(body: String): StepResult = read(body) { content, payload ->
        val needs = payload["needs_questions"]?.jsonPrimitive?.booleanOrNull
        when {
            needs == true -> question(payload, content)
            // Loosely followed in D57's json_object fallback: items without the flag are an estimate.
            needs == false || (needs == null && payload["items"] != null) ->
                StepResult.Estimate(EstimateResponse.readContent(content))
            else -> unreadable(content)
        }
    }

    /** A later step: the next question, or none. */
    fun step(body: String): StepResult = read(body) { content, payload ->
        when (payload["needs_questions"]?.jsonPrimitive?.booleanOrNull) {
            true -> question(payload, content)
            false -> StepResult.Enough
            null -> unreadable(content)
        }
    }

    /**
     * The final analysis, by today's reader; its `plate` is ignored. The answer is always kept for
     * *Show the model's answer*, since the working is worth reading there (D58 §5.1).
     */
    fun final(body: String): EstimateResult {
        val content = EstimateResponse.content(body)
            ?: return EstimateResult.Unreadable(EstimateResponse.NOT_THE_SHAPE, body)
        return when (val result = EstimateResponse.readContent(content)) {
            is EstimateResult.Proposed -> result.copy(proposal = result.proposal.copy(answer = content))
            else -> result
        }
    }

    private fun read(body: String, reading: (String, JsonObject) -> StepResult): StepResult {
        val content = EstimateResponse.content(body)
            ?: return StepResult.Failed(EstimateResult.Unreadable(EstimateResponse.NOT_THE_SHAPE, body))
        val payload = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
            ?: return unreadable(content)
        return runCatching { reading(content, payload) }.getOrElse { unreadable(content) }
    }

    private fun question(payload: JsonObject, content: String): StepResult {
        val asked = payload["question"] as? JsonObject ?: return unreadable(content)
        val text = asked["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val given = asked["options"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            ?.distinctBy { it.lowercase() }
            .orEmpty()
        val (unsure, answers) = given.partition(::meansNotSure)
        if (text.isEmpty() || answers.isEmpty()) return unreadable(content)
        val options = answers.take(MOST_ANSWERS) + (unsure.firstOrNull() ?: NOT_SURE)
        val planned = (payload["total_planned"]?.jsonPrimitive?.intOrNull ?: 1)
            .coerceIn(1, ConversationPrompt.MOST_QUESTIONS)
        return StepResult.Ask(Question(text, options), planned)
    }

    private fun meansNotSure(answer: String): Boolean =
        answer.lowercase().let { lower -> NOT_SURE_WORDS.any { it in lower } }

    private fun unreadable(content: String): StepResult =
        StepResult.Failed(EstimateResult.Unreadable(EstimateResponse.NOT_THE_SHAPE, content))
}
