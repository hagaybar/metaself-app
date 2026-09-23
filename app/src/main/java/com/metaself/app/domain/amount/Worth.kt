package com.metaself.app.domain.amount

import com.metaself.app.domain.day.Confidence
import com.metaself.app.domain.food.CountedAs
import com.metaself.app.domain.food.Food
import com.metaself.app.domain.food.Nutrients

/**
 * What a worth is stated per: per 100 of the unit (grams or millilitres), or per one piece of it.
 *
 * @property divisor what an amount is divided by before the worth is multiplied by it.
 */
enum class Per(val divisor: Double) { HUNDRED(100.0), ONE(1.0) }

/**
 * Four figures per 100 g, per 100 ml or per one piece (D53 §1). Kept in decimals: it is a food's
 * kind of figure, and rounding happens once, when the row is logged.
 *
 * Not judged here. Whoever builds one — the reply's reader, the worth boxes — checks it against D42's
 * ceiling for its basis first, so this type stays total.
 */
data class Rate(val nutrients: Nutrients, val per: Per)

/**
 * Where an item's worth came from — which is where its row's figures came from (D53 §3). The amount
 * has no source and never changes this.
 */
sealed interface Worth {

    /** The model's figures, with the confidence it gave them. Logged as `AI_ESTIMATE`. */
    data class Estimated(val rate: Rate, val confidence: Confidence) : Worth

    /** Figures he typed over the worth. Logged as `TYPED`, with no confidence. */
    data class Typed(val rate: Rate) : Worth

    /**
     * His own food's figures, costed exactly as logging it by hand would cost them — through
     * `Logging.log`, so the row carries the food's own source, the weaker of two when computed.
     */
    data class YourFood(val food: Food, val countedAs: CountedAs) : Worth
}
