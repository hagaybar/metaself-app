package com.metaself.app.domain.food

import com.metaself.app.domain.portion.Portions

/** What his own foods say about an item the model named (D53 §4). */
sealed interface FoodMatch {

    /** None of his foods: the item is a new food, from the estimate. */
    data object None : FoodMatch

    /** The very food logging this item by name would land on anyway. Used by default. */
    data class Exact(val food: Food) : FoodMatch

    /** The first food a search for the name lists. Offered as one question, never applied. */
    data class Close(val food: Food) : FoodMatch
}

/** A way of counting his food, and the unit an amount of it is then typed in. */
data class CountedIn(val countedAs: CountedAs, val unit: String)

/**
 * Finding his own food for an item the model named — on the phone, after the reply, and never sent
 * anywhere (D16, D53 §4).
 *
 * **Exact is the app's one identity rule and nothing looser**: a food with no brand, one of whose
 * names keys the same as the item's plain name ([FoodKeys.nameKey]) — the match `findOrCreate` has
 * always made at the moment of logging, moved forward to the moment of deciding. **Close is the food
 * search, unchanged** ([FoodSearch.matching], D28's plain contains and D41's words in any order), and
 * only its first result, only when there is no exact one. Close is a question with his food's name in
 * it; nothing here applies it (D28's objection to fuzzy matching).
 */
object FoodMatching {

    /**
     * @param offered the foods on offer — not hidden, not knowing nothing — in the order the food list
     *   shows them. Nothing outside it is searched.
     */
    fun match(plainName: String, offered: List<Food>): FoodMatch {
        // A name that keys to nothing matches nothing: the search would list every food for it.
        val key = keyOrNull(plainName) ?: return FoodMatch.None
        val exact = offered.firstOrNull { food ->
            FoodKeys.brandKey(food.brand) == FoodKeys.NO_BRAND_KEY &&
                food.everyName.any { keyOrNull(it) == key }
        }
        if (exact != null) return FoodMatch.Exact(exact)
        return FoodSearch.matching(offered, plainName).firstOrNull()
            ?.let { FoodMatch.Close(it) }
            ?: FoodMatch.None
    }

    /**
     * How his food can cost an amount in [unit] exactly as logging it by hand would — through
     * `Logging.log`, which converts nothing — or null when it cannot (D53 §5).
     *
     * Grams, when the food can be weighed. Any other unit, only when it is the unit the food is
     * counted in, the same name by [FoodKeys.nameKey] (so case and spacing aside, and a food naming
     * no unit counting in `portion`, D36). Nothing works one unit out from another: not grams from
     * millilitres or kilograms, and not a piece from a per-100 g figure.
     */
    fun countedAsFor(food: Food, unit: String): CountedAs? {
        val facts = food.facts
        if (Portions.isGrams(unit)) {
            return CountedAs.GRAMS.takeIf { Logging.canWeigh(facts) == null }
        }
        if (Logging.canCount(facts) != null) return null
        val unitKey = keyOrNull(unit) ?: return null
        val countedIn = facts.perUnit?.unitName ?: FoodFacts.PORTION
        return CountedAs.UNITS.takeIf { keyOrNull(countedIn) == unitKey }
    }

    /**
     * The one honest way across when his food cannot cost an amount in [unit] (D53 §5): the way it
     * CAN be counted, for an amount he then types. Null when none is needed — [countedAsFor] has an
     * answer — or when there is none.
     *
     * Grams, when it can be weighed and the described amount was not already grams. Otherwise the
     * unit it is counted in (`portion` when it names none). Nothing is converted: the amount box
     * this leads to is empty.
     */
    fun countedInstead(food: Food, unit: String): CountedIn? {
        if (countedAsFor(food, unit) != null) return null
        val facts = food.facts
        if (!Portions.isGrams(unit) && Logging.canWeigh(facts) == null) {
            return CountedIn(CountedAs.GRAMS, GRAMS)
        }
        if (Logging.canCount(facts) != null) return null
        return CountedIn(CountedAs.UNITS, facts.perUnit?.unitName ?: FoodFacts.PORTION)
    }

    private const val GRAMS = "g"

    private fun keyOrNull(text: String): String? = runCatching { FoodKeys.nameKey(text) }.getOrNull()
}
