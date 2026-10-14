package com.metaself.app.data.ai

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.day.TEST_EPOCH_DAY
import com.metaself.app.domain.trainer.ConversationLine
import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.LineKind
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetRewriteRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** D150: what the weekly rewrite sends, and what it is told. Every fact and word is invented. */
class SheetPromptTest {

    @Test
    fun `the rewrite names its schema and asks for the whole sheet, each fact with its id`() {
        val body = SheetPrompt.body("a-model", REQUEST)

        assertThat(body).contains("trainer_sheet")
        val schema = SheetPrompt.SCHEMA.toString()
        assertThat(schema).contains("\"required\":[\"id\",\"group\",\"text\",\"since\",\"source\"]")
        assertThat(schema).contains("[\"body\",\"training\",\"goals\",\"life\"]")
        assertThat(schema).contains("[\"they_said\",\"you_noticed\"]")
        assertThat(schema).doesNotContain("week_note")
    }

    /** The whole shape: the sheet, the removed facts, the note, the span — never the activity record. */
    @Test
    fun `nothing else is sent`() {
        val user = user(SheetPrompt.body("a-model", REQUEST))

        assertThat(paths(user)).containsExactly(
            "today", "since", "sheet", "sheet.facts", "sheet.facts[].id", "sheet.facts[].group", "sheet.facts[].text",
            "sheet.facts[].since", "sheet.facts[].source", "sheet.facts[].edited_by_them", "sheet.removed_by_them",
            "sheet.removed_by_them[].group", "sheet.removed_by_them[].text", "sheet.removed_by_them[].removed",
            "about_me", "conversation", "conversation[].at", "conversation[].from", "conversation[].kind",
            "conversation[].session", "conversation[].week", "conversation[].text",
        )
    }

    @Test
    fun `the sheet goes with ids and edits, removed facts as removed, and rewrite-dropped facts not at all`() {
        val user = user(SheetPrompt.body("a-model", REQUEST))

        val facts = user.getValue("sheet").jsonObject.getValue("facts").jsonArray.map { it.jsonObject }
        assertThat(facts.map { it.getValue("text").jsonPrimitive.content }).containsExactly("Invented said fact.", "Invented noticed fact.").inOrder()
        assertThat(facts[0].getValue("id").jsonPrimitive.content).isEqualTo("3")
        assertThat(facts[0].getValue("edited_by_them").jsonPrimitive.content).isEqualTo("true")
        assertThat(facts[1].getValue("source").jsonPrimitive.content).isEqualTo("you_noticed")
        assertThat(facts[1].getValue("group").jsonPrimitive.content).isEqualTo("training")
        val removed = user.getValue("sheet").jsonObject.getValue("removed_by_them").jsonArray.single().jsonObject
        assertThat(removed.getValue("text").jsonPrimitive.content).isEqualTo("Invented removed fact.")
        assertThat(removed.getValue("removed").jsonPrimitive.content).isEqualTo(LocalDate.ofEpochDay(TEST_EPOCH_DAY - 2).toString())
        assertThat(user.toString()).doesNotContain("Invented dropped fact.")
        assertThat(user.getValue("since").jsonPrimitive.content).isEqualTo("2026-08-30T20:00")
        assertThat(user.getValue("conversation").jsonArray.single().jsonObject.getValue("from").jsonPrimitive.content).isEqualTo("them")
    }

    @Test
    fun `with no sheet and no rewrite yet the sheet is empty and since is null`() {
        val user = user(SheetPrompt.body("a-model", REQUEST.copy(facts = emptyList(), since = null, aboutMe = null)))

        assertThat(user.getValue("sheet").jsonObject.getValue("facts").jsonArray).isEmpty()
        assertThat(user.getValue("since")).isEqualTo(JsonNull)
        assertThat(user.getValue("about_me")).isEqualTo(JsonNull)
    }

    @Test
    fun `the instructions update facts, keep his edits, say only what he said, invent no figure, and ban the phrase`() {
        val system = SheetPrompt.INSTRUCTIONS

        assertThat(system).contains("Update facts")
        assertThat(system).contains("Never summarise the\nsheet or the conversation")
        assertThat(system).contains("keep it as it is, unless the conversation since\n  says otherwise")
        assertThat(system).contains("source \"they_said\" only for what they actually said")
        assertThat(system).contains("Never invent a figure")
        assertThat(system).contains("At most 40 facts and 4000 characters")
        assertThat(system).contains(TrainerPrompt.OWN_CHOICE)
    }

    @Test
    fun `every other request's sheet is the current facts only, or null`() {
        assertThat(SheetPrompt.sheetJson(null)).isEqualTo(JsonNull)
        assertThat(SheetPrompt.sheetJson(listOf(DROPPED))).isEqualTo(JsonNull)
        val sent = SheetPrompt.sheetJson(REQUEST.facts) as JsonArray
        assertThat(sent.map { it.jsonObject.keys }).containsExactly(
            setOf("group", "text", "since", "source"), setOf("group", "text", "since", "source"),
        )
    }

    private fun user(body: String): JsonObject {
        val messages = Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray.map { it.jsonObject }
        return Json.parseToJsonElement(
            messages.single { it.getValue("role").jsonPrimitive.content == "user" }.getValue("content").jsonPrimitive.content,
        ).jsonObject
    }

    private fun paths(element: JsonElement, prefix: String = ""): Set<String> = when (element) {
        is JsonObject -> element.flatMap { (key, value) ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            setOf(path) + paths(value, path)
        }.toSet()
        is JsonArray -> element.flatMap { paths(it, "$prefix[]") }.toSet()
        else -> emptySet()
    }

    private companion object {
        val DROPPED = SheetFact(1, FactGroup.LIFE, "Invented dropped fact.", TEST_EPOCH_DAY - 20, FactSource.SAID, droppedEpochDay = TEST_EPOCH_DAY - 7)
        val REQUEST = SheetRewriteRequest(
            today = TEST_EPOCH_DAY,
            facts = listOf(
                SheetFact(3, FactGroup.BODY, "Invented said fact.", TEST_EPOCH_DAY - 9, FactSource.SAID, edited = true),
                SheetFact(4, FactGroup.TRAINING, "Invented noticed fact.", TEST_EPOCH_DAY - 9, FactSource.NOTICED),
                SheetFact(2, FactGroup.GOALS, "Invented removed fact.", TEST_EPOCH_DAY - 9, FactSource.SAID, droppedEpochDay = TEST_EPOCH_DAY - 2, removedByOwner = true),
                DROPPED,
            ),
            aboutMe = "Invented note.",
            since = LocalDateTime.of(2026, 8, 30, 20, 0),
            conversation = listOf(ConversationLine(LocalDateTime.of(2026, 9, 1, 8, 30), true, LineKind.MESSAGE, "Invented message.")),
        )
    }
}
