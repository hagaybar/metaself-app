package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.Proposal
import com.metaself.app.domain.trainer.ProposalKind
import com.metaself.app.domain.trainer.RememberedFact
import com.metaself.app.domain.trainer.Sheets
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * A coach's answer (D161), read on the phone. Only what is not a JSON object at all is unreadable: a drawer it cannot
 * name goes back to it as such ([CoachDrawers]), a fact that does not read is dropped, and whether blank words are a
 * failure is for the loop that knows which look this was.
 */
object CoachResponse {

    private val json = Json { ignoreUnknownKeys = true }

    private const val NOT_THE_SHAPE = "the reply was not in the shape this app asked for"

    fun parse(body: String, model: String): TrainerReply<CoachTurn> {
        val content = TrainerResponse.content(body)
        val payload = content?.let { text -> runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() }
            ?: return TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content ?: body))
        val open = (payload["open"] as? JsonArray).orEmpty().map { element ->
            val ask = element as? JsonObject
            DrawerAsk(ask.text("drawer"), ask.text("from"), ask.text("to"), ask.text("words"))
        }
        val remember = (payload["remember"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { RememberedFact(it.text("group"), it.text("text")) }
        }
        val aboutFood = (payload["about_food"] as? JsonPrimitive)?.takeUnless { it.isString }?.content == "true"
        return TrainerReply.Answered(CoachTurn(open, payload.text("reply"), remember, proposal(payload["propose"] as? JsonObject), aboutFood), model)
    }

    /**
     * D164: the change named, or null for none. Only the kinds here exist, so no answer can name anything else. One that
     * does not read stays a proposal the card can say so about, since the reply's words may promise it.
     */
    private fun proposal(payload: JsonObject?): Proposal? {
        val kind = payload.text("kind")
        if (payload == null || kind.isEmpty() || kind == "none") return null
        val unreadable = Proposal(ProposalKind.UNREADABLE)
        val words = payload.text("words")
        val fact = payload.text("fact")
        val text = payload.text("text")
        return when (kind) {
            "change_plan" -> if (words.isEmpty()) unreadable else Proposal(ProposalKind.CHANGE_PLAN, words = words)
            "new_plan" -> {
                val goal = runCatching { TrainerPrompt.goalJson(TrainerResponse.goalOf(payload.getValue("goal").toString())).toString() }.getOrNull()
                val perWeek = (payload["per_week"] as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toIntOrNull()
                if (goal == null || perWeek == null) unreadable else Proposal(ProposalKind.NEW_PLAN, words = words, goal = goal, perWeek = perWeek)
            }
            "stop_plan" -> Proposal(ProposalKind.STOP_PLAN)
            "fill_week" -> Proposal(ProposalKind.FILL_WEEK)
            "next_block" -> Proposal(ProposalKind.NEXT_BLOCK)
            "add_fact" -> {
                val group = SheetPrompt.groupOf(payload.text("group"))
                if (group == null || !fits(text)) unreadable else Proposal(ProposalKind.ADD_FACT, group = group.name, text = text)
            }
            "change_fact" -> if (fact.isEmpty() || !fits(text)) unreadable else Proposal(ProposalKind.CHANGE_FACT, fact = fact, text = text)
            "remove_fact" -> if (fact.isEmpty()) unreadable else Proposal(ProposalKind.REMOVE_FACT, fact = fact)
            else -> unreadable
        }
    }

    private fun fits(fact: String): Boolean = fact.isNotEmpty() && fact.length <= Sheets.MAX_FACT

    /** A text part, trimmed; "" when it is missing or not a JSON string. */
    private fun JsonObject?.text(name: String): String =
        (this?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
}
