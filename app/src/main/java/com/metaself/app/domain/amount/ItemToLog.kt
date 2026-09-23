package com.metaself.app.domain.amount

import com.metaself.app.domain.day.FoodItem
import com.metaself.app.domain.day.Source
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.LoggedFrom
import com.metaself.app.domain.food.Logging
import com.metaself.app.domain.food.Nutrients
import com.metaself.app.domain.food.Provenance
import com.metaself.app.domain.portion.Portions

/**
 * One item being logged from a description: what it is worth, times how much was had (D53 §1).
 *
 * The two are separate and neither is derived from the other. Changing [amountText] changes the
 * total and nothing about [worth]; typing over the worth changes the total and nothing about the
 * amount. The unit is not edited on its own, because a different unit needs a different worth.
 *
 * @property name the plain name of the food, as the model gave it or as his food is called.
 * @property detail everything else worth saying about it, or "" — kept in the row's portion words.
 * @property amountText the amount as typed. A string because "1." and "" are real states of a box.
 * @property foodId set only when the worth is his food's, or was typed over his food's (§3): such a
 *   row is attached to that food and teaches it nothing.
 */
data class ItemToLog(
    val name: String,
    val detail: String,
    val amountText: String,
    val unit: String,
    val worth: Worth,
    val foodId: Long?,
) {

    /** The ceiling on the amount: 5000 of a measured unit, 100 of a counted one (D42). */
    val most: Double get() = BelievableAmount.amountIn(unit)

    /** The amount, when it is above nothing and not past [most]; a comma is a decimal point. */
    val amountOrNull: Double?
        get() = typedAmount?.takeIf { it > 0.0 && BelievableAmount.isBelievable(it, most) }

    /** True only for a number past [most] — the one wrong the box says out loud. */
    val amountTooMuch: Boolean
        get() = typedAmount?.let { BelievableAmount.isTooMuch(it, most) } == true

    private val typedAmount: Double? get() = amountText.trim().replace(',', '.').toDoubleOrNull()

    /**
     * What the row will log, rounded once — or null while the amount is not usable, or while his
     * food cannot cost it without a conversion.
     */
    val numbers: LoggedFrom.Numbers?
        get() {
            val amount = amountOrNull ?: return null
            return when (val worth = worth) {
                is Worth.YourFood ->
                    Logging.log(worth.food.facts, amount, worth.countedAs) as? LoggedFrom.Numbers
                is Worth.Estimated -> costed(
                    worth.rate,
                    Provenance(Source.AI_ESTIMATE, worth.confidence, setAtMillis = 0),
                    amount,
                )
                is Worth.Typed ->
                    costed(worth.rate, Provenance(Source.TYPED, null, setAtMillis = 0), amount)
            }
        }

    /**
     * The worth, for the line that prints it. For his food, what 100 g or one of it logs as — the
     * figures his food would put on a row at that amount, so the line and the total cannot disagree.
     */
    val rateLine: Rate?
        get() = when (val worth = worth) {
            is Worth.Estimated -> worth.rate
            is Worth.Typed -> worth.rate
            is Worth.YourFood -> {
                val per = if (worth.countedAs == CountedAs.GRAMS) Per.HUNDRED else Per.ONE
                val one = Logging.log(worth.food.facts, per.divisor, worth.countedAs)
                (one as? LoggedFrom.Numbers)?.let {
                    val figures = Nutrients(
                        it.kcal.toDouble(),
                        it.proteinG.toDouble(),
                        it.carbsG.toDouble(),
                        it.fatG.toDouble(),
                    )
                    Rate(figures, per)
                }
            }
        }

    private fun costed(rate: Rate, provenance: Provenance, amount: Double): LoggedFrom.Numbers =
        Logging.rounded(rate.nutrients * (amount / rate.per.divisor), provenance, amount, unit)

    /** The row that goes on the day, or null while this cannot be logged. */
    fun toFoodItem(): FoodItem? {
        val numbers = numbers ?: return null
        val words = Portions.words(numbers.amount, numbers.unit)
        return FoodItem(
            name = name,
            portion = if (detail.isBlank()) words else "$words (${detail.trim()})",
            portionAmount = numbers.amount,
            portionUnit = numbers.unit,
            kcal = numbers.kcal,
            proteinG = numbers.proteinG,
            carbsG = numbers.carbsG,
            fatG = numbers.fatG,
            source = numbers.source,
            confidence = numbers.confidence,
            foodId = foodId,
        )
    }
}

/**
 * The worth typed over: the row becomes his (`TYPED`) in all four figures, since the source belongs
 * to the row and not to each figure (D53 §3, D44's cost). The food it is attached to, if any, stays.
 */
fun ItemToLog.withTypedRate(rate: Rate): ItemToLog = copy(worth = Worth.Typed(rate))
