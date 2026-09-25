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
 * The reply's `verdict` (D54 §10.3) is a required enum, `consistent` or `problem_found`, so
 * whether the model found a problem is never read out of the note's prose.
 *
 * Since D54 §12 the reply may also carry a name, a unit and what one weighs, each nullable; never a
 * brand. `weightAsked` chooses whether the weight is invited, and is never itself sent.
 */
object ReviewPrompt {

    private val INSTRUCTIONS = """
        You review one food, as a nutritionist would: everything the owner's food page holds except
        its brand. That is its name, what one of it is called (the unit), its figures per 100 g, its
        figures for one, and what one of it weighs. Propose a value for anything you judge wrong,
        missing or improvable; return what is right as null, or exactly as given.

        You are given the food's name and brand, its figures per 100 g, what one of it is called and
        its figures for one, and what one of it weighs in grams. A group that is not known is null.

        Where each figure came from matters:
        - LABEL is printed on the packet: the packet's own statement, and strong evidence. Change it
          when you judge it wrong or improvable, and say why. Figures are wrong, for example, when
          the energy of the macros, at
          4 kcal per gram of protein or carbohydrate, 9 per gram of fat,
          is far from the calories, beyond what fibre, alcohol or rounding explain; or when there are
          more than 100 g of macros in 100 g.
        - TYPED is the owner's own number. Change it when you judge it wrong, and say why.
        - AI_ESTIMATE is an earlier guess, with its confidence, and may be improved.
        - REPEATED was copied from a past meal and its origin is unknown.
        - UNKNOWN is a figure of unknown origin.

        The name:
        - You may correct its spelling or make it clearer. It must stay the same food. Never add a
          brand to it or take one out of it; the brand is not yours to change.

        The unit:
        - When no unit is named, you may name the one this food is most often counted in, such as a
          slice, a cup, a tablespoon or a piece, with its figures for one.
        - When one is named, you may propose a better one. Then per_unit must be the figures for
          your unit, all four.
        - Never a unit of mass such as g, 100 g or oz: that is what per 100 g is for. Never
          propose "portion" as a unit.
        - "ml" means the food is counted by volume: then the figures for one are per 100 ml.
        - "portion" means an unnamed serving whose size nobody recorded: you may name a real unit
          for it only with figures for that unit.

        Rules:
        - Return a group as null to leave it exactly as it is. Otherwise return all four figures of
          the group, the ones you keep exactly as given.
        - Every figure is a single number, never a range.
        - For each value you change, give a short reason, one sentence. For a figure you keep, the
          reason is empty. For a group you fill that was null, give one short reason for the group in
          kcal_reason and leave the other reasons empty.
        - Give a confidence for each group you return: LOW, MEDIUM or HIGH.
        - Always write the note: what you concluded, in one sentence. For example, that the figures
          are consistent and kept, or what you changed and why. Never leave the note empty.
        - Give the verdict: "consistent" only when you found nothing wrong, missing or contradictory;
          "problem_found" when you found anything wrong, missing or contradictory, whether or not you
          propose corrected figures.
        - Reply in the language of the food's name.
    """.trimIndent()

    /**
     * What one weighs may be proposed only where the editor has a box for it (D54 §12.2); the app
     * never works one out, so this is the one way an estimate of it can arise, and it is shown to
     * him as a suggestion before anything is kept.
     */
    private val WEIGHT_ASKED = """
        What one weighs:
        - You may propose what one of the unit weighs, in grams, as a typical figure, with a reason
          and a confidence, in grams_per_unit. It will be shown to the owner as a suggestion and kept
          as an estimate.
        - If you propose a different unit and grams_per_unit is given, grams_per_unit must be what
          one of your unit weighs; give the same number if it still holds.
        - Never say what one weighs for a food counted in ml: return grams_per_unit as null.
    """.trimIndent()

    private val WEIGHT_NOT_ASKED = """
        What one weighs: return grams_per_unit as null.
    """.trimIndent()

