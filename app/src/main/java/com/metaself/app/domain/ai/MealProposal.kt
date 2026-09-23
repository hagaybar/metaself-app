package com.metaself.app.domain.ai

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source

/**
 * One thing the model thinks was on the plate.
 *
 * [portion] is the sentence the model gave — "1 ball, ~100 g" — and is what goes on the record,
 * because it is what makes the numbers arguable (D5). [portionAmount] and [portionUnit] are the
 * same thing as arithmetic, so that scaling can multiply it; they exist for the buttons and never
 * for display — with one exception: they also choose the plural of the app's own "portion", so
 * the model's "2 portion" reads "2 portions" while "2 portion" is what is saved (D37).
 */
data class ProposedItem(
    val name: String,
    val portion: String,
    val portionAmount: Double,
    val portionUnit: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val confidence: Confidence,
) {
    /** What this becomes if the owner accepts it. */
    fun toFoodItem(): FoodItem = FoodItem(
        name = name,
        portion = portion,
        portionAmount = portionAmount,
        portionUnit = portionUnit,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        source = Source.AI_ESTIMATE,
        confidence = confidence,
    )
}

/**
 * What the model came back with: the components of one meal, and at most one note about its biggest
 * assumption.
 *
 * A list, never a total. A comparison of two models on one real meal showed why: one
 * returned a confident table that had silently left a third of the dish out of it, and a total gives
 * nothing to notice. An omission you can see is a correction; an omission inside one number is a
 * silent error carried for months.
 */
data class MealProposal(
    val items: List<ProposedItem>,
    val note: String?,
) {
    init {
        require(items.isNotEmpty()) { "a proposal cannot be empty" }
    }

    val totalKcal: Int get() = items.sumOf { it.kcal }
}
