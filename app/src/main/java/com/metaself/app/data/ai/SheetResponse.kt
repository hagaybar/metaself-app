package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.trainer.RewrittenFact
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.TrainerReply
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * The rewrite's answer (D150), read on the phone — and the sheet's stored shape. A fact with an unknown group or
 * source, or a field missing or of the wrong type, makes the whole answer unreadable: the sheet then stays as it was.
 * An unreadable `since` is not: the phone falls back to the fact's own day ([com.metaself.app.domain.trainer.Sheets.afterRewrite]).
 */
object SheetResponse {

    private val json = Json { ignoreUnknownKeys = true }

    private const val NOT_THE_SHAPE = "the reply was not in the shape this app asked for"

    fun parse(body: String, model: String): TrainerReply<List<RewrittenFact>> {
        val content = TrainerResponse.content(body)
        val facts = content?.let { text ->
            runCatching { json.parseToJsonElement(text).jsonObject.getValue("facts").jsonArray.map { fact(it.jsonObject) } }.getOrNull()
        }
        return facts?.let { TrainerReply.Answered(it, model) }
            ?: TrainerReply.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content ?: body))
    }

    private fun fact(payload: JsonObject): RewrittenFact {
        val id = payload.getValue("id").jsonPrimitive.let { if (it is JsonNull) null else it.also { v -> require(!v.isString) }.long }
        return RewrittenFact(
            id = id,
            group = SheetPrompt.groupOf(payload.text("group"))!!,
            text = payload.text("text"),
            sinceEpochDay = runCatching { LocalDate.parse(payload.text("since")).toEpochDay() }.getOrNull(),
            source = SheetPrompt.sourceOf(payload.text("source"))!!,
        )
    }

    /** The facts of one version, as `trainer_sheets.facts` holds them. */
    fun encode(facts: List<SheetFact>): String = JsonArray(
        facts.map { fact ->
            buildJsonObject {
                put("id", fact.id)
                put("group", fact.group.name)
                put("text", fact.text)
                put("since", fact.sinceEpochDay)
                put("source", fact.source.name)
                put("edited", fact.edited)
                put("dropped", fact.droppedEpochDay)
                put("removed", fact.removedByOwner)
            }
        },
    ).toString()

    /** [encode]'s shape read back; null when it is not one this version reads. */
    fun read(stored: String): List<SheetFact>? = runCatching {
        json.parseToJsonElement(stored).jsonArray.map { element ->
            val payload = element.jsonObject
            SheetFact(
                id = payload.getValue("id").jsonPrimitive.long,
                group = enumValueOf(payload.getValue("group").jsonPrimitive.content),
                text = payload.getValue("text").jsonPrimitive.content,
                sinceEpochDay = payload.getValue("since").jsonPrimitive.long,
                source = enumValueOf(payload.getValue("source").jsonPrimitive.content),
                edited = payload["edited"]?.jsonPrimitive?.booleanOrNull ?: false,
                droppedEpochDay = payload["dropped"]?.jsonPrimitive?.takeUnless { it is JsonNull }?.long,
                removedByOwner = payload["removed"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
    }.getOrNull()

    /** A text part, which must be a JSON string. */
    private fun JsonObject.text(name: String): String {
        val value = getValue(name).jsonPrimitive
        require(value.isString) { "$name is not a string" }
        return value.content.trim()
    }
}
