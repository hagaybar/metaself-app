package com.metaself.app.domain.food

/**
 * A food: a thing the owner eats, rather than a name repeated on every row he ever ate it in.
 *
 * @property name the name shown in lists — the preferred one of possibly several.
 * @property alsoKnownAs every other name it answers to. Empty for almost every food; non-empty for
 *   one that has been merged with a duplicate, which is the whole point of merging: after joining
 *   `יוגורט` to `Yoghurt`, logging in either language finds the one food.
 * @property brand `NA` — itself a brand, the brand of food that has no brand — for anything
 *   described in words. Real brand names arrive mainly from scanning a packet. Editing this splits
 *   a food, because it genuinely changes what the thing is.
 * @property hidden true when it is kept out of every picker while its history stays attached.
 *   Usually the better answer than deleting for a food that has been eaten, because deleting
 *   detaches the past and lets every day fall back to whatever name was typed on the day.
 */
data class Food(
    val id: Long = 0,
    val name: String,
    val alsoKnownAs: List<String> = emptyList(),
    val brand: String = FoodKeys.NO_BRAND,
    val barcode: String? = null,
    val facts: FoodFacts,
    val createdAtMillis: Long = 0,
    val updatedAtMillis: Long = 0,
    val hidden: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "a food needs a name" }
        require(brand.isNotBlank()) { "a food's brand is `NA` when it has none, never nothing" }
    }

    /** Every name this food would be found by, the preferred one first. */
    val everyName: List<String> get() = listOf(name) + alsoKnownAs

    /**
     * The brand as printed, or null for the brand of food with no brand.
     *
     * One rule for what is printed on a food's row and what a search looks at (D41): `NA`, `na`,
     * `N/A` and `N.A.` all mean "no brand", and a search that looked at them would find every
     * unbranded food the moment he typed an `a`.
     */
    val realBrand: String? get() = brand.takeIf { FoodKeys.brandKey(it) != FoodKeys.NO_BRAND_KEY }

    /**
     * How far apart the food's own facts are, when it knows enough for them to disagree.
     *
     * A state the earlier design could not even represent, because it only allowed one kind of
     * number. Storing three independent facts is what buys the honesty, and this is its price: 190
     * kcal per bar, 422 per 100 g and 45 g per bar multiply out, until one of them is corrected and
     * they do not. Shown to the owner so he can fix whichever is wrong — never averaged, never
     * silently preferred, never quietly dropped.
     *
     * Null when the food cannot disagree with itself for want of the third fact.
     */
    val disagreement: Double?
        get() {
            val per100g = facts.per100g?.nutrients?.kcal ?: return null
            val perUnit = facts.perUnit?.nutrients?.kcal ?: return null
            val grams = facts.gramsPerUnit?.grams ?: return null
            val impliedPerUnit = per100g * grams / 100.0
            if (impliedPerUnit <= 0.0) return null
            return (perUnit - impliedPerUnit) / impliedPerUnit
        }
}
