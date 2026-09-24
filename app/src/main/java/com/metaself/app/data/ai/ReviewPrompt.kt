package com.metaself.app.data.ai

import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.ReviewProcess
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.day.Source
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * What leaves the phone when he asks for a food's figures to be reviewed (D54 §2).
 *
 * The second thing this app ever sends, and like the first ([EstimatePrompt]) a pure function with
 * its own "nothing else is sent" test. D16 as D54 amends it: one food's name, brand, figures, where
 * each came from, what one is called and what one weighs — only when he presses the button, and
 * nothing about him, his other foods or when he ate.
 *
 * The reply is pinned by a strict schema (§3), with each group nullable. That nullability is written
 * `anyOf: [{the object}, {"type": "null"}]`: OpenAI's structured-outputs guide lists `anyOf` among
 * the supported types, allows it anywhere but the root, requires every field to be `required` and
 * shows optional values as a union with null — its own recursive-schema example is exactly
 * `"next": {"anyOf": [{"$ref": ...}, {"type": "null"}]}` under `strict`.
 *
 * **The schema has no weight field and no unit name**, so a reply cannot carry a guess at what one
 * piece weighs whatever the model makes of the instructions.
 */
object ReviewPrompt {

    private val INSTRUCTIONS = """
        You review the nutrition figures of one food, as a nutritionist would, and return a complete
        set: keep what is right, fill what is missing, replace what is wrong.

        You are given the food's name and brand, its figures per 100 g, what one of it is called and
        its figures for one, and what one of it weighs in grams. A group that is not known is null.

        Where each figure came from matters:
        - LABEL is printed on the packet and is near-certain. Keep it unless the figures are
          internally impossible: the energy of the macros, at
          4 kcal per gram of protein or carbohydrate, 9 per gram of fat,
          far from the calories, beyond what fibre, alcohol or rounding explain; or more than 100 g
          of macros in 100 g. If you change a LABEL figure, say why.
        - TYPED is the owner's own number. Change it only when it is clearly wrong, and say why.
        - AI_ESTIMATE is an earlier guess, with its confidence, and may be improved.
        - REPEATED was copied from a past meal and its origin is unknown.
        - UNKNOWN is a figure of unknown origin.

        Rules:
        - Never state what one piece weighs, and never name a unit. The figures for one are for the
          unit named, and only when one is named; if no unit is named, return null for per_unit.
          "portion" means an unnamed serving whose size nobody recorded: change its figures only if
          they are impossible.
        - Return a group as null to leave it exactly as it is. Otherwise return all four figures of
          the group, the ones you keep exactly as given.
        - Every figure is a single number, never a range.
        - For each figure you change, give a short reason, one sentence. For a figure you keep, the
          reason is empty. For a group you fill that was null, give one short reason for the group in
          kcal_reason and leave the other reasons empty.
        - Give a confidence for each group you return: LOW, MEDIUM or HIGH.
        - Add at most one short note overall, or leave it empty.
        - Reply in the language of the food's name.
    """.trimIndent()

    /** The whole request: the instructions, the one food as JSON, and the reply's schema. */
    fun requestBody(model: String, request: ReviewRequest): String = buildJsonObject {
        put("model", model)
        put("temperature", 0)
        putJsonArray("messages") {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", INSTRUCTIONS)
                },
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", food(request).toString())
                },
            )
        }
        putJsonObject("response_format") {
            put("type", "json_schema")
            putJsonObject("json_schema") {
                put("name", "food_review")
                put("strict", true)
                put("schema", SCHEMA)
            }
        }
    }.toString()

    /** The user message, in exactly the spec's shape (§2). */
    private fun food(request: ReviewRequest): JsonObject = buildJsonObject {
        put(
            "process",
            when (request.process) {
                ReviewProcess.NEW_FOOD -> "new_food"
                ReviewProcess.EXISTING_FOOD -> "existing_food"
            },
        )
        put("name", request.name)
        put("brand", request.brand)
        put("per_100g", request.per100g?.let(::group) ?: JsonNull)
        put("unit_name", request.unitName)
        put("per_unit", request.perUnit?.let(::group) ?: JsonNull)
        put(
            "grams_per_unit",
            request.gramsPerUnit?.let { weight ->
                buildJsonObject {
                    put("grams", weight.grams)
                    put("source", sourceName(weight.source))
                }
            } ?: JsonNull,
        )
    }

    private fun group(held: HeldGroup): JsonObject = buildJsonObject {
        put("kcal", held.nutrients.kcal)
        put("protein_g", held.nutrients.proteinG)
        put("carbs_g", held.nutrients.carbsG)
        put("fat_g", held.nutrients.fatG)
        put("source", sourceName(held.source))
        put("confidence", held.confidence?.name)
    }

    /** One of the five names the instructions explain; a source this version cannot read is UNKNOWN. */
    private fun sourceName(source: Source): String = when (source) {
        Source.LABEL -> "LABEL"
        Source.TYPED -> "TYPED"
        Source.AI_ESTIMATE -> "AI_ESTIMATE"
        Source.REPEATED -> "REPEATED"
        Source.UNRECOGNISED -> "UNKNOWN"
    }

    private val SUGGESTION: JsonObject = buildJsonObject {
        val figures = listOf("kcal", "protein_g", "carbs_g", "fat_g")
        val reasons = listOf("kcal_reason", "protein_reason", "carbs_reason", "fat_reason")
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { (figures + reasons + "confidence").forEach { add(it) } }
        putJsonObject("properties") {
            // Numbers, not integers: a figure keeps its decimals.
            figures.forEach { putJsonObject(it) { put("type", "number") } }
            reasons.forEach { putJsonObject(it) { put("type", "string") } }
            putJsonObject("confidence") {
                put("type", "string")
                putJsonArray("enum") { add("LOW"); add("MEDIUM"); add("HIGH") }
            }
        }
    }

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("per_100g"); add("per_unit"); add("note") }
        putJsonObject("properties") {
            listOf("per_100g", "per_unit").forEach { group ->
                putJsonObject(group) {
                    putJsonArray("anyOf") {
                        add(SUGGESTION)
                        add(buildJsonObject { put("type", "null") })
                    }
                }
            }
            putJsonObject("note") { put("type", "string") }
        }
    }
}
