package com.metaself.app.domain.ai

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.AcceptedGroup
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.FormOrigins
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.PerHundredMillilitres

/** Which editor asked for a review (D54 §1). Joining two foods is reserved for later. */
enum class ReviewProcess {
    /** The meal builder's *Make a food* panel: a food not yet made. */
    NEW_FOOD,

    /** My foods' editor: a food that exists. */
    EXISTING_FOOD,
}

/** Four figures the form holds for one group, and where they came from (D54 §2). */
data class HeldGroup(val nutrients: Nutrients, val source: Source, val confidence: Confidence?)

/** What one weighs, and where it came from. Sent as context; a review cannot answer it. */
data class HeldWeight(val grams: Double, val source: Source)

/**
 * Everything a review sends — one food, as the editor holds it, and nothing else (D54 §2, D16 as
 * amended).
 *
 * No other food, no alias, no barcode, no id, no date, no history of eating it, nothing about the
 * owner. The fields below are the whole of it, and `ReviewPromptTest` fails if one is added.
 *
 * @property brand the brand box, or "" for no brand (D41's `NA` spellings included).
 * @property unitName what "one" is, from the unit box, or "". Sent even with no per-one figures,
 *   because a named unit is what lets the model fill them. "100 ml" for a food counted in
 *   millilitres, whose per-one figures are then sent per 100 ml (D56).
 */
data class ReviewRequest(
    val process: ReviewProcess,
    val name: String,
    val brand: String,
    val per100g: HeldGroup?,
    val unitName: String,
    val perUnit: HeldGroup?,
    val gramsPerUnit: HeldWeight?,
) {
    /**
     * True when the per-one group is per 100 ml — a food counted in millilitres (D56) — so its
     * answer is judged by the per-100 ceilings, as its boxes are. Worked out, never sent.
     */
    val perUnitPer100Ml: Boolean get() = unitName == PerHundredMillilitres.PER

    companion object {
        /**
         * The form as it stands when he asks — not the stored food, since he may have typed since
         * opening it — each group with the origin Save would give it ([FormOrigins]).
         *
         * Takes the stored food's facts rather than the food, so its aliases, barcode, id and dates
         * are not within reach.
         *
         * @param stored the stored food's facts, or null for a food not yet made.
         * @param accepted the groups accepted from a review in this editing session.
         */
        fun of(
            process: ReviewProcess,
            form: FoodForm,
            stored: FoodFacts?,
            accepted: Map<FactGroup, AcceptedGroup>,
        ): ReviewRequest {
            val origins = FormOrigins.of(stored, form, accepted)
            fun held(figures: Nutrients?, origin: FormOrigins.Origin?): HeldGroup? =
                if (figures == null || origin == null) {
                    null
                } else {
                    HeldGroup(figures, origin.source, origin.confidence)
                }

            // A food counted in millilitres is reviewed per 100 ml, as its boxes show it (D56): the
            // model reads the carton's figures, and a change rounded to one decimal (§3) is rounded
            // at that scale, not per one ml. What one ml weighs is a density, never assumed (D4), and
            // the cross-check it would feed has no meaning for it, so it is not sent.
            val millilitres = form.perHundredMl
            val weight = form.weightFigure(stored?.gramsPerUnit?.grams).takeUnless { millilitres }
            val weightOrigin = origins.gramsPerUnit
            return ReviewRequest(
                process = process,
                name = form.name.trim(),
                brand = form.brand.trim()
                    .takeIf { it.isNotEmpty() && FoodKeys.brandKey(it) != FoodKeys.NO_BRAND_KEY }
                    .orEmpty(),
                per100g = held(form.per100gFigures(stored?.per100g?.nutrients), origins.per100g),
                unitName = if (millilitres) PerHundredMillilitres.PER else form.unitName.trim(),
                perUnit = held(form.perUnitFiguresAsShown(stored?.perUnit?.nutrients), origins.perUnit),
                gramsPerUnit = if (weight == null || weightOrigin == null) {
                    null
                } else {
                    HeldWeight(weight, weightOrigin.source)
                },
            )
        }
    }
}

