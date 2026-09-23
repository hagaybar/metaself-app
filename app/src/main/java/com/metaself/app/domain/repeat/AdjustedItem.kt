package com.metaself.app.domain.repeat

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.portion.Portions
import kotlin.math.roundToInt

/*
 * Changing how much of something was eaten, on an item already on the record.
 *
 * The same arithmetic as scaling one of the model's proposals, over the same rules, because it is
 * the same question: two slices are one slice twice, and half a risotto is half of everything in
 * it. It is deliberately linear, and therefore handles a bigger bowl and not a richer one.
 *
 * Every function here scales from the item it is given. The screen keeps the item as it was logged
 * and always scales from that, so going back arrives exactly where it started rather than at the
 * compounded rounding of two multiplications.
 */

fun FoodItem.canBeAdjusted(): Boolean = Portions.canScale(portionAmount, portionUnit)

/**
 * Set the amount outright — 1 slice, 0.5 of a dish.
 *
 * An amount of nothing is not a portion; it is an item that should be removed instead, so the
 * smallest a count can go is one and the smallest a mass can go is a tenth of what it was.
 */
fun FoodItem.withAmount(amount: Double): FoodItem {
    if (!canBeAdjusted() || amount <= 0.0) return this
    return scaledBy(amount / portionAmount, amount)
}

/**
 * Less, as logged, or more — the proportions the model's own proposals offer, applied to something
 * eaten before.
 */
fun FoodItem.scaledBy(factor: Double): FoodItem {
    if (!canBeAdjusted() || factor <= 0.0) return this
    if (factor == 1.0) return this
    return scaledBy(factor, portionAmount * factor)
}

private fun FoodItem.scaledBy(factor: Double, amount: Double): FoodItem = copy(
    portionAmount = amount,
    portion = Portions.words(amount, portionUnit),
    kcal = (kcal * factor).roundToInt(),
    proteinG = (proteinG * factor).roundToInt(),
    carbsG = (carbsG * factor).roundToInt(),
    fatG = (fatG * factor).roundToInt(),
)
