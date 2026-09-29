package com.metaself.app.data.letter

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

/** How a letter's figures are stored (D104). Every figure is invented. */
class LetterCodecTest {

    @Test
    fun `figures read back as they were written, every figure present or every one missing`() {
        val full = someFigures()
        val empty = someFigures(week = ::anEmptyWeek, targetKcal = null)

        assertThat(LetterCodec.read(LetterCodec.encode(full))).isEqualTo(full)
        assertThat(LetterCodec.read(LetterCodec.encode(empty))).isEqualTo(empty)
    }

    @Test
    fun `text that is not the stored shape reads as nothing, never a throw`() {
        assertThat(LetterCodec.read("{}")).isNull()
        assertThat(LetterCodec.read("not json")).isNull()
        assertThat(LetterCodec.read("")).isNull()
    }

    @Test
    fun `a stored set without four earlier weeks reads as nothing`() {
        val stored = Json.parseToJsonElement(LetterCodec.encode(someFigures())).jsonObject
        val cut = JsonObject(stored + ("earlier" to JsonArray(stored.getValue("earlier").jsonArray.drop(1))))

        assertThat(LetterCodec.read(cut.toString())).isNull()
    }
}