/** One of a group's four figures. */
enum class Figure { KCAL, PROTEIN, CARBS, FAT }

/** One figure the review would change, from what the group holds, and why (D54 §3, §4). */
data class FigureChange(val figure: Figure, val from: Double, val to: Double, val reason: String)

/**
 * What a review suggests for one group, ready to be shown and, if he accepts, written into its four
 * boxes (D54 §4).
 *
 * @property nutrients the whole group as it would stand: figures kept exactly as held, changed or
 *   filled ones rounded to one decimal place.
 * @property filled true when the form did not know the group; then [changes] is empty and [reason]
 *   is the group's one reason. Otherwise [changes] holds at least one change and [reason] is null.
 * @property keptFrom where the figures this suggestion keeps came from — the source the group was
 *   sent with — or null when it keeps none: a fill, or all four changed. Accepting it labels the
 *   group by its weakest member (D54 §5 as amended 2026-09-24), so the kept figures' source is needed.
 */
data class Suggestion(
    val nutrients: Nutrients,
    val confidence: Confidence,
    val filled: Boolean,
    val changes: List<FigureChange>,
    val reason: String?,
    val keptFrom: Source? = null,
)

/**
 * What the model concluded about the food as a whole, in its own required field (D54 §10.3) —
 * read from there, never out of the note's prose, so the line under the button cannot say
 * "no changes suggested" over a note that names a problem.
 */
enum class Verdict {
    /** The model found nothing wrong. */
    CONSISTENT,

    /** The model found something wrong, missing or contradictory, whether or not it proposed a fix. */
    PROBLEM_FOUND,
}

/**
 * A review's answer, a group at a time. A group with nothing to show — kept exactly, left alone, or
 * set aside — is null.
 *
 * @property setAside the groups whose suggestion could not be used (D54 §3), each said on screen in
 *   one line; the group stays as it was.
 * @property verdict what the model concluded (§10.3). [ReviewResponse] always sets it from the
 *   reply; the default is for a review built by hand.
 */
data class FoodReview(
    val per100g: Suggestion?,
    val perUnit: Suggestion?,
    val note: String?,
    val setAside: List<FactGroup>,
    val verdict: Verdict = Verdict.CONSISTENT,
) {
    fun suggestionFor(group: FactGroup): Suggestion? = when (group) {
        FactGroup.PER_100G -> per100g
        FactGroup.PER_UNIT -> perUnit
    }
}

/**
 * What came back from a review, or what went wrong.
 *
 * A failure is one of [EstimateResult]'s, so the editor says it in `ProposalWording.failure`'s
 * existing sentences and there is one closed set of failures for everything that asks the model.
 *
 * [raw] is the model's reply as it came — the message's content, or the whole body when there is
 * no content to find — for **Show the model's answer** (D54 §8.4). Null when no reply arrived. It
 * is shown on screen and nowhere else: never stored, never logged, since it can hold the food's
 * name.
 */
sealed interface ReviewResult {

    val raw: String?

    data class Proposed(val review: FoodReview, override val raw: String? = null) : ReviewResult

    /**
     * The answer arrived in the shape asked for, and every group it changed was set aside, so there
     * is nothing to accept (D54 §8.3). Not a failure to understand it, and not said as one: the
     * editor says the answer could not be used, with [review]'s set-aside lines and note.
     */
    data class Unusable(val review: FoodReview, override val raw: String? = null) : ReviewResult

    /** [failure] is NoKey, CeilingReached, Unreachable, Refused or Unreadable — never a proposal. */
    data class Failed(val failure: EstimateResult, override val raw: String? = null) : ReviewResult {
        init {
            require(failure !is EstimateResult.Proposed && failure !is EstimateResult.AmountMissing) {
                "a review fails for want of a key, allowance, network, permission or sense; not $failure"
            }
        }
    }
}

/**
 * Reviewing one food's figures — D54's narrow interface, beside [MealEstimator] (D2 as amended).
 *
 * One call, no retry, and nothing in its vocabulary that names a vendor.
 */
interface FoodReviewer {
    suspend fun review(request: ReviewRequest): ReviewResult
}
