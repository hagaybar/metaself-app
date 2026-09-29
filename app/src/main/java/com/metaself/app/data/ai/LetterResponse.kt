package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterTexts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The letter's reply, read strictly (D102) — [TrainerResponse]'s twin; also how a letter is stored. */
object LetterResponse {

    private val json = Json { ignoreUnknownKeys = true }
    private const val NOT_THE_SHAPE = "the reply was not in the shape this app asked for"

    sealed interface Parsed {
        data class Written(val texts: LetterTexts, val model: String) : Parsed
        data class Failed(val failure: EstimateResult) : Parsed
    }

    fun parse(body: String, model: String): Parsed =
        content(body)?.let(::read)?.let { Parsed.Written(it, model) }
            ?: Parsed.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** Six non-empty strings, or null. */
    fun read(content: String?): LetterTexts? = runCatching {
        val o = json.parseToJsonElement(content!!).jsonObject
        fun t(name: String): String {
            val v = o.getValue(name).jsonPrimitive
            require(v.isString) { "$name is not a string" }
            return v.content.trim().also { require(it.isNotEmpty()) { "$name is empty" } }
        }
        LetterTexts(t("headline"), t("effort"), t("progress"), t("look_at"), t("next_week"), t("close"))
    }.getOrNull()

    fun encode(texts: LetterTexts): String = buildJsonObject {
        put("headline", texts.headline); put("effort", texts.effort); put("progress", texts.progress)
        put("look_at", texts.lookAt); put("next_week", texts.nextWeek); put("close", texts.close)
    }.toString()

    private fun content(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrNull()
}
