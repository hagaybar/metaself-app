package com.metaself.app.domain.ai

import com.metaself.app.domain.portion.PortionControl
import com.metaself.app.domain.portion.Portions
import kotlin.math.roundToInt

/**
 * How much of it there was, adjusted.
 *
 * The portion is the thing the owner knows and the calories are the thing he does not, so the
 * portion is what he changes and everything else follows from it. Scaling is linear: it is the
 * model's own estimate of the food's density applied to a different amount of the same food.
 *
 * It therefore handles a bigger bowl and does NOT handle the same bowl cooked richer — which the
 * model itself named as the real swing factor in a dish like risotto. That case is what "tell it more" is
 * for.
 *
 * The rules about which unit gets which control, and how an amount is written down, live in
 * [Portions] and are shared with meals repeated from the record.
 */
enum class PortionScale(val factor: Double) {

    LESS(Portions.LESS),
    AS_DESCRIBED(Portions.AS_IT_WAS),
    MORE(Portions.MORE),
    ;

    /**
     * [AS_DESCRIBED] returns the item untouched, including the model's own wording of the portion.
     * Rewriting "~280 g" into "280 g" for a scale that changed nothing would quietly replace what
     * the model said with what this app inferred, which is the one thing the portion must not do.
     *
     * Scaling always applies to the ORIGINAL proposal, never to an already-scaled item, so pressing
     * MORE and then AS_DESCRIBED returns exactly where it started.
     */
    fun applyTo(item: ProposedItem): ProposedItem = when {
        this == AS_DESCRIBED -> item
        !canScale(item) -> item
        else -> scale(item, factor, item.portionAmount * factor)
    }

    companion object {

        /** An item whose portion has no number in it cannot be scaled; the buttons must not show. */
        fun canScale(item: ProposedItem): Boolean =
            Portions.canScale(item.portionAmount, item.portionUnit)

        /** What to offer for this item: a scale, a count, or nothing. */
        fun controlFor(item: ProposedItem): PortionControl =
            Portions.controlFor(item.portionAmount, item.portionUnit)

        /**
         * Set how many there were — three slices, two eggs.
         *
         * A count is never less than one: nought of something is not a thing you ate, it is an item
         * you should delete.
         */
        fun count(howMany: Int, item: ProposedItem): ProposedItem =
            exactly(howMany.coerceAtLeast(1).toDouble(), item)

        fun exactly(amount: Double, item: ProposedItem): ProposedItem =
            if (!canScale(item)) item else scale(item, amount / item.portionAmount, amount)

        private fun scale(item: ProposedItem, factor: Double, amount: Double): ProposedItem =
            item.copy(
                portionAmount = amount,
                portion = Portions.words(amount, item.portionUnit),
                kcal = (item.kcal * factor).roundToInt(),
                proteinG = (item.proteinG * factor).roundToInt(),
                carbsG = (item.carbsG * factor).roundToInt(),
                fatG = (item.fatG * factor).roundToInt(),
            )
    }
}
