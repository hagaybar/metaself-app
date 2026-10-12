package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterTexts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The letter's reply, read strictly (D140) — [TrainerResponse]'s twin; also how a letter is stored. A reply
 * must be the coach's shape; a stored letter may also be D102's six parts, written before it.
 */
object LetterResponse {

    private val json = Json { ignoreUnknownKeys = true }
    private const val NOT_THE_SHAPE = "the reply was not in the shape this app asked for"

    sealed interface Parsed {
        /** [weekNote]: the letter's week_note (D116), null when left out; the letter stands without it. */
        data class Written(val texts: LetterTexts.Coach, val model: String, val weekNote: String? = null) : Parsed
        data class Failed(val failure: EstimateResult) : Parsed
    }

    fun parse(body: String, model: String): Parsed =
        content(body)?.let(::readCoach)?.let { Parsed.Written(it, model, TrainerResponse.readWeekNote(content(body))) }
            ?: Parsed.Failed(EstimateResult.Unreadable(NOT_THE_SHAPE, content(body) ?: body))

    /** A stored letter: the coach's seven texts, or D102's six; null when neither. */
    fun read(content: String?): LetterTexts? = readCoach(content) ?: readSixParts(content)

    /** D140's seven non-empty strings, or null. */
    fun readCoach(content: String?): LetterTexts.Coach? = texts(content) { t ->
        LetterTexts.Coach(t("headline"), t("recognition"), t("paying_off"), t("look_after"), t("rests_on"), t("next_week"), t("close"))
    }

    private fun readSixParts(content: String?): LetterTexts.SixParts? = texts(content) { t ->
        LetterTexts.SixParts(t("headline"), t("effort"), t("progress"), t("look_at"), t("next_week"), t("close"))
    }

    private fun <T> texts(content: String?, build: ((String) -> String) -> T): T? = runCatching {
        val o = json.parseToJsonElement(content!!).jsonObject
        build { name ->
            val v = o.getValue(name).jsonPrimitive
            require(v.isString) { "$name is not a string" }
            v.content.trim().also { require(it.isNotEmpty()) { "$name is empty" } }
        }
    }.getOrNull()

    /** Each shape is stored under its own names, so a letter reads back as the shape it was written in. */
    fun encode(texts: LetterTexts): String = buildJsonObject {
        when (texts) {
            is LetterTexts.Coach -> {
                put("headline", texts.headline); put("recognition", texts.recognition); put("paying_off", texts.payingOff)
                put("look_after", texts.lookAfter); put("rests_on", texts.restsOn); put("next_week", texts.nextWeek)
                put("close", texts.close)
            }
            is LetterTexts.SixParts -> {
                put("headline", texts.headline); put("effort", texts.effort); put("progress", texts.progress)
                put("look_at", texts.lookAt); put("next_week", texts.nextWeek); put("close", texts.close)
            }
        }
    }.toString()

    private fun content(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrNull()
}
