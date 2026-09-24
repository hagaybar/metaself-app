package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.ReplacedFacts
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToLong

/**
 * A review's answer, read strictly, a group at a time (D54 §3) — [EstimateResponse]'s twin.
 *
 * Parsed inside `runCatching`, so a malformed body is [EstimateResult.Unreadable], never an
 * exception crossing the seam.
 *
 * - `null` for a group means leave it as it is; a reply cannot remove a group.
 * - `per_unit` is ignored when the editor names no unit.
 * - A figure equal to the one held (D45's comparison) is kept **exactly as held**: a model echoing
 *   3.25 does not turn a label's 3.25 into 3.3. So is one equal to it once both are rounded to one
 *   decimal: 8.57 or 8.6 given back for a held 8.571428571428571 is an echo, not a change.
 * - A figure that differs is a change, rounded to one decimal place — a guess claims no finer
 *   precision — and carries a reason: its own, or else the first non-blank reason in its group. A
 *   group the form did not know is a fill, rounded the same way, and needs at least one non-blank
 *   reason.
 * - **A group is set aside whole, never repaired**, when a figure is missing, not finite, negative
 *   or past D42's ceiling for its basis, or when a change or fill has no reason anywhere in its
 *   group. If every group that changed was set aside, the answer is [ReviewResult.Unusable] —
 *   it arrived and was read; what it suggested could not be used.
 */
object ReviewResponse {

    private val json = Json { ignoreUnknownKeys = true }

    private val FIGURES = listOf(
        Triple(Figure.KCAL, "kcal", "kcal_reason"),
        Triple(Figure.PROTEIN, "protein_g", "protein_reason"),
        Triple(Figure.CARBS, "carbs_g", "carbs_reason"),
        Triple(Figure.FAT, "fat_g", "fat_reason"),
    )

    /** What a group's answer came to. */
    private sealed interface Read {
        /** Nothing to show: left alone, or every figure kept. */
        data object Unchanged : Read
        data class Suggested(val suggestion: Suggestion) : Read
        data object SetAside : Read
    }

    fun parse(body: String, request: ReviewRequest): ReviewResult = runCatching {
        val content = json.parseToJsonElement(body)
            .jsonObject["choices"]!!.jsonArray
            .first().jsonObject["message"]!!.jsonObject["content"]!!
            .jsonPrimitive.content
        val payload = json.parseToJsonElement(content).jsonObject

        // Both groups are required by the schema; one that is absent is not the shape asked for.
        val per100g = read(
            payload.getValue("per_100g"),
            request.per100g,
            BelievableAmount.KCAL_PER_100G,
            BelievableAmount.MACRO_PER_100G,
        )
        val perUnit = if (request.unitName.isBlank()) {
            payload.getValue("per_unit")
            Read.Unchanged
        } else {
            read(
                payload.getValue("per_unit"),
                request.perUnit,
                BelievableAmount.KCAL_PER_UNIT,
                BelievableAmount.MACRO_PER_UNIT,
            )
        }

        val setAside = listOf(FactGroup.PER_100G to per100g, FactGroup.PER_UNIT to perUnit)
            .filter { it.second == Read.SetAside }
            .map { it.first }
        val review = FoodReview(
            per100g = (per100g as? Read.Suggested)?.suggestion,
            perUnit = (perUnit as? Read.Suggested)?.suggestion,
            note = payload["note"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            setAside = setAside,
        )

        if (setAside.isNotEmpty() && review.per100g == null && review.perUnit == null) {
            ReviewResult.Unusable(review)
        } else {
            ReviewResult.Proposed(review)
        }
    }.getOrElse {
        ReviewResult.Failed(EstimateResult.Unreadable("the reply was not in the shape this app asked for"))
    }

    /**
     * One group's answer against what the form [held] for it, judged by the ceilings of its basis.
     *
     * @throws IllegalArgumentException (caught by [parse]) when the group is neither null nor an
     *   object: not the shape asked for, so the whole reply is.
     */
    private fun read(answer: JsonElement, held: HeldGroup?, kcalMost: Double, macroMost: Double): Read {
        if (answer == JsonNull) return Read.Unchanged
        val group = answer.jsonObject

        val figures = FIGURES.map { (_, field, _) ->
            val most = if (field == "kcal") kcalMost else macroMost
            group.figure(field, most) ?: return Read.SetAside
        }
        val reasons = FIGURES.map { (_, _, field) ->
            group[field]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        }
        val confidence = confidenceOf(group["confidence"]?.jsonPrimitive?.contentOrNull)

        if (held == null) {
            val reason = reasons.firstOrNull { it.isNotEmpty() } ?: return Read.SetAside
            val filled = figures.map(::toOneDecimal)
            return Read.Suggested(
                Suggestion(
                    nutrients = Nutrients(filled[0], filled[1], filled[2], filled[3]),
                    confidence = confidence,
                    filled = true,
                    changes = emptyList(),
                    reason = reason,
                ),
            )
        }

        val holds = listOf(
            held.nutrients.kcal, held.nutrients.proteinG, held.nutrients.carbsG, held.nutrients.fatG,
        )
        // A change with no reason of its own borrows the group's first: models often explain two
        // linked changes once. Only a change with no reason anywhere in the group sets it aside.
        val groupReason = reasons.firstOrNull { it.isNotEmpty() }
        val changes = mutableListOf<FigureChange>()
        val result = FIGURES.indices.map { i ->
            val was = holds[i]
            val given = figures[i]
            val rounded = toOneDecimal(given)
            if (kept(was, given)) {
                was
            } else {
                val reason = reasons[i].ifEmpty { groupReason ?: return Read.SetAside }
                changes += FigureChange(FIGURES[i].first, was, rounded, reason)
                rounded
            }
        }
        if (changes.isEmpty()) return Read.Unchanged
        return Read.Suggested(
            Suggestion(
                nutrients = Nutrients(result[0], result[1], result[2], result[3]),
                confidence = confidence,
                filled = false,
                changes = changes,
                reason = null,
                keptFrom = held.source.takeIf { changes.size < FIGURES.size },
            ),
        )
    }

    /** A figure, or null when it is missing, not a finite number, negative, or past [most] (D42). */
    private fun JsonObject.figure(field: String, most: Double): Double? =
        this[field]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?.takeIf { BelievableAmount.isBelievable(it, most) }

    /**
     * The model kept [was] when it gave it back as held, as held to D45's nine significant
     * figures, or as held once both are rounded to one decimal: a food whose figures are a
     * serving's scaled holds 8.571428571428571, and a model echoing it writes 8.57 or 8.6 with no
     * reason, because it changed nothing (D54 as amended 2026-09-24). Read as a change, that
     * missing reason would set the whole group aside.
     */
    private fun kept(was: Double, given: Double): Boolean =
        ReplacedFacts.sameFigure(was, given) ||
            ReplacedFacts.sameFigure(toOneDecimal(was), toOneDecimal(given))

    private fun toOneDecimal(value: Double): Double = (value * 10).roundToLong() / 10.0

    /** Anything unrecognised is LOW — [EstimateResponse]'s rule, and the safe direction. */
    private fun confidenceOf(raw: String?): Confidence = when (raw?.uppercase()) {
        "HIGH" -> Confidence.HIGH
        "MEDIUM" -> Confidence.MEDIUM
        else -> Confidence.LOW
    }
}
