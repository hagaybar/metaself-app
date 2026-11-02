package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.CoachTurn
import com.metaself.app.domain.trainer.DrawerAsk
import com.metaself.app.domain.trainer.RememberedFact
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
        return TrainerReply.Answered(CoachTurn(open, payload.text("reply"), remember), model)
    }

    /** A text part, trimmed; "" when it is missing or not a JSON string. */
    private fun JsonObject?.text(name: String): String =
        (this?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim().orEmpty()
}
