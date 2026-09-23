package com.metaself.app.domain.ai

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.FactGroup
import com.metaself.app.domain.food.FoodFacts
import com.metaself.app.domain.food.FoodForm
import com.metaself.app.domain.food.FoodKeys
import com.metaself.app.domain.food.FormOrigins
import com.metaself.app.domain.food.Nutrients

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
 *   because a named unit is what lets the model fill them.
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
            accepted: Map<FactGroup, Confidence>,
        ): ReviewRequest {
            val origins = FormOrigins.of(stored, form, accepted)
            fun held(figures: Nutrients?, origin: FormOrigins.Origin?): HeldGroup? =
                if (figures == null || origin == null) {
                    null
                } else {
                    HeldGroup(figures, origin.source, origin.confidence)
                }

            val weight = form.weightFigure()
            val weightOrigin = origins.gramsPerUnit
            return ReviewRequest(
                process = process,
                name = form.name.trim(),
                brand = form.brand.trim()
                    .takeIf { it.isNotEmpty() && FoodKeys.brandKey(it) != FoodKeys.NO_BRAND_KEY }
                    .orEmpty(),
                per100g = held(form.per100gFigures(), origins.per100g),
                unitName = form.unitName.trim(),
                perUnit = held(form.perUnitFigures(), origins.perUnit),
                gramsPerUnit = if (weight == null || weightOrigin == null) {
                    null
                } else {
                    HeldWeight(weight, weightOrigin.source)
                },
            )
        }
    }
}
