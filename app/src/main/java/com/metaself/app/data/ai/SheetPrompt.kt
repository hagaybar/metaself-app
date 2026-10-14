package com.metaself.app.data.ai

import com.metaself.app.domain.trainer.FactGroup
import com.metaself.app.domain.trainer.FactSource
import com.metaself.app.domain.trainer.SheetFact
import com.metaself.app.domain.trainer.SheetRewriteRequest
import com.metaself.app.domain.trainer.Sheets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What leaves the phone for the weekly rewrite of the sheet (D150) — a pure function with its own "nothing else is
 * sent" test. It sends today, the sheet (each current fact with its id and whether the owner edited it, and the facts
 * he removed that are still shown), his note (D90), when the span starts, and the conversation since the last rewrite,
 * word for word — never the activity record. The reply is pinned by a strict schema and checked again on the phone
 * ([SheetResponse], [Sheets.afterRewrite]).
 */
object SheetPrompt {

    internal val INSTRUCTIONS = """
        You are a walking and running trainer for one person, and you keep a short sheet of what you know about them:
        facts learned from your conversations with them. You read this sheet every time you write to them: your
        feedback after a session, their plans, your note on their week, your weekly letter and your replies.

        You are given: sheet.facts, the sheet as it is now, each fact with its id, its group, its text, since (the day
        it became true), its source ("they_said" or "you_noticed") and edited_by_them (true when they changed its words
        themselves); sheet.removed_by_them, facts they removed from the sheet themselves; about_me, their own standing
        note about themselves, which they write and you never do; since, when this conversation span starts (null: the
        start of the conversation); and conversation, everything said since then, word for word, oldest first: their
        messages ("from": "them"), your replies ("from": "you"), and, in their place, your feedback after a session and
        your weekly letters, each with its local time ("at").

        Rewrite the whole sheet from the sheet as it is and the conversation since. Update facts: keep what is still
        true, change what has changed, add what is new, and leave out what is no longer true. Never summarise the
        sheet or the conversation; each fact is one short sentence that stands on its own, written to them in the
        second person.
        - A fact they edited (edited_by_them) is their own words: keep it as it is, unless the conversation since
          says otherwise. Do not bring back a fact they removed, unless the conversation since says it is true again.
        - Groups: "body" for aches, injuries and illness; "training" for how they like to train (times, kinds of
          session, equipment, what they enjoy or avoid); "goals" for goals in their own words; "life" for what
          affects their training: travel, a busy month, work, family.
        - source "they_said" only for what they actually said in their own messages; "you_noticed" for anything
          you inferred, from what they said or from your feedback and letters. Never mark an inference as said.
        - Never invent a figure: a number appears in a fact only when it was said in the conversation.
        - Nothing that is already in about_me, and nothing about a single meal or food.
        - since is the day the fact became true, as YYYY-MM-DD, never after today; for a fact you keep unchanged,
          keep its since.
        - Give a kept or changed fact its id; a new fact has id null.
        - At most ${Sheets.MAX_FACTS} facts and ${Sheets.MAX_CHARS} characters in all; when there is more, keep what
          is most useful to a coach, most useful first within each group.
        - ${TrainerPrompt.OWN_CHOICE}
        - When nothing has changed, return the sheet as it is.

        Reply with facts: every fact of the new sheet.
    """.trimIndent()

    fun body(model: String, request: SheetRewriteRequest, profile: RequestProfile = RequestProfile.guess(model)): String =
        ChatRequest.body(
            model, profile,
            listOf(ChatRequest.Message("system", INSTRUCTIONS), ChatRequest.Message("user", user(request).toString())),
            "trainer_sheet", SCHEMA,
        )

    private fun user(request: SheetRewriteRequest): JsonObject = buildJsonObject {
        put("today", TrainerPrompt.date(request.today))
        put("since", request.since?.let { JsonPrimitive(TrainerPrompt.at(it)) } ?: JsonNull)
        putJsonObject("sheet") {
            putJsonArray("facts") {
                request.facts.filter { it.current }.forEach { fact ->
                    add(
                        buildJsonObject {
                            put("id", fact.id)
                            put("group", group(fact.group))
                            put("text", fact.text)
                            put("since", TrainerPrompt.date(fact.sinceEpochDay))
                            put("source", source(fact.source))
                            put("edited_by_them", fact.edited)
                        },
                    )
                }
            }
            putJsonArray("removed_by_them") {
                request.facts.filter { it.removedByOwner && it.droppedEpochDay != null }.forEach { fact ->
                    add(
                        buildJsonObject {
                            put("group", group(fact.group))
                            put("text", fact.text)
                            put("removed", TrainerPrompt.date(fact.droppedEpochDay!!))
                        },
                    )
                }
            }
        }
        put("about_me", request.aboutMe?.let(::JsonPrimitive) ?: JsonNull)
        putJsonArray("conversation") { request.conversation.forEach { add(TrainerPrompt.lineJson(it)) } }
    }

    /** D151: the sheet as every other request sends it — the current facts, or null when there are none. */
    fun sheetJson(facts: List<SheetFact>?): JsonElement {
        val current = facts?.filter { it.current }.orEmpty()
        if (current.isEmpty()) return JsonNull
        return JsonArray(
            current.map { fact ->
                buildJsonObject {
                    put("group", group(fact.group))
                    put("text", fact.text)
                    put("since", TrainerPrompt.date(fact.sinceEpochDay))
                    put("source", source(fact.source))
                }
            },
        )
    }

    internal fun group(group: FactGroup): String = when (group) {
        FactGroup.BODY -> "body"
        FactGroup.TRAINING -> "training"
        FactGroup.GOALS -> "goals"
        FactGroup.LIFE -> "life"
    }

    internal fun groupOf(name: String): FactGroup? = FactGroup.entries.firstOrNull { group(it) == name }

    internal fun source(source: FactSource): String = when (source) {
        FactSource.SAID -> "they_said"
        FactSource.NOTICED -> "you_noticed"
    }

    internal fun sourceOf(name: String): FactSource? = FactSource.entries.firstOrNull { source(it) == name }

    private fun string() = buildJsonObject { put("type", "string") }

    private fun enumOf(values: List<String>) = buildJsonObject {
        put("type", "string")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    private fun strictObject(vararg properties: Pair<String, JsonObject>) = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonObject("properties") { properties.forEach { (name, schema) -> put(name, schema) } }
        putJsonArray("required") { properties.forEach { add(it.first) } }
    }

    /** D150: the whole new sheet — each fact with the id it continues (or null), its group, text, since and source. */
    internal val SCHEMA: JsonObject = strictObject(
        "facts" to buildJsonObject {
            put("type", "array")
            put(
                "items",
                strictObject(
                    "id" to buildJsonObject {
                        putJsonArray("anyOf") {
                            add(buildJsonObject { put("type", "integer") })
                            add(buildJsonObject { put("type", "null") })
                        }
                    },
                    "group" to enumOf(FactGroup.entries.map(::group)),
                    "text" to string(),
                    "since" to string(),
                    "source" to enumOf(FactSource.entries.map(::source)),
                ),
            )
        },
    )
}
