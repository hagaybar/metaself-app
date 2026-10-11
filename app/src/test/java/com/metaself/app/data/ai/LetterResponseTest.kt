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

/** The letter's reply (D140), read strictly; a letter stored in D102's shape still reads. Every text is invented. */
class LetterResponseTest {

    private val full = mapOf(
        "headline" to "Invented headline", "recognition" to "Invented recognition.", "paying_off" to "Invented paying off.",
        "look_after" to "Invented look.", "rests_on" to "Invented rests on.", "next_week" to "Invented step.", "close" to "Invented close.",
    )

    private val coach = LetterTexts.Coach(
        "Invented headline", "Invented recognition.", "Invented paying off.", "Invented look.", "Invented rests on.",
        "Invented step.", "Invented close.",
    )

    private val sixParts = mapOf(
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
    fun `a full reply reads as the coach's seven texts`() {
        val parsed = LetterResponse.parse(reply(content(full)), "a-model")

        assertThat(parsed).isEqualTo(LetterResponse.Parsed.Written(coach, "a-model"))
    }

    /** A reply must be D140's shape: D102's six texts are a stored letter's, never a new answer's. */
    @Test
    fun `a reply in the old six-part shape is unreadable`() {
        assertThat(LetterResponse.parse(reply(content(sixParts)), "a-model")).isInstanceOf(LetterResponse.Parsed.Failed::class.java)
    }

    @Test
    fun `a letter stored in the old six-part shape still reads, as six parts`() {
        val read = LetterResponse.read(content(sixParts))

        assertThat(read).isEqualTo(
            LetterTexts.SixParts("Invented headline", "Invented effort.", "Invented progress.", "Invented look.", "Invented step.", "Invented close."),
        )
        assertThat(read!!.lookAfter).isEqualTo("Invented look.")
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

    /** D116: the letter's week_note is read beside its six texts; a letter without one is still a letter. */
    @Test
    fun `the letter's week_note is read, and a letter without one is still written`() {
        val noted = LetterResponse.parse(reply(content(full + ("week_note" to " Invented week note. "))), "a-model")
        val without = LetterResponse.parse(reply(content(full)), "a-model")
        val wrong = LetterResponse.parse(reply(content(full + ("week_note" to 7))), "a-model")

        assertThat((noted as LetterResponse.Parsed.Written).weekNote).isEqualTo("Invented week note.")
        assertThat((without as LetterResponse.Parsed.Written).weekNote).isNull()
        assertThat((wrong as LetterResponse.Parsed.Written).weekNote).isNull()
    }

    @Test
    fun `a reply that is not a chat answer is unreadable`() {
        assertThat(LetterResponse.parse("not json", "a-model")).isInstanceOf(LetterResponse.Parsed.Failed::class.java)
    }

    @Test
    fun `a stored letter reads back as it was, in its own shape`() {
        val old = LetterTexts.SixParts("A", "B", "C", "D", "E", "F")
        val new = LetterTexts.Coach("A", "B", "C", "D", "E", "F", "G")

        assertThat(LetterResponse.read(LetterResponse.encode(old))).isEqualTo(old)
        assertThat(LetterResponse.read(LetterResponse.encode(new))).isEqualTo(new)
    }
}