    /**
     * Sent only when both groups and what one weighs are held (D54 §9.2, as amended by §10.1 and
     * §12.5): then the three can be checked against each other. When they disagree, one of them is
     * wrong, and the model proposes the correction for the one it believes wrong — either group, a
     * label included, or the weight — always as a suggestion he accepts or cancels.
     */
    private fun crossCheck(unit: String) = """
        Cross-check: per 100 g, per $unit and grams_per_unit are all given, so check them against
        each other. The figures per $unit should equal the figures per 100 g times grams_per_unit / 100,
        within label rounding. If they disagree beyond rounding, at least one of the three is wrong.
        Decide which you believe is wrong — either group, even a LABEL group, or grams_per_unit — and
        propose the correction for that one, with a reason for each value you change. Say in the
        note what you believe and why.
    """.trimIndent()

    /**
     * The note and the reasons are drawn on screen as they come (D54 §10.2), so they are asked for
     * in the words the editor uses — "per 100 g", "per" the food's own unit — and never in the
     * request's field names, which a model otherwise quotes back ("the per_unit figures").
     */
    private fun plainWords(unit: String?): String {
        val per = if (unit == null) "say \"per 100 g\"" else "say \"per 100 g\" and \"per $unit\""
        return "- Write the note and every reason in plain words, for the owner to read: $per; " +
            "never write field names such as per_unit, per_100g, grams_per_unit or kcal_reason, " +
            "and never write \"the figures for one\"."
    }

    /**
     * The instructions for [request]: the plain-words rule, with the food's own unit name when one
     * is named, and the cross-check only when there is something to check.
     */
    private fun instructions(request: ReviewRequest): String {
        val unit = request.unitName.trim().takeIf { it.isNotEmpty() }
        val weight = if (request.weightAsked) WEIGHT_ASKED else WEIGHT_NOT_ASKED
        val base = INSTRUCTIONS + "\n" + plainWords(unit) + "\n\n" + weight
        return if (request.per100g != null && request.perUnit != null && request.gramsPerUnit != null) {
            base + "\n\n" + crossCheck(unit ?: "one")
        } else {
            base
        }
    }

    /**
     * The whole request: the instructions, the one food as JSON, and the reply's schema, sent as
     * [profile] says (D57) — by default the first guess for [model].
     */
    fun requestBody(
        model: String,
        request: ReviewRequest,
        profile: RequestProfile = RequestProfile.guess(model),
    ): String = ChatRequest.body(
        model = model,
        profile = profile,
        messages = listOf(
            ChatRequest.Message("system", instructions(request)),
            ChatRequest.Message("user", food(request).toString()),
        ),
        schemaName = "food_review",
        schema = SCHEMA,
    )

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

    /** `{value, reason}`: a name or a unit proposed, and why (D54 §12.3). */
    private val PROPOSED_TEXT: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("value"); add("reason") }
        putJsonObject("properties") {
            putJsonObject("value") { put("type", "string") }
            putJsonObject("reason") { put("type", "string") }
        }
    }

    /** `{grams, reason, confidence}`: what one weighs, proposed (D54 §12.3). */
    private val PROPOSED_WEIGHT: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") { add("grams"); add("reason"); add("confidence") }
        putJsonObject("properties") {
            putJsonObject("grams") { put("type", "number") }
            putJsonObject("reason") { put("type", "string") }
            putJsonObject("confidence") {
                put("type", "string")
                putJsonArray("enum") { add("LOW"); add("MEDIUM"); add("HIGH") }
            }
        }
    }

    private fun nullable(option: JsonObject): JsonObject = buildJsonObject {
        putJsonArray("anyOf") {
            add(option)
            add(buildJsonObject { put("type", "null") })
        }
    }

    private val SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        putJsonArray("required") {
            listOf("name", "unit_name", "per_100g", "per_unit", "grams_per_unit", "note", "verdict")
                .forEach { add(it) }
        }
        putJsonObject("properties") {
            put("name", nullable(PROPOSED_TEXT))
            put("unit_name", nullable(PROPOSED_TEXT))
            put("per_100g", nullable(SUGGESTION))
            put("per_unit", nullable(SUGGESTION))
            put("grams_per_unit", nullable(PROPOSED_WEIGHT))
            // A verdict on every reply (§9.3). Strict structured outputs take no length constraint,
            // so "never empty" is asked for here and in the instructions, and an empty one is read.
            putJsonObject("note") {
                put("type", "string")
                put("description", "What you concluded, in one sentence. Never empty.")
            }
            putJsonObject("verdict") {
                put("type", "string")
                putJsonArray("enum") { add("consistent"); add("problem_found") }
            }
        }
    }
}
