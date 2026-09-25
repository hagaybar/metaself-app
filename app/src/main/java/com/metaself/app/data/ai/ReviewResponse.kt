package com.metaself.app.data.ai

import com.metaself.app.domain.ai.EstimateResult
import com.metaself.app.domain.ai.Figure
import com.metaself.app.domain.ai.FigureChange
import com.metaself.app.domain.ai.FoodReview
import com.metaself.app.domain.ai.HeldGroup
import com.metaself.app.domain.ai.NameSuggestion
import com.metaself.app.domain.ai.ReviewItem
import com.metaself.app.domain.ai.ReviewRequest
import com.metaself.app.domain.ai.ReviewResult
import com.metaself.app.domain.ai.Suggestion
import com.metaself.app.domain.ai.UnitSuggestion
import com.metaself.app.domain.ai.Verdict
import com.metaself.app.domain.ai.WeightSuggestion
import com.metaself.app.domain.amount.BelievableAmount
import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredMillilitres
import com.metaself.app.domain.food.ReplacedFacts
import com.metaself.app.domain.portion.Portions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToLong

/**
 * A review's answer, read strictly, a group at a time (D54 §3) — [EstimateResponse]'s twin.
 *
 * Parsed inside `runCatching`, so a malformed body is [EstimateResult.Unreadable], never an
 * exception crossing the seam.
 *
 * - `null` for a group means leave it as it is; a reply cannot remove a group.
 * - `per_unit` with no unit named, and none proposed, is set aside (D54 §12.4).
 * - A name, a unit and what one weighs may be proposed (§12.4); see [answer].
 * - `verdict` is [Verdict.CONSISTENT] only when it says "consistent" (§10.3).
 * - A figure equal to the one held (D45's comparison) is kept **exactly as held**: a model echoing
 *   3.25 does not turn a label's 3.25 into 3.3. So is one that is the held figure written at the
 *   model's own precision — 8.57 or 8.6 given back for a held 8.571428571428571 is an echo, not a
 *   change ([kept]).
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
        data class Named(val suggestion: NameSuggestion) : Read
        data class Weighed(val suggestion: WeightSuggestion) : Read
        data object SetAside : Read
    }

    fun parse(body: String, request: ReviewRequest): ReviewResult {
        val content = runCatching {
            json.parseToJsonElement(body)
                .jsonObject["choices"]!!.jsonArray
                .first().jsonObject["message"]!!.jsonObject["content"]!!
                .jsonPrimitive.content
        }.getOrNull()
        // What *Show the model's answer* shows (D54 §8.4): the content, or the body when there is none.
        val raw = content ?: body
        return runCatching { answer(content!!, request) }.getOrElse {
            ReviewResult.Failed(
                EstimateResult.Unreadable("the reply was not in the shape this app asked for"),
                raw,
            )
        }
    }

    /**
     * The message's [content], read against what was asked; it is also what goes as the raw reply.
     *
     * Four items, each used whole or set aside whole (D54 §12.4): the name, per 100 g, the per-one
     * bundle (the unit and its four figures, with the weight when the unit moves), and the weight.
     * `per_100g` and `per_unit` are required, as they always were; the three fields §12 added are
     * read as null when absent, so an answer in the older shape still reads.
     */
    private fun answer(content: String, request: ReviewRequest): ReviewResult {
        val payload = json.parseToJsonElement(content).jsonObject

        // Both groups are required by the schema; one that is absent is not the shape asked for.
        val per100g = read(
            payload.getValue("per_100g"),
            request.per100g,
            BelievableAmount.KCAL_PER_100G,
            BelievableAmount.MACRO_PER_100G,
        )
        val perUnitAnswer = payload.getValue("per_unit")
        val name = readName(payload["name"].orNull(), request)
        val weightAnswer = payload["grams_per_unit"].orNull()
        val bundle = readBundle(payload["unit_name"].orNull(), perUnitAnswer, weightAnswer, request)
        val weight = when {
            bundle.weight != null -> bundle.weight
            else -> readWeight(weightAnswer, request, bundle.unitThatStands)
        }

        val setAside = buildList {
            if (name == Read.SetAside) add(ReviewItem.NAME)
            if (per100g == Read.SetAside) add(ReviewItem.PER_100G)
            if (bundle.setAside) add(ReviewItem.PER_UNIT)
            if (weight == Read.SetAside) add(ReviewItem.WEIGHT)
        }
        val review = FoodReview(
            per100g = (per100g as? Read.Suggested)?.suggestion,
            perUnit = bundle.perUnit,
            note = payload["note"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
            setAside = setAside,
            verdict = verdictOf(payload["verdict"]?.jsonPrimitive?.contentOrNull),
            name = (name as? Read.Named)?.suggestion,
            unit = bundle.unit,
            weight = (weight as? Read.Weighed)?.suggestion,
            weightInBundle = bundle.unit != null && weight is Read.Weighed,
        )

        return if (setAside.isNotEmpty() && !review.suggestsAnything) {
            ReviewResult.Unusable(review, content)
        } else {
            ReviewResult.Proposed(review, content)
        }
    }

    private fun JsonElement?.orNull(): JsonElement? = this?.takeUnless { it == JsonNull }

    /** The name (§12.4, 1): an echo is kept; a change needs a reason and a name the app can hold. */
    private fun readName(answer: JsonElement?, request: ReviewRequest): Read {
        val given = answer ?: return Read.Unchanged
        val proposal = given.jsonObject
        val value = proposal["value"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val reason = proposal["reason"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (value == request.name.trim()) return Read.Unchanged
        // Only case or spacing apart, and nothing said about it: an echo, not a proposal.
        if (reason.isEmpty() && folded(value) == folded(request.name)) return Read.Unchanged
        if (reason.isEmpty()) return Read.SetAside
        if (runCatching { FoodKeys.nameKey(value) }.isFailure) return Read.SetAside
        return Read.Named(NameSuggestion(value, reason))
    }

    /**
     * What the per-one bundle came to (§12.4, 3).
     *
     * @property unitThatStands the unit the form will hold if the answer is taken: the one proposed,
     *   else the one named, else null.
     * @property weight the weight's reading when the bundle decided it (a unit that moved); null
     *   when the weight is read on its own.
     */
    private class Bundle(
        val unit: UnitSuggestion?,
        val perUnit: Suggestion?,
        val setAside: Boolean,
        val unitThatStands: String?,
        val weight: Read?,
    )

    private fun readBundle(
        unitAnswer: JsonElement?,
        perUnitAnswer: JsonElement,
        weightAnswer: JsonElement?,
        request: ReviewRequest,
    ): Bundle {
        val held = request.unitName.trim()
        val heldIsMl = request.perUnitPer100Ml
        val proposal = unitAnswer?.jsonObject
        val value = proposal?.get("value")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            // What §2 sends a food counted in ml as, and what the one-tap switch writes (D56).
            .let { if (it.equals(PerHundredMillilitres.PER, ignoreCase = true)) PerHundredMillilitres.UNIT else it }
        val unitReason = proposal?.get("reason")?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val kept = proposal == null || value.isEmpty() ||
            (held.isNotEmpty() && sameUnit(value, held)) ||
            (held.isNotEmpty() && unitReason.isEmpty() && folded(value) == folded(held)) ||
            (heldIsMl && Portions.isMillilitres(value))

        if (kept) {
            if (held.isEmpty()) {
                // Figures of nothing: set aside, where §3 once ignored them silently.
                val setAside = perUnitAnswer != JsonNull
                return Bundle(null, null, setAside, null, null)
            }
            val read = readPerUnit(perUnitAnswer, request.perUnit, heldIsMl)
            return Bundle(
                unit = null,
                perUnit = (read as? Read.Suggested)?.suggestion,
                setAside = read == Read.SetAside,
                // A food counted in ml is sent as "100 ml"; the unit that stands is the millilitre.
                unitThatStands = if (heldIsMl) PerHundredMillilitres.UNIT else held,
                weight = null,
            )
        }

        // A unit named, or a different one proposed.
        // Set aside whole — and a weight given with it was for the unit it proposed, so it goes too.
        val weightGoes = if (weightAnswer != null && request.weightAsked) Read.SetAside else Read.Unchanged
        val aside = Bundle(null, null, setAside = true, unitThatStands = held.ifEmpty { null }, weight = weightGoes)
        val reason = unitReason
        val unitName = runCatching { FoodKeys.displayName(value) }.getOrNull() ?: return aside
        if (reason.isEmpty() || perUnitAnswer == JsonNull) return aside
        val newIsMl = Portions.isMillilitres(unitName)
        // "g", "kg", and "100 g" alike: a mass is what per 100 g is for. The millilitre is a unit.
        val bare = unitName.replace(LEADING_AMOUNT, "")
        if (!newIsMl && (Portions.isMass(unitName) || Portions.isMass(bare))) return aside
        if (unitName.trim().lowercase() == FoodFacts.PORTION) return aside

        // Same side of the millilitre as what was held: the held figures are at the same scale,
        // and one left alone is not new. A unit named, or moved across the millilitre: all new.
        val against = request.perUnit.takeIf { held.isNotEmpty() && heldIsMl == newIsMl }
        val read = readPerUnit(perUnitAnswer, against, newIsMl)
        if (read == Read.SetAside) return aside
        val perUnit = (read as? Read.Suggested)?.suggestion

        // The old unit's weight must not stand under the new unit's name unread (§12.4, 3).
        val heldWeight = request.gramsPerUnit
        val weight: Read? = when {
            newIsMl -> if (weightAnswer != null && request.weightAsked) Read.SetAside else null
            !request.weightAsked -> null
            heldWeight != null && weightAnswer == null -> return Bundle(null, null, true, held, Read.Unchanged)
            weightAnswer != null -> readWeight(weightAnswer, request, unitName).also {
                if (it == Read.SetAside) return Bundle(null, null, true, held, Read.SetAside)
            }
            else -> null
        }
        return Bundle(
            unit = UnitSuggestion(
                unitName = unitName,
                reason = reason,
                confidence = confidenceOf(perUnitAnswer.jsonObject["confidence"]?.jsonPrimitive?.contentOrNull),
                heldSource = against?.source,
            ),
            perUnit = perUnit,
            setAside = false,
            unitThatStands = unitName,
            weight = weight,
        )
    }

    /** A name or unit with case, spacing, marks and punctuation folded away ([FoodKeys.nameKey]). */
    private fun folded(text: String): String? = runCatching { FoodKeys.nameKey(text) }.getOrNull()

    private fun sameUnit(a: String, b: String): Boolean =
        runCatching { FoodKeys.displayName(a) == FoodKeys.displayName(b) }.getOrDefault(false)

    /** Per one, judged by the ceilings of its basis: per 100 for the millilitre (D56), per one otherwise. */
    private fun readPerUnit(answer: JsonElement, held: HeldGroup?, perHundredMl: Boolean): Read = read(
        answer,
        held,
        if (perHundredMl) BelievableAmount.KCAL_PER_100G else BelievableAmount.KCAL_PER_UNIT,
        if (perHundredMl) BelievableAmount.MACRO_PER_100G else BelievableAmount.MACRO_PER_UNIT,
    )

    /**
     * What one weighs (§12.4, 4): read only where the editor has a box for it, for a unit that will
     * stand and is not the millilitre. An echo is kept; anything else needs a reason and a weight
     * above nothing and within D42's ceiling for grams.
     */
    private fun readWeight(answer: JsonElement?, request: ReviewRequest, unit: String?): Read {
        if (!request.weightAsked) return Read.Unchanged
        val given = answer?.jsonObject ?: return Read.Unchanged
        if (unit == null || Portions.isMillilitres(unit)) return Read.SetAside
        val grams = given.figure("grams", BelievableAmount.GRAMS)?.takeIf { it > 0.0 } ?: return Read.SetAside
        val decimals = decimalsOf(given.getValue("grams").jsonPrimitive.content)
        val held = request.gramsPerUnit?.grams
        if (held != null && kept(held, grams, decimals)) return Read.Unchanged
        val reason = given["reason"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (reason.isEmpty()) return Read.SetAside
        val rounded = toOneDecimal(grams).takeIf { it > 0.0 } ?: return Read.SetAside
        return Read.Weighed(
            WeightSuggestion(
                grams = rounded,
                confidence = confidenceOf(given["confidence"]?.jsonPrimitive?.contentOrNull),
                reason = reason,
                from = held,
            ),
        )
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
        // How many decimals the model wrote each figure with: the precision it is judged at.
        val decimals = FIGURES.map { (_, field, _) -> decimalsOf(group.getValue(field).jsonPrimitive.content) }
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
                    heldSource = null,
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
            if (kept(was, given, decimals[i])) {
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
                heldSource = held.source,
            ),
        )
    }

    /** A figure, or null when it is missing, not a finite number, negative, or past [most] (D42). */
    private fun JsonObject.figure(field: String, most: Double): Double? =
        this[field]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
            ?.takeIf { BelievableAmount.isBelievable(it, most) }

    /**
     * The model kept [was] when what it [given] back is [was] written at the model's own precision
     * — the [decimals] it wrote the figure with (D54 §8.1 as amended 2026-09-24). A food whose
     * figures are a serving's scaled holds 4.848484848484849; a model echoing it writes 4.85, or
     * 4.9, or 4.8, with no reason, because it changed nothing. Read as a change, that missing reason
     * would set the whole group aside. Kept, exactly as held, when any of these holds:
     *
     * - equal by D45's nine significant figures;
     * - within half a unit of the given figure's last decimal place (4.85 for 4.8485);
     * - equal to the held figure rounded half up one decimal at a time, from three places down to
     *   the given figure's (4.8485 → 4.848 → 4.85 → 4.9): a model rounding its own echo again;
     * - equal to it once both are rounded to one decimal: a change is rounded to one decimal
     *   (§3), so one that lands on the held figure's tenth could not be shown as a change.
     */
    private fun kept(was: Double, given: Double, decimals: Int): Boolean {
        if (ReplacedFacts.sameFigure(was, given)) return true
        if (ReplacedFacts.sameFigure(toOneDecimal(was), toOneDecimal(given))) return true
        val held = BigDecimal(was.toString())
        val written = BigDecimal(given.toString())
        val halfAUnit = BigDecimal.ONE.movePointLeft(decimals).divide(BigDecimal(2))
        if ((written - held).abs() <= halfAUnit) return true
        var stepped = held.setScale(MOST_DECIMALS, RoundingMode.HALF_UP)
        for (places in MOST_DECIMALS - 1 downTo decimals) {
            stepped = stepped.setScale(places, RoundingMode.HALF_UP)
        }
        return stepped.compareTo(written) == 0
    }

    /**
     * The decimals a figure was written with, from the JSON number's own text: "4.85" is 2, "4.0"
     * is 1, "5" is 0. Capped at [MOST_DECIMALS] — nothing finer is anybody's precision.
     */
    private fun decimalsOf(text: String): Int =
        (text.toBigDecimalOrNull()?.scale() ?: 0).coerceIn(0, MOST_DECIMALS)

    private const val MOST_DECIMALS = 3

    /** A number written before a unit, as in "100 g". */
    private val LEADING_AMOUNT = Regex("""^[\d.,]+\s*""")

    private fun toOneDecimal(value: Double): Double = (value * 10).roundToLong() / 10.0

    /**
     * Only "consistent" is [Verdict.CONSISTENT] (D54 §10.3); anything else, a missing verdict
     * included, is a problem found — the line under the button may say nothing is wrong only when
     * the model said so.
     */
    private fun verdictOf(raw: String?): Verdict =
        if (raw == "consistent") Verdict.CONSISTENT else Verdict.PROBLEM_FOUND

    /** Anything unrecognised is LOW — [EstimateResponse]'s rule, and the safe direction. */
    private fun confidenceOf(raw: String?): Confidence = when (raw?.uppercase()) {
        "HIGH" -> Confidence.HIGH
        "MEDIUM" -> Confidence.MEDIUM
        else -> Confidence.LOW
    }
}
