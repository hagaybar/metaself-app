package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.letter.LetterTexts
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import org.junit.jupiter.api.Test

/** The letter's reply (D102), read strictly. Every text is invented. */
class LetterResponseTest {

    private val full = mapOf(
        "headline" to "Invented headline", "effort" to "Invented effort.", "progress" to "Invented progress.",
        "look_at" to "Invented look.", "next_week" to "Invented step.", "close" to "Invented close.",
    )

    private fun reply(content: String): String = buildJsonObject {
        putJsonArray("choices") {
            add(buildJsonObject { putJsonObject("message") { put("content", content) } })
        }
    }.toString()

    private fun content(fields: Map<String, Any?>): String =
        JsonObject(fields.mapValues { (_, v) -> if (v is Number) JsonPrimitive(v) else JsonPrimitive(v as String) }).toString()

    @Test
    fun `a full reply reads as six texts`() {
        val parsed = LetterResponse.parse(reply(content(full)), "a-model")

        assertThat(parsed).isEqualTo(
            LetterResponse.Parsed.Written(
                LetterTexts("Invented headline", "Invented effort.", "Invented progress.", "Invented look.", "Invented step.", "Invented close."),
                "a-model",
            ),
        )
    }

    @Test
    fun `each text missing, empty or a number is unreadable`() {
        LetterPrompt.NAMES.forEach { name ->
            listOf(full - name, full + (name to "  "), full + (name to 7)).forEach { fields ->
                val parsed = LetterResponse.parse(reply(content(fields)), "a-model")

                assertThat(parsed).isInstanceOf(LetterResponse.Parsed.Failed::class.java)
                assertThat((parsed as LetterResponse.Parsed.Failed).failure).isInstanceOf(EstimateResult.Unreadable::class.java)
            }
        }
    }

    @Test
    fun `a reply that is not a chat answer is unreadable`() {
        assertThat(LetterResponse.parse("not json", "a-model")).isInstanceOf(LetterResponse.Parsed.Failed::class.java)
    }

    @Test
    fun `a stored letter reads back as it was`() {
        val texts = LetterTexts("A", "B", "C", "D", "E", "F")

        assertThat(LetterResponse.read(LetterResponse.encode(texts))).isEqualTo(texts)
    }
}
